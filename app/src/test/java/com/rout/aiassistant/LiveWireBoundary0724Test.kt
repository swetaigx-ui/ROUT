package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import com.rout.aiassistant.voice.LiveSession
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Frame-level boundary: what actually goes out on the Live socket after private on-device results. Fake socket, no network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveWireBoundary0724Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private class FakeWs : WebSocket {
        val frames = ArrayList<String>()
        override fun request(): Request = Request.Builder().url("http://localhost/").build()
        override fun queueSize() = 0L
        override fun send(text: String): Boolean { frames.add(text); return true }
        override fun send(bytes: ByteString): Boolean = true
        override fun close(code: Int, reason: String?) = true
        override fun cancel() {}
    }
    private val router by lazy {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {})
    }

    private val secretScreen = "Screen par (sirf is device pe padha gaya, kahin nahi bheja):\nRohan: mera PIN 4821 aur password hunter2 hai"
    private val secretNotifs = "Tumhari latest notifications: WhatsApp: Amit: bank OTP 553311 mat batana."
    private val secretText = "Screen ka text AI ko bheja gaya (tumhari approval ke baad, common patterns chhupa kar). Jo text bheja gaya:\n---\nsecret-line-xyz\n---\nJawab: private-answer-abc"
    private val secretImage = "Screenshot AI ko bheja gaya (tumhari approval ke baad, upar dikhayi gayi image). Jawab: image-secret-answer-qrs"

    @Test fun privateResultsNeverAppearInSocketFrames() {
        val ws = FakeWs(); val live = LiveSession(ctx); live.attachForTest(ws)
        for (r in listOf(secretScreen, secretNotifs, secretText, secretImage)) {
            router.markPrivateForTest(r)
            live.sendContextNote(router.liveNoteFor("kuch bhi", r))
        }
        assertEquals(4, ws.frames.size)
        val all = ws.frames.joinToString("\n")
        for (s in listOf("4821", "hunter2", "553311", "Amit", "secret-line-xyz", "private-answer-abc", "image-secret-answer-qrs", "Rohan: mera"))
            assertFalse("leaked '$s' on the live socket", all.contains(s))
        assertTrue(all.contains("private on-device result, not shared"))
    }

    @Test fun markerAloneStillBlocksAfterRestart() {
        // fresh router: its in-memory private set is empty, only the text markers can protect
        val ws = FakeWs(); val live = LiveSession(ctx); live.attachForTest(ws)
        for (r in listOf(secretScreen, secretNotifs, secretText, secretImage)) live.sendContextNote(router.liveNoteFor("x", r))
        val all = ws.frames.joinToString("\n")
        for (s in listOf("4821", "hunter2", "553311", "secret-line-xyz", "private-answer-abc", "image-secret-answer-qrs"))
            assertFalse("leaked '$s' after restart", all.contains(s))
    }

    @Test fun ordinaryResultStillReachesTheModelSoItCanReportOutcome() {
        val ws = FakeWs(); val live = LiveSession(ctx); live.attachForTest(ws)
        live.sendContextNote(router.liveNoteFor("flashlight on", "Torch on."))
        assertTrue(ws.frames.single().contains("Torch on."))
    }
}
