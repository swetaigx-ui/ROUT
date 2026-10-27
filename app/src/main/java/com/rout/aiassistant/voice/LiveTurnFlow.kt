package com.rout.aiassistant.voice

/**
 * Assembles one user utterance per LIVE turn from inputTranscription fragments
 * and deduplicates re-deliveries.
 *
 * The Live proto does NOT guarantee ordering between inputTranscription and
 * turnComplete: a transcription tail can legally arrive AFTER its turn's
 * turnComplete. Such a late tail is not dropped and not fired alone mid-turn -
 * it stays buffered and is delivered at the next turnComplete, so a late but
 * complete imperative ("camera kholo") still executes exactly once instead of
 * never or twice.
 *
 * Dedup: transcription retries / interruption replays can deliver the same
 * utterance twice; an identical utterance inside [windowMs] fires once. A
 * legitimate repeat after the window fires again.
 */
class LiveTurnFlow(private val windowMs: Long = 8000) {
    private val buf = StringBuilder()
    private var lastFired: String = ""
    private var lastFiredAt: Long = 0

    fun onTranscription(text: String) { buf.append(text) }

    /** Rolling "Sun raha:" display text. */
    fun currentHeard(): String = buf.toString().takeLast(200)

    /** Drop any buffered partial (new session) without clearing the dedup memory. */
    fun resetBuffer() { buf.setLength(0) }

    /**
     * Call on serverContent.turnComplete. Returns the utterance to dispatch
     * locally, or null when the buffer was empty or the same utterance already
     * fired within [windowMs].
     */
    fun onTurnComplete(now: Long): String? {
        val said = buf.toString().trim()
        buf.setLength(0)
        if (said.isEmpty()) return null
        if (said == lastFired && now - lastFiredAt < windowMs) return null
        lastFired = said; lastFiredAt = now
        return said
    }
}
