package com.rout.aiassistant.voice

import java.util.Locale

/**
 * Name matching for the user-started listening session ("Hey ROUT session").
 * Only the name saved in Settings (or the default "ROUT" when none is saved) addresses the assistant.
 * Matching is whole-word at the START of the utterance, never a substring, so "routine", "jarvisx" or
 * a name said mid-sentence never activates. This does not make the mic always-on: it only decides whether
 * speech heard during a session the user already started is addressed to ROUT.
 */
object WakeName {
    const val DEFAULT = "rout"
    const val MAX_LEN = 20
    private val GREETINGS = setOf("hey", "hi", "hello", "ok", "okay", "oye", "arre", "are", "o")
    /** Speech recognisers often hear the default name as these; only applied to the default name. */
    private val DEFAULT_VARIANTS = setOf("rout", "route", "raut")

    /** Normalise for saving: trim, collapse spaces, letters/digits/spaces only, <= MAX_LEN. Blank result means "use the default". */
    fun sanitize(input: String): String {
        val cleaned = input.map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }.joinToString("")
        return cleaned.trim().replace(Regex("\\s+"), " ").take(MAX_LEN).trim()
    }

    /** A custom name must have at least 2 letters and must not be a greeting word (it would swallow "hey"). */
    fun isValid(name: String): Boolean {
        val s = sanitize(name)
        return s.count { it.isLetter() } >= 2 && s.lowercase(Locale.ROOT) !in GREETINGS
    }

    private fun tokens(s: String): List<String> =
        s.lowercase(Locale.ROOT).map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /**
     * Returns the command text after the name when [said] is addressed to [savedName] (blank = default ROUT),
     * "" when only the name was said, or null when it is not addressed to the assistant.
     */
    fun match(said: String, savedName: String): String? {
        val custom = sanitize(savedName)
        val nameTokens = if (custom.isBlank() || !isValid(custom)) listOf(DEFAULT) else tokens(custom)
        val isDefault = nameTokens == listOf(DEFAULT)
        // Walk the original text word by word so the remainder keeps its own casing and punctuation.
        val words = said.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var i = 0
        if (i < words.size && tokens(words[i]).singleOrNull() in GREETINGS) i++
        for (n in nameTokens) {
            if (i >= words.size) return null
            val w = tokens(words[i])
            // a spoken word such as "rout," is one token; "rout-a" or "routine" is not equal
            val ok = if (isDefault) w.singleOrNull() in DEFAULT_VARIANTS else w.singleOrNull() == n
            if (!ok) return null
            i++
        }
        return words.drop(i).joinToString(" ").trim().trimStart(',', '.', ':', '-').trim()
    }
}
