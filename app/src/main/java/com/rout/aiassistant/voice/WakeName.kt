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
        s.lowercase(Locale.ROOT).map { if (it.isLetterOrDigit() || it in '\u0900'..'\u097F') it else ' ' }.joinToString("").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Speech transcription may render a Latin name in Devanagari ("Ruby" -> "रूबी") or a near spelling ("Rubi"). Compare transliterated, and allow one edit for names of 4+ letters. */
    private val DEV_CONS = mapOf(
        '\u0915' to "k", '\u0916' to "kh", '\u0917' to "g", '\u0918' to "gh", '\u0919' to "ng",
        '\u091a' to "ch", '\u091b' to "chh", '\u091c' to "j", '\u091d' to "jh",
        '\u091f' to "t", '\u0920' to "th", '\u0921' to "d", '\u0922' to "dh", '\u0923' to "n",
        '\u0924' to "t", '\u0925' to "th", '\u0926' to "d", '\u0927' to "dh", '\u0928' to "n",
        '\u092a' to "p", '\u092b' to "f", '\u092c' to "b", '\u092d' to "bh", '\u092e' to "m",
        '\u092f' to "y", '\u0930' to "r", '\u0932' to "l", '\u0935' to "v",
        '\u0936' to "sh", '\u0937' to "sh", '\u0938' to "s", '\u0939' to "h")
    private val DEV_VOWEL = mapOf(
        '\u0905' to "a", '\u0906' to "a", '\u0907' to "i", '\u0908' to "i", '\u0909' to "u", '\u090a' to "u",
        '\u090f' to "e", '\u0910' to "ai", '\u0913' to "o", '\u0914' to "au", '\u090b' to "ri")
    private val DEV_MATRA = mapOf(
        '\u093e' to "a", '\u093f' to "i", '\u0940' to "i", '\u0941' to "u", '\u0942' to "u",
        '\u0947' to "e", '\u0948' to "ai", '\u094b' to "o", '\u094c' to "au",
        '\u0902' to "n", '\u0903' to "h", '\u0901' to "n")
    private const val VIRAMA = '\u094d'

    /** Generic Devanagari -> Latin phonetic transliteration for name tokens (vocabulary-independent, unlike HindiNormal). */
    internal fun devanagariToLatin(s: String): String {
        if (s.none { it in '\u0900'..'\u097F' }) return s
        val out = StringBuilder()
        var pendingCons: String? = null
        fun flush(withA: Boolean) { pendingCons?.let { out.append(it); if (withA) out.append('a') }; pendingCons = null }
        for (c in s) {
            when {
                DEV_CONS.containsKey(c) -> { flush(true); pendingCons = DEV_CONS[c] }
                DEV_MATRA.containsKey(c) -> { out.append(pendingCons ?: ""); out.append(DEV_MATRA[c]); pendingCons = null }
                c == VIRAMA -> flush(false)
                DEV_VOWEL.containsKey(c) -> { flush(true); out.append(DEV_VOWEL[c]) }
                c.isLetterOrDigit() -> { flush(true); out.append(c) }
                else -> flush(true)
            }
        }
        flush(true)
        return out.toString()
    }

    private fun latinToken(tok: String): String = devanagariToLatin(tok)

    private fun nameTokenMatches(heard: String, name: String): Boolean {
        val h = latinToken(heard); val n = latinToken(name)
        if (h == n) return true
        if (n.length >= 4 && levenshtein(h, n) <= 1) return true
        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val t = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return dp[b.length]
    }

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
            val ok = if (isDefault) w.singleOrNull() in DEFAULT_VARIANTS else (w.singleOrNull()?.let { nameTokenMatches(it, n) } == true)
            if (!ok) return null
            i++
        }
        return words.drop(i).joinToString(" ").trim().trimStart(',', '.', ':', '-').trim()
    }
}

/**
 * Gate for LIVE device commands. With no saved name every turn is allowed (unchanged default). With a saved name, a turn runs
 * ROUT's local commands only when it starts with that name. There is NO follow-up window: a yes/no to a pending confirmation is
 * resolved before this gate by the confirmation flow itself, and anything else (a new call or send) must name the assistant again.
 * This gates ROUT's own on-phone actions only; the LIVE session itself still streams the mic to the model.
 */
class LiveAddressGate {
    /** Returns the text to run, or null when the turn is not addressed to the saved name. */
    fun accept(said: String, savedName: String, @Suppress("UNUSED_PARAMETER") nowMs: Long = 0L): String? {
        if (WakeName.sanitize(savedName).isBlank() || !WakeName.isValid(savedName)) return said
        return WakeName.match(said, savedName)?.ifEmpty { null }
    }
}
