package com.rout.aiassistant.features.translate

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage

/** "translate karo hello hindi mein" / "hindi to english: ..." - on-device ML Kit, no upload. */
object TranslateCommands {
    private val langByName = mapOf(
        "hindi" to TranslateLanguage.HINDI, "english" to TranslateLanguage.ENGLISH,
        "angrezi" to TranslateLanguage.ENGLISH, "bengali" to TranslateLanguage.BENGALI,
        "tamil" to TranslateLanguage.TAMIL, "telugu" to TranslateLanguage.TELUGU,
        "marathi" to TranslateLanguage.MARATHI, "gujarati" to TranslateLanguage.GUJARATI,
        "spanish" to TranslateLanguage.SPANISH, "french" to TranslateLanguage.FRENCH,
        "german" to TranslateLanguage.GERMAN, "arabic" to TranslateLanguage.ARABIC,
        "japanese" to TranslateLanguage.JAPANESE, "korean" to TranslateLanguage.KOREAN,
        "chinese" to TranslateLanguage.CHINESE
    )

    suspend fun handle(ctx: Context, raw: String): String? {
        val t = raw.trim()
        val lower = t.lowercase()
        if (!lower.startsWith("translate")) return null
        // "translate <text> to <lang>" / "translate karo <text> <lang> mein"
        val m = Regex("^translate(?:\\s+karo)?\\s+(.+?)\\s+(?:to|into)\\s+(\\w+)$", RegexOption.IGNORE_CASE).find(t)
            ?: Regex("^translate(?:\\s+karo)?\\s+(.+?)\\s+(\\w+)\\s+mein$", RegexOption.IGNORE_CASE).find(t)
            ?: return "Bolo jaise: 'translate hello to Hindi' ya 'translate karo good morning Hindi mein'."
        val text = m.groupValues[1].trim()
        val langName = m.groupValues[2].lowercase()
        val target = langByName[langName] ?: return "Ye bhasha abhi list mein nahi: $langName"
        return try {
            val (src, out) = TranslatorManager.autoTranslate(text, target)
            "($src se) $out"
        } catch (e: Exception) {
            "Translation nahi ho paya: ${e.message ?: "model download chal raha hai ya net band"}"
        }
    }
}
