package com.rout.aiassistant.features.offlinestt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Offline STT (Vosk). Opt-in: model (~40MB, vosk small en-in) downloads once on first enable.
 * After that, recognition works fully offline - audio never leaves the phone.
 */
object OfflineSttEngine {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
    private const val MODEL_DIR = "vosk-model-small-en-in-0.4"

    var model: Model? = null; private set
    var speechService: SpeechService? = null; private set

    fun isReady(ctx: Context) = File(ctx.filesDir, MODEL_DIR).isDirectory

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences("rout_offline_stt", Context.MODE_PRIVATE).getBoolean("on", false)
    fun setEnabled(ctx: Context, on: Boolean) = ctx.getSharedPreferences("rout_offline_stt", Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()

    fun modelDir(ctx: Context) = File(ctx.filesDir, MODEL_DIR)

    /** Downloads + unpacks the model. Calls progress(0..100) and returns success. */
    suspend fun download(ctx: Context, progress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val zip = File(ctx.cacheDir, "vosk-model.zip")
            URL(MODEL_URL).openStream().use { input ->
                FileOutputStream(zip).use { out -> input.copyTo(out) }
            }
            progress(60)
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val f = File(ctx.filesDir, e.name)
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); FileOutputStream(f).use { z.copyTo(it) } }
                    z.closeEntry(); e = z.nextEntry
                }
            }
            zip.delete()
            progress(100)
            true
        } catch (e: Exception) { false }
    }

    fun loadModel(ctx: Context): Boolean {
        if (model != null) return true
        val dir = modelDir(ctx)
        if (!dir.isDirectory) return false
        return try { model = Model(dir.absolutePath); true } catch (e: Exception) { false }
    }

    fun start(ctx: Context, listener: RecognitionListener): Boolean {
        val m = model ?: return false
        return try {
            val rec = Recognizer(m, 16000.0f)
            speechService = SpeechService(rec, 16000.0f).also { it.startListening(listener) }
            true
        } catch (e: Exception) { false }
    }

    fun stop() {
        try { speechService?.stop() } catch (_: Exception) {}
        speechService = null
    }
}
