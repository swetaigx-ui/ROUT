package com.rout.aiassistant.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LiveTurnTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun inputTranscriptionAccumulatesAndFiresOnTurnComplete() {
        val live = LiveSession(ctx)
        val turns = mutableListOf<String>()
        live.onUserTurn = { turns.add(it) }
        live.handle("""{"serverContent":{"inputTranscription":{"text":"camera "}}}""")
        live.handle("""{"serverContent":{"inputTranscription":{"text":"kholo"}}}""")
        assertEquals("camera kholo", live.heard.value)
        live.handle("""{"serverContent":{"turnComplete":true}}""")
        assertEquals(listOf("camera kholo"), turns)
        assertEquals("", live.heard.value)
        // Next turn starts clean.
        live.handle("""{"serverContent":{"inputTranscription":{"text":"time batao"}}}""")
        live.handle("""{"serverContent":{"turnComplete":true}}""")
        assertEquals(listOf("camera kholo", "time batao"), turns)
    }

    @Test fun silentTurnFiresNoCommand() {
        val live = LiveSession(ctx)
        val turns = mutableListOf<String>()
        live.onUserTurn = { turns.add(it) }
        live.handle("""{"serverContent":{"turnComplete":true}}""")
        assertTrue(turns.isEmpty())
    }

    @Test fun upstreamErrorSurfacesInsteadOfSilence() {
        val live = LiveSession(ctx)
        live.handle("""{"error":{"code":503,"message":"model overloaded"}}""")
        assertEquals("failed", live.state.value)
        assertTrue(live.error.value!!.contains("model overloaded"))
    }
}
