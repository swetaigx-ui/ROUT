package com.rout.aiassistant.ui.components

/**
 * Warby's emotional brain - ported ditto from the user's warby-orb.js design logic.
 * autoEmotion detects the mood from the user's actual words; labels follow his sheet.
 */
enum class WarbyEmotion { HAPPY, LOVE, SAD, ANGRY, EXCITED, SLEEPY, CURIOUS, THINKING }

object Warby {

    /** Same rule order and words as his JS: first match wins, default happy. */
    private val RULES = listOf(
        WarbyEmotion.LOVE to listOf("love you", "i love", "pyaar", "pyar", "miss you"),
        WarbyEmotion.SAD to listOf("sad", "dukhi", "udaas", "pareshan", "upset", "bura lag"),
        WarbyEmotion.ANGRY to listOf("gussa", "angry", "naraz", "irritated"),
        WarbyEmotion.EXCITED to listOf("wow", "amazing", "zabardast", "excited", "awesome"),
        WarbyEmotion.HAPPY to listOf("happy", "khush", "maza aa", "congratulations", "thank you"),
        WarbyEmotion.SLEEPY to listOf("good night", "so jao", "sleep", "neend aa"),
        WarbyEmotion.CURIOUS to listOf("kya hai", "kaise", "kyun", "why", "what", "how"),
        WarbyEmotion.THINKING to listOf("calculate", "solve", "explain", "socho", "samjhao")
    )

    /** Detect emotion from user text. null = no mood signal (orb stays in current state). */
    fun autoEmotion(text: String): WarbyEmotion? {
        val t = text.lowercase()
        for ((mode, words) in RULES) {
            if (words.any { t.contains(it) }) return mode
        }
        return null
    }

    /** Status labels - exactly his JS labels; idle/listening/talking come from live state. */
    fun label(mode: String): String = when (mode) {
        "idle" -> "READY - ROUT"
        "listening" -> "LISTENING TO YOU"
        "talking" -> "SPEAKING - LIVE"
        "happy" -> "HAPPY VIBES"
        "love" -> "PLAYFUL VIBES"
        "sad" -> "GENTLE MODE"
        "angry" -> "STRONG EMOTION"
        "excited" -> "EXCITED MODE"
        "curious" -> "CURIOUS MODE"
        "thinking" -> "THINKING"
        "sleepy" -> "SLEEP MODE"
        else -> "ROUT - LIVE"
    }
}
