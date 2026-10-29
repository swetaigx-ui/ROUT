package com.rout.aiassistant

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 0.8.0 LIVE execution regression (Gyan's 13:02 complaint: LIVE verbally promises
 * but executes nothing). Common exact spoken phrases must reach the dispatch layer
 * and the model must be told the ACTUAL outcome; an unmatched command-like
 * utterance must trigger the audible "nothing happened" correction, not silence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveDispatchRegressionTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private class RecSocket : WebSocket {
        val sent = mutableListOf<String>()
        override fun request() = okhttp3.Request.Builder().url("https://test.invalid").build()
        override fun queueSize() = 0L
        override fun send(text: String): Boolean { sent.add(text); return true }
        override fun send(bytes: ByteString) = true
        override fun close(code: Int, reason: String?) = true
        override fun cancel() {}
    }

    private fun newVm(sock: RecSocket): MainViewModel {
        com.rout.aiassistant.data.RoutDatabase.testInstance = null
        com.rout.aiassistant.data.RoutDatabase.testInstance =
            androidx.room.Room.inMemoryDatabaseBuilder(app, com.rout.aiassistant.data.RoutDatabase::class.java)
                .allowMainThreadQueries().build()
        RoutApplication.live.attachForTest(sock)
        val vm = MainViewModel(app)
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

    private suspend fun drain(until: () -> Boolean = { false }) {
        repeat(60) {
            if (until()) return
            Thread.sleep(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    private fun actionNotes(sock: RecSocket) = sock.sent.filter { it.contains("ROUT device action result") }

    @Test fun youtubeKholoDispatchesAndReportsActualOutcome() = runBlocking {
        val sock = RecSocket()
        val vm = newVm(sock)
        vm.onLiveUserTurn("youtube kholo")
        drain { actionNotes(sock).isNotEmpty() }
        assertTrue("spoken app-open must dispatch and report the actual outcome to the model, sent=" + sock.sent,
            actionNotes(sock).isNotEmpty())
    }

    @Test fun whatsappOpenKaroDispatches() = runBlocking {
        val sock = RecSocket()
        val vm = newVm(sock)
        vm.onLiveUserTurn("whatsapp open karo")
        drain { actionNotes(sock).isNotEmpty() }
        assertTrue("whatsapp open karo must reach dispatch, sent=" + sock.sent, actionNotes(sock).isNotEmpty())
    }

    @Test fun devanagariAppOpenDispatches() = runBlocking {
        val sock = RecSocket()
        val vm = newVm(sock)
        vm.onLiveUserTurn("यूट्यूब खोलो")
        drain { actionNotes(sock).isNotEmpty() }
        assertTrue("Devanagari app-open must reach dispatch, sent=" + sock.sent, actionNotes(sock).isNotEmpty())
    }

    @Test fun unmatchedCommandTriggersAudibleFailureNote() = runBlocking {
        val sock = RecSocket()
        val vm = newVm(sock)
        vm.onLiveUserTurn("bhejo")
        drain { sock.sent.isNotEmpty() }
        assertTrue("unmatched command must produce the audible nothing-happened correction, sent=" + sock.sent,
            sock.sent.any { it.contains("KUCH NAHI HUA") })
    }

    @Test fun generalChatSendsNoActionNote() = runBlocking {
        val sock = RecSocket()
        val vm = newVm(sock)
        vm.onLiveUserTurn("tumhara din kaisa ja raha hai")
        drain()
        assertTrue("general chat must not fake an action result, sent=" + sock.sent,
            sock.sent.none { it.contains("ROUT device action result") || it.contains("KUCH NAHI HUA") })
    }
}
