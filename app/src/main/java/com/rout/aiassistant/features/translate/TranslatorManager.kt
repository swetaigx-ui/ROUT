package com.rout.aiassistant.features.translate

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translator as MlTranslator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Live Translator - on-device ML Kit translation (free, works offline after a one-time
 * per-language model download). No API key, nothing uploaded.
 */
object TranslatorManager {
    val languages = listOf(
        "English" to TranslateLanguage.ENGLISH, "Hindi" to TranslateLanguage.HINDI,
        "Bengali" to TranslateLanguage.BENGALI, "Tamil" to TranslateLanguage.TAMIL,
        "Telugu" to TranslateLanguage.TELUGU, "Marathi" to TranslateLanguage.MARATHI,
        "Gujarati" to TranslateLanguage.GUJARATI, "Urdu" to TranslateLanguage.URDU,
        "French" to TranslateLanguage.FRENCH, "German" to TranslateLanguage.GERMAN,
        "Arabic" to TranslateLanguage.ARABIC, "Japanese" to TranslateLanguage.JAPANESE,
        "Korean" to TranslateLanguage.KOREAN, "Chinese" to TranslateLanguage.CHINESE
    )

    suspend fun detect(text: String): String = suspendCancellableCoroutine { cont ->
        LanguageIdentification.getClient().identifyLanguage(text)
            .addOnSuccessListener { code -> cont.resume(if (code == "und") "" else code) }
            .addOnFailureListener { cont.resume("") }
    }

    private val cache = mutableMapOf<Pair<String, String>, MlTranslator>()

    private fun client(from: String, to: String): MlTranslator = cache.getOrPut(from to to) {
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
    }

    /** Ensures the language model is present; downloads (~30MB) on first use. */
    suspend fun ensureModel(from: String, to: String): Boolean = suspendCancellableCoroutine { cont ->
        client(from, to).downloadModelIfNeeded()
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { cont.resume(false) }
    }

    suspend fun translate(text: String, from: String, to: String): String {
        ensureModel(from, to)
        return suspendCancellableCoroutine { cont ->
            client(from, to).translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /** Detect source language, translate to target. Returns (detectedLangCode, translated). */
    suspend fun autoTranslate(text: String, to: String): Pair<String, String> {
        val src = detect(text).ifBlank { TranslateLanguage.ENGLISH }
        if (src == to) return src to text
        return src to translate(text, src, to)
    }
}
