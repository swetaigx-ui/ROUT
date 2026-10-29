package com.rout.aiassistant.commands

/** Rough Devanagari -> Latin transliteration used ONLY to look up contacts saved in Latin letters. Never used to pick a contact on its own:
 *  the resolver still treats several hits as ambiguous, and every send/call shows name + number for confirmation. */
object Translit {
    private val cons = mapOf(
        '\u0915' to "k", '\u0916' to "kh", '\u0917' to "g", '\u0918' to "gh", '\u0919' to "n", '\u091a' to "ch", '\u091b' to "chh", '\u091c' to "j", '\u091d' to "jh",
        '\u091e' to "n", '\u091f' to "t", '\u0920' to "th", '\u0921' to "d", '\u0922' to "dh", '\u0923' to "n", '\u0924' to "t", '\u0925' to "th", '\u0926' to "d",
        '\u0927' to "dh", '\u0928' to "n", '\u092a' to "p", '\u092b' to "f", '\u092c' to "b", '\u092d' to "bh", '\u092e' to "m", '\u092f' to "y", '\u0930' to "r",
        '\u0932' to "l", '\u0935' to "v", '\u0936' to "sh", '\u0937' to "sh", '\u0938' to "s", '\u0939' to "h", '\u095e' to "f", '\u0958' to "q", '\u0959' to "kh",
        '\u095a' to "g", '\u095b' to "z", '\u095c' to "d", '\u095d' to "dh"
    )
    private val vowels = mapOf(
        '\u0905' to "a", '\u0906' to "a", '\u0907' to "i", '\u0908' to "i", '\u0909' to "u", '\u090a' to "u", '\u090f' to "e", '\u0910' to "ai", '\u0913' to "o", '\u0914' to "au", '\u090b' to "ri"
    )
    private val matras = mapOf(
        '\u093e' to "a", '\u093f' to "i", '\u0940' to "i", '\u0941' to "u", '\u0942' to "u", '\u0947' to "e", '\u0948' to "ai", '\u094b' to "o", '\u094c' to "au", '\u0943' to "ri", '\u0949' to "o"
    )

    fun hasDevanagari(s: String) = s.any { it in '\u0900'..'\u097f' }

    fun toRoman(s: String): String = s.split(Regex("\\s+")).joinToString(" ") { word(it) }.trim()

    private fun word(w: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < w.length) {
            val c = w[i]
            val next = w.getOrNull(i + 1)
            when {
                c in cons -> {
                    sb.append(cons[c])
                    if (next == '\u093c') { i++ }
                    val n2 = w.getOrNull(i + 1)
                    when {
                        n2 == '\u094d' -> i++
                        n2 != null && n2 in matras -> { sb.append(matras[n2]); i++ }
                        n2 == '\u0902' || n2 == '\u0901' -> sb.append("a")
                        n2 == null || n2 !in '\u0900'..'\u097f' -> if (n2 != null) sb.append("a")  // word end: schwa dropped
                        else -> sb.append("a")
                    }
                }
                c in vowels -> sb.append(vowels[c])
                c == '\u0902' || c == '\u0901' -> sb.append("n")
                c == '\u0903' -> sb.append("h")
                c in '\u0900'..'\u097f' -> {}
                else -> sb.append(c.lowercaseChar())
            }
            i++
        }
        return sb.toString()
    }
}
