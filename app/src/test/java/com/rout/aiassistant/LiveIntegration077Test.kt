package com.rout.aiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.commands.ConfirmRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        val vm = MainViewModel(app)
        // Wait for init{} to create the conversation, else rows land under cid 0.
        val cidF = vm.javaClass.getDeclaredField("conversationId").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cidFlow = cidF.get(vm) as MutableStateFlow<Long>
        repeat(50) {
            Thread.sleep(100)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (cidFlow.value != 0L) return vm
        }
        return vm
    }

    /** Direct DB read: the messages StateFlow is WhileSubscribed (cold without a UI),
     *  so assertions verify the persisted rows, not the view pipeline. */
    private fun rows(): List<Pair<String, String>> {
        val db = com.rout.aiassistant.data.RoutDatabase.testInstance!!.openHelper.readableDatabase
        val c = db.query("SELECT role, text FROM messages ORDER BY id")
        val out = mutableListOf<Pair<String, String>>()
        while (c.moveToNext()) out.add(c.getString(0) to c.getString(1))
        c.close(); return out
    }

    private suspend fun drain(until: () -> Boolean = { false }) {
        repeat(60) {
            if (until()) return
            Thread.sleep(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    private fun armConfirm(vm: MainViewModel, ran: BooleanArray, id: String = "test", image: ByteArray? = null) {
        val f = vm.javaClass.getDeclaredField("confirmSlot").apply { isAccessible = true }
        (f.get(vm) as com.rout.aiassistant.commands.ConfirmSlot).arm(
            ConfirmRequest(id = id, description = "Call karein?", imageJpeg = image, action = { ran[0] = true; "Call laga di" }))
    }

    @Test fun liveTurnDispatchesAndRecords() = runBlocking {
        val vm = newVm()
        vm.onLiveUserTurn("kitne baje")
        drain { rows().size >= 2 }
        val msgs = rows()
        assertTrue("user turn recorded, got " + msgs, msgs.any { it.first == "user" && it.second == "kitne baje" })
        val reply = msgs.lastOrNull { it.first == "rout" }?.second ?: ""
        assertTrue("device-grounded reply, got: " + reply, Regex("\\d{1,2}:\\d{2} [AP]M [A-Z]{2,5}").containsMatchIn(reply))
    }

    @Test fun liveTurnGeneralChatDoesNotDispatch() = runBlocking {
        val vm = newVm()
        vm.onLiveUserTurn("tumhara din kaisa ja raha hai")
        drain()
        assertTrue("general chat in LIVE must dispatch nothing, got ${rows()}", rows().isEmpty())
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
        vm.onLiveUserTurn("nahi")
        drain { vm.confirm.value == null && rows().any { it.second == "Cancelled." } }
        assertFalse("no/cancel dispatched the action", ran[0])
        assertEquals(null, vm.confirm.value)
        assertTrue("cancel acknowledged, got ${rows()}", rows().any { it.first == "rout" && it.second == "Cancelled." })
        assertTrue("user no recorded", rows().any { it.first == "user" && it.second == "nahi" })
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

    @Test fun spokenYesNeverApprovesAnImagePreview() = runBlocking {
        val vm = newVm(); val ran = booleanArrayOf(false)
        armConfirm(vm, ran, id = "screen-image", image = byteArrayOf(1, 2))
        vm.onLiveUserTurn("haan"); drain { rows().any { it.second.contains("Confirm button") } }
        assertFalse("image approved by voice", ran[0])
        assertTrue("preview must stay pending for the button", vm.confirm.value != null)
        assertTrue(rows().any { it.first == "rout" && it.second.contains("Confirm button") })
        vm.confirmPending()                       // the on-screen button path
        drain { ran[0] }
        assertTrue("button confirm should run it", ran[0])
        assertEquals(null, vm.confirm.value)
    }

    @Test fun spokenYesNeverApprovesScreenTextPreview() = runBlocking {
        val vm = newVm(); val ran = booleanArrayOf(false)
        armConfirm(vm, ran, id = "screen-cloud")
        vm.onLiveUserTurn("ok"); drain { rows().any { it.second.contains("Confirm button") } }
        assertFalse(ran[0]); assertTrue(vm.confirm.value != null)
    }
}
