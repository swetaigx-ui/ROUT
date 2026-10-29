package com.rout.aiassistant.commands

/**
 * Speech-to-text often returns Devanagari ("\u092e\u094c\u0938\u092e \u092c\u0924\u093e\u0913"). The command rules speak
 * romanized Hinglish, so this maps common Devanagari command vocabulary to its
 * romanized twin before matching. Longest phrases first so compounds win.
 * Free-text without these words passes through untouched.
 */
object HindiNormal {

    /** Spoken-command vocabulary (LIVE returns Devanagari transcripts): app names, screen verbs, system controls. Compounds first. */
    private val VOCAB: List<Pair<String, String>> = listOf(
        "डू नॉट डिस्टर्ब" to "do not disturb", "डू नॉट डिस्टर्भ" to "do not disturb",
        "फ्लैशलाइट" to "flashlight", "फ़्लैशलाइट" to "flashlight", "फ्लैश लाइट" to "flashlight", "फ़्लैश लाइट" to "flashlight",
        "वाई-फाई" to "wifi", "वाईफाई" to "wifi", "वाईफ़ाई" to "wifi", "वाई फाई" to "wifi", "वाई फ़ाई" to "wifi",
        "ब्लूटूथ" to "bluetooth", "हॉटस्पॉट" to "hotspot", "वॉल्यूम" to "volume", "ब्राइटनेस" to "brightness",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp", "वॉट्सएप" to "whatsapp",
        "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "इंस्टाग्राम" to "instagram", "इंस्टा" to "instagram", "फेसबुक" to "facebook",
        "टेलीग्राम" to "telegram", "स्नैपचैट" to "snapchat", "स्पॉटिफाई" to "spotify", "क्रोम" to "chrome", "गूगल मैप्स" to "google maps",
        "गूगल" to "google", "कैमरा" to "camera", "गैलरी" to "gallery", "सेटिंग्स" to "settings", "सेटिंग" to "settings",
        "रील्स" to "reels", "रील" to "reel", "शॉर्ट्स" to "shorts", "क्लिक करो" to "click karo", "क्लिक कर दो" to "click karo",
        "लाइक करो" to "like karo", "लाइक कर दो" to "like karo", "स्क्रॉल करो" to "scroll karo", "स्क्रॉल कर दो" to "scroll karo",
        "सर्च करो" to "search karo", "सर्च कर दो" to "search karo", "सर्च" to "search", "क्लिक" to "click", "लाइक" to "like", "स्क्रॉल" to "scroll",
        "स्टेटस" to "status", "प्ले करो" to "play karo", "प्ले कर दो" to "play karo", "प्ले" to "play",
        "भेज दो" to "bhej do", "भेजो" to "bhejo", "सेंड करो" to "send karo", "सेंड कर दो" to "send kar do", "सेंड" to "send",
        "लिख कर" to "likh kar", "लिखकर" to "likh kar", "लिख के" to "likh ke", "हाय" to "hi", "हेलो" to "hello",
        "देखो" to "dekho", "दिखाओ" to "dikhao", "ढूंढो" to "dhundho", "ढूँढो" to "dhundho",
        "बढ़ाओ" to "badhao", "बढाओ" to "badhao", "घटाओ" to "kam karo", "कम करो" to "kam karo",
        "नीचे" to "neeche", "ऊपर" to "upar", "होम" to "home", "बैक" to "back", "पीछे" to "piche", "जाओ" to "jao", "वापस" to "wapas",
        "मेरा" to "mera", "मेरी" to "meri", "मेरे" to "mere",
        "ओपन" to "open", "चालू" to "chalu", "बंद" to "band"
    )
    // Short function words only as whole words, so they never mangle longer Devanagari words.
    private val WHOLE: List<Pair<Regex, String>> = listOf(
        "और" to "aur", "फिर" to "phir", "ऑन" to "on", "ऑफ" to "off", "में" to "mein", "पर" to "par", "पे" to "pe", "से" to "se", "का" to "ka", "की" to "ki"
    ).map { (d, l) -> Regex("(?<![\\u0900-\\u097F])" + d + "(?![\\u0900-\\u097F])") to l }

    private val PHRASES: List<Pair<String, String>> = listOf(
        "\u091f\u093e\u0907\u092e\u0930" to "timer",
        "\u0938\u094d\u0915\u094d\u0930\u0940\u0928 \u0936\u0949\u091f" to "screenshot",
        "\u0938\u094d\u0915\u094d\u0930\u0940\u0928\u0936\u0949\u091f" to "screenshot",
        "\u0938\u094d\u0915\u094d\u0930\u0940\u0928\u0938\u094d\u0939\u0949\u091f" to "screenshot",
        "\u0932\u0947 \u0932\u094b" to "le lo",
        "\u092e\u093f\u0928\u091f" to "minute",
        "\u0938\u0947\u0915\u0902\u0921" to "second",
        "\u0918\u0902\u091f\u0947" to "ghante",
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

    // Standalone "\u0932\u094b" (lo) only as a whole word, so "\u092c\u094b\u0932\u094b"/"\u0916\u094b\u0932\u094b" are never mangled.
    private val LO = Regex("(?<![\u0900-\u097F])\u0932\u094b(?![\u0900-\u097F])")

    /** STT sometimes repeats the whole utterance ("X X"); collapse an exact doubled phrase. */
    fun dedupeRepeat(t: String): String {
        val words = t.trim().split(Regex("\\s+"))
        if (words.size >= 2 && words.size % 2 == 0) {
            val h = words.size / 2
            if (words.subList(0, h) == words.subList(h, words.size)) return words.subList(0, h).joinToString(" ")
        }
        return t
    }

    fun latinize(t: String): String {
        if (t.none { it in '\u0900'..'\u097F' }) return t
        var out = t
        for ((dev, lat) in VOCAB) out = out.replace(dev, lat)
        for ((dev, lat) in PHRASES) out = out.replace(dev, lat)
        for ((re, lat) in WHOLE) out = re.replace(out, lat)
        out = LO.replace(out, "lo")
        return out.replace(Regex("\\s+"), " ").trim()
    }
}
