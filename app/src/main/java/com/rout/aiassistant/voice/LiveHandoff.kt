package com.rout.aiassistant.voice

/**
 * Bounded hand-off from the on-device name-waiting recogniser to a LIVE conversation and back.
 * One microphone owner at a time: the recogniser is stopped (mic released) BEFORE LIVE starts, and LIVE is
 * stopped (socket + mic closed) BEFORE the recogniser resumes. LIVE ends on silence, on a hard time cap, on the
 * user's STOP/X, or on failure. Pure logic with injected actions so the ordering is unit-tested.
 */
class LiveHandoff(
    private val pauseRecognizer: () -> Unit,
    private val startLive: () -> Unit,
    private val stopLive: () -> Unit,
    private val resumeRecognizer: () -> Unit,
    private val onUserStopped: () -> Unit,
    private val silenceMs: Long = 20_000L,
    private val maxMs: Long = 5 * 60_000L,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    enum class Owner { RECOGNIZER, LIVE }
    @Volatile var owner = Owner.RECOGNIZER; private set
    private var startedAt = 0L
    private var lastActivity = 0L
    private var endingOurselves = false
    var lastEnd: String = ""; private set

    /** The saved name was heard on its own. Returns false when LIVE is already owning the mic. */
    @Synchronized fun onNameHeard(): Boolean {
        if (owner == Owner.LIVE) return false
        pauseRecognizer()          // mic released first
        owner = Owner.LIVE
        startedAt = now(); lastActivity = startedAt
        endingOurselves = false
        startLive()
        return true
    }

    @Synchronized fun onActivity() { if (owner == Owner.LIVE) lastActivity = now() }

    /** Call about once a second while LIVE owns the mic. */
    @Synchronized fun tick() {
        if (owner != Owner.LIVE) return
        val t = now()
        val why = when {
            t - startedAt >= maxMs -> "time cap"
            t - lastActivity >= silenceMs -> "silence"
            else -> return
        }
        endingOurselves = true
        stopLive()                 // socket + mic closed first
        owner = Owner.RECOGNIZER
        lastEnd = why
        resumeRecognizer()
    }

    /** LIVE reported off/failed. [failed] true when it ended by error. */
    @Synchronized fun onLiveEnded(failed: Boolean) {
        if (owner != Owner.LIVE) return                 // already handled by tick()
        owner = Owner.RECOGNIZER
        if (endingOurselves) return
        if (failed) { lastEnd = "failed"; resumeRecognizer() }
        else { lastEnd = "user stop"; onUserStopped() }    // STOP / X / tile: everything stays off
    }

    companion object {
        /** Spoken when only the name was heard but LIVE cannot start (not enrolled / no mic permission / unnamed session). Says plainly what works. */
        const val NAME_ONLY_UNAVAILABLE =
            "Naam suna, par LIVE yahan se shuru nahi ho sakta. Naam ke saath poora command ek hi baar mein bolo, jaise 'naam YouTube kholo'."
    }
}
