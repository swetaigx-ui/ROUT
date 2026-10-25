package com.rout.aiassistant.commands

/**
 * Speech-to-text often returns Devanagari ("\u092e\u094c\u0938\u092e \u092c\u0924\u093e\u0913"). The command rules speak
 * romanized Hinglish, so this maps common Devanagari command vocabulary to its
 * romanized twin before matching. Longest phrases first so compounds win.
 * Free-text without these words passes through untouched.
 */
object HindiNormal {

    private val PHRASES: List<Pair<String, String>> = listOf(
        // compounds first
        "\u0915\u093f\u0924\u0928\u0947 \u092c\u091c\u0947" to "kitne baje",
        "\u0915\u094d\u092f\u093e \u0938\u092e\u092f" to "kya samay",
        "\u0917\u0941\u0921 \u092e\u0949\u0930\u094d\u0928\u093f\u0902\u0917" to "good morning",
        "\u092e\u0941\u091d\u0947 \u092f\u093e\u0926 \u0926\u093f\u0932\u093e" to "mujhe yaad dila",
        "\u0913\u092a\u0928 \u0915\u0930\u094b" to "open karo",
        "\u0913\u092a\u0928 \u0915\u0930\u094b\n" to "open karo",
        "\u0916\u094b\u0932 \u0926\u094b" to "khol do",
        "\u092c\u0902\u0926 \u0915\u0930\u094b" to "band karo",
        "\u091a\u093e\u0932\u0942 \u0915\u0930\u094b" to "chalu karo",
        // single words
        "\u092e\u094c\u0938\u092e" to "mausam",
        "\u0924\u093e\u092a\u092e\u093e\u0928" to "temperature",
        "\u0938\u092e\u092f" to "samay",
        "\u091f\u093e\u0907\u092e" to "time",
        "\u0924\u093e\u0930\u0940\u0916" to "date",
        "\u0926\u093f\u0928\u093e\u0902\u0915" to "date",
        "\u0905\u0932\u093e\u0930\u094d\u092e" to "alarm",
        "\u091f\u093e\u0907\u092e\u0930" to "timer",
        "\u092c\u0924\u093e\u0913" to "batao",
        "\u092c\u0924\u093e" to "bata",
        "\u091f\u0949\u0930\u094d\u091a" to "torch",
        "\u092c\u0924\u094d\u0924\u0940" to "batti",
        "\u0916\u092c\u0930" to "news",
        "\u0938\u092e\u093e\u091a\u093e\u0930" to "news",
        "\u092c\u0948\u091f\u0930\u0940" to "battery",
        "\u0915\u0949\u0932" to "call",
        "\u092b\u094b\u0928 \u0915\u0930\u094b" to "call karo",
        "\u092e\u0948\u0938\u0947\u091c" to "message",
        "\u0938\u0902\u0926\u0947\u0936" to "message",
        "\u0916\u094b\u0932\u094b" to "kholo",
        "\u0932\u0917\u093e\u0913" to "lagao",
        "\u0932\u0917\u093e \u0926\u094b" to "laga do",
        "\u092c\u091c\u0947" to "baje",
        "\u0928\u094b\u091f" to "note",
        "\u0932\u093f\u0916\u094b" to "likho",
        "\u092f\u093e\u0926 \u0926\u093f\u0932\u093e\u0913" to "yaad dilao",
        "\u092f\u093e\u0926 \u0926\u093f\u0932\u093e\u0928\u093e" to "yaad dilana",
        "\u0930\u093f\u092e\u093e\u0907\u0902\u0921\u0930" to "reminder",
        "\u0915\u0948\u0932\u0947\u0902\u0921\u0930" to "calendar",
        "\u0935\u093f\u091f\u094d\u0930\u0947\u0938" to "weather",
        "\u0930\u093e\u0938\u094d\u0924\u093e" to "rasta",
        "\u0928\u0948\u0935\u093f\u0917\u0947\u091f" to "navigate",
        "\u091a\u0932\u093e\u0913" to "chalao",
        "\u0938\u0941\u0928\u093e\u0913" to "sunao",
        "\u092a\u0922\u093c\u094b" to "padho",
        "\u092e\u0941\u091d\u0947" to "mujhe",
        "\u0915\u0930\u094b" to "karo",
        "\u0915\u0930 \u0926\u094b" to "kar do",
        "\u0939\u0948" to "hai",
        "\u0915\u094d\u092f\u093e" to "kya",
        "\u0915\u094b" to "ko",
        "\u092e\u0948\u0902" to "main",
        "\u0939\u0948\u0902" to "hain"
    )

    fun latinize(t: String): String {
        if (t.none { it in '\u0900'..'\u097F' }) return t
        var out = t
        for ((dev, lat) in PHRASES) out = out.replace(dev, lat)
        return out.replace(Regex("\\s+"), " ").trim()
    }
}
