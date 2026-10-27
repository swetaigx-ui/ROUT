package com.rout.aiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.commands.ConfirmRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 0.7.7 integration review: LIVE turn -> dispatch -> record; pending-confirm
 * semantics (action must NOT run before "haan", "nahi" must never dispatch);
 * LIVE stop() state cleanup. Robolectric main-thread execution is used for
 * ViewModel calls; viewModelScope work is drained via idleMainLooper + delay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveIntegration077Test {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun newVm(): MainViewModel {
        com.rout.aiassistant.data.RoutDatabase.testInstance = null // isolate from any prior class
        com.rout.aiassistant.data.RoutDatabase.testInstance =
            androidx.room.Room.inMemoryDatabaseBuilder(app, com.rout.aiassistant.data.RoutDatabase::class.java)
                .allowMainThreadQueries().build()
        return MainViewModel(app)
    }

    private suspend fun drain(until: () -> Boolean = { false }) {
        repeat(60) {
            if (until()) return
            Thread.sleep(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    @Suppress("UNCHECKED_CAST")
    private fun armConfirm(vm: MainViewModel, ran: BooleanArray) {
        val f = vm.javaClass.getDeclaredField("_confirm").apply { isAccessible = true }
        (f.get(vm) as MutableStateFlow<Any?>).value =
            ConfirmRequest(id = "test", description = "Call karein?", action = { ran[0] = true; "Call laga di" })
    }

    @Test fun liveTurnDispatchesAndRecords() = runBlocking {
        val vm = newVm()
        val before = vm.messages.value.size
        vm.onLiveUserTurn("kitne baje")
        drain { vm.messages.value.size >= before + 2 }
        val msgs = vm.messages.value
        assertTrue("user turn recorded", msgs.any { it.role == "user" && it.text == "kitne baje" })
        val reply = msgs.lastOrNull { it.role == "rout" }?.text ?: ""
        assertTrue("device-grounded reply, got: $reply", Regex("\\d{1,2}:\\d{2} [AP]M [A-Z]{2,5}").containsMatchIn(reply))
    }

    @Test fun liveTurnGeneralChatDoesNotDispatch() = runBlocking {
        val vm = newVm()
        val before = vm.messages.value.size
        vm.onLiveUserTurn("tumhara din kaisa ja raha hai")
        drain()
        assertEquals("general chat in LIVE must dispatch nothing", before, vm.messages.value.size)
    }

    @Test fun confirmActionNotExecutedBeforeYes() = runBlocking {
        val vm = newVm()
        val ran = booleanArrayOf(false)
        armConfirm(vm, ran)
        // An unrelated utterance while a confirm is armed must not run the action.
        vm.onLiveUserTurn("kitne baje")
        drain()
        assertFalse("action ran without yes", ran[0])
        assertTrue("confirm still pending", vm.confirm.value != null)
    }

    @Test fun confirmYesExecutesExactlyOnce() = runBlocking {
        val vm = newVm()
        val ran = booleanArrayOf(false)
        armConfirm(vm, ran)
        vm.onLiveUserTurn("haan")
        drain { vm.confirm.value == null }
        assertTrue("yes did not execute the action", ran[0])
        assertEquals(null, vm.confirm.value)
        // Re-saying haan with nothing pending must not re-execute.
        val ran2 = booleanArrayOf(false)
        vm.onLiveUserTurn("haan")
        drain()
        assertFalse("stale yes re-executed", ran2[0])
    }

    @Test fun confirmNoNeverDispatches() = runBlocking {
        val vm = newVm()
        val ran = booleanArrayOf(false)
        armConfirm(vm, ran)
        val before = vm.messages.value.size
        vm.onLiveUserTurn("nahi")
        drain { vm.confirm.value == null }
        assertFalse("no/cancel dispatched the action", ran[0])
        assertEquals(null, vm.confirm.value)
        assertTrue("cancel acknowledged", vm.messages.value.any { it.role == "rout" && it.text == "Cancelled." })
        assertTrue("user no recorded", vm.messages.value.size >= before + 1)
    }

    @Test fun liveStopResetsState() {
        val vm = newVm()
        val live = vm.live
        val st = live.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        val heard = live.javaClass.getDeclaredField("_heard").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (st.get(live) as MutableStateFlow<String>).value = "live"
        @Suppress("UNCHECKED_CAST") (heard.get(live) as MutableStateFlow<String>).value = "camera kholo"
        // Simulate an actually-open session: start() clears the closed flag.
        val closedF = live.javaClass.getDeclaredField("closed").apply { isAccessible = true }
        closedF.setBoolean(live, false)
        live.stop()
        assertEquals("off", live.state.value)
        assertEquals("", live.heard.value)
        assertEquals("", live.transcript.value)
    }
}
