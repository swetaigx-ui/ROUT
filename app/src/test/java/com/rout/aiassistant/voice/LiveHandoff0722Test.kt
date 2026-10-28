package com.rout.aiassistant.voice

import org.junit.Assert.*
import org.junit.Test

class LiveHandoff0722Test {
    private class Rig(silence: Long = 20_000, max: Long = 300_000) {
        var t = 0L
        val log = mutableListOf<String>()
        var micHolders = 0       // recogniser and LIVE each hold the mic; must never be 2
        var maxHolders = 0
        var recognizerHolds = true
        var liveHolds = false
        var userStopped = 0
        private fun upd() { micHolders = (if (recognizerHolds) 1 else 0) + (if (liveHolds) 1 else 0); maxHolders = maxOf(maxHolders, micHolders) }
        val h = LiveHandoff(
            pauseRecognizer = { recognizerHolds = false; log += "pause"; upd() },
            startLive = { liveHolds = true; log += "live+"; upd() },
            stopLive = { liveHolds = false; log += "live-"; upd() },
            resumeRecognizer = { recognizerHolds = true; log += "resume"; upd() },
            onUserStopped = { userStopped++; log += "userstop" },
            silenceMs = silence, maxMs = max, now = { t })
    }

    @Test fun recognizerReleasedBeforeLiveStarts() {
        val r = Rig(); assertTrue(r.h.onNameHeard())
        assertEquals(listOf("pause", "live+"), r.log); assertEquals(1, r.maxHolders)
    }
    @Test fun secondNameHitWhileLiveDoesNothing() {
        val r = Rig(); r.h.onNameHeard(); assertFalse(r.h.onNameHeard()); assertEquals(listOf("pause", "live+"), r.log)
    }
    @Test fun silenceStopsLiveThenResumesRecognizer() {
        val r = Rig(); r.h.onNameHeard()
        r.t = 19_000; r.h.tick(); assertEquals(LiveHandoff.Owner.LIVE, r.h.owner)
        r.t = 20_000; r.h.tick()
        assertEquals(listOf("pause", "live+", "live-", "resume"), r.log)
        assertFalse(r.liveHolds); assertEquals(1, r.maxHolders); assertEquals("silence", r.h.lastEnd)
        r.h.onLiveEnded(false)           // live.state "off" callback after our own stop: no second action
        assertEquals(4, r.log.size); assertEquals(0, r.userStopped)
    }
    @Test fun activityExtendsTheSession() {
        val r = Rig(); r.h.onNameHeard()
        r.t = 15_000; r.h.onActivity(); r.t = 30_000; r.h.tick(); assertEquals(LiveHandoff.Owner.LIVE, r.h.owner)
        r.t = 35_000; r.h.tick(); assertEquals(LiveHandoff.Owner.RECOGNIZER, r.h.owner)
    }
    @Test fun hardCapEndsEvenWithConstantActivity() {
        val r = Rig(max = 60_000); r.h.onNameHeard()
        for (s in 1..60) { r.t = s * 1000L; r.h.onActivity(); r.h.tick() }
        assertEquals(LiveHandoff.Owner.RECOGNIZER, r.h.owner); assertEquals("time cap", r.h.lastEnd); assertFalse(r.liveHolds)
    }
    @Test fun userStopOrXKeepsEverythingOffAndNeverResumesMic() {
        val r = Rig(); r.h.onNameHeard()
        r.liveHolds = false              // live.stop() by STOP/X/tile
        r.h.onLiveEnded(failed = false)
        assertEquals(1, r.userStopped); assertFalse("recogniser must not restart on its own", r.log.contains("resume"))
        assertFalse(r.recognizerHolds); assertFalse(r.liveHolds)
    }
    @Test fun failureReturnsToOnDeviceWaiting() {
        val r = Rig(); r.h.onNameHeard(); r.liveHolds = false; r.h.onLiveEnded(failed = true)
        assertTrue(r.log.contains("resume")); assertEquals("failed", r.h.lastEnd); assertEquals(0, r.userStopped)
    }
    @Test fun canHandOffAgainAfterReturn() {
        val r = Rig(); r.h.onNameHeard(); r.t = 25_000; r.h.tick(); r.t = 26_000
        assertTrue(r.h.onNameHeard()); assertEquals(1, r.maxHolders)
    }
    @Test fun tickAndEndAreNoOpsWhenRecognizerOwnsMic() {
        val r = Rig(); r.h.tick(); r.h.onLiveEnded(false); r.h.onLiveEnded(true); assertTrue(r.log.isEmpty())
    }
}
