package com.rout.aiassistant

import com.rout.aiassistant.voice.LiveTurnFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Live proto does not guarantee inputTranscription/turnComplete ordering.
 * These tests pin the assembler behavior for reordered, late, truncated and
 * repeated utterances.
 */
class LiveTurnFlowTest {

    @Test fun fragmentsAccumulateThenFireOnce() {
        val f = LiveTurnFlow()
        f.onTranscription("time ")
        f.onTranscription("batao")
        assertEquals("time batao", f.onTurnComplete(1000))
        // Nothing new buffered: next turnComplete fires nothing.
        assertNull(f.onTurnComplete(1100))
    }

    @Test fun lateCompleteImperativeAfterTurnCompleteFiresExactlyOnce() {
        val f = LiveTurnFlow()
        // Turn 1 completes, then a LATE tail fragment of turn 1 arrives
        // (proto-legal reordering). It must not be dropped...
        f.onTranscription("time batao")
        assertEquals("time batao", f.onTurnComplete(1000))
        f.onTranscription("camera kholo")
        // ...and it fires at the NEXT turnComplete, exactly once.
        assertEquals("camera kholo", f.onTurnComplete(2000))
        assertNull(f.onTurnComplete(3000))
    }

    @Test fun duplicateRedeliveryWithinWindowSuppressed() {
        val f = LiveTurnFlow()
        f.onTranscription("camera kholo")
        assertEquals("camera kholo", f.onTurnComplete(1000))
        // Retry/replay delivers the identical utterance within the window.
        f.onTranscription("camera kholo")
        assertNull(f.onTurnComplete(2000))
    }

    @Test fun legitimateRepeatAfterWindowRefires() {
        val f = LiveTurnFlow()
        f.onTranscription("camera kholo")
        assertEquals("camera kholo", f.onTurnComplete(1000))
        f.onTranscription("camera kholo")
        assertEquals("camera kholo", f.onTurnComplete(1000 + 8001))
    }

    @Test fun emptyAndWhitespaceTurnsFireNothing() {
        val f = LiveTurnFlow()
        assertNull(f.onTurnComplete(1000))
        f.onTranscription("   ")
        assertNull(f.onTurnComplete(2000))
    }

    @Test fun truncatedFragmentPassesThroughForRouterToReject() {
        // A truncated tail is still delivered as its own (harmless) utterance;
        // CommandRouter pattern-matching is the layer that refuses non-commands.
        val f = LiveTurnFlow()
        f.onTranscription("cam")
        assertEquals("cam", f.onTurnComplete(1000))
    }
}
