package com.rout.aiassistant.voice

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Natural voice via the ROUT backend (/v1/speak, Gemini TTS). Used only when
 * the device is enrolled; any failure returns false so the caller falls back
 * to on-device TTS. Audio is fetched to a private cache file and played with
 * MediaPlayer; nothing is kept after playback.
 */
class BackendSpeaker(private val context: Context) {

    private var player: MediaPlayer? = null

    suspend fun speak(
        backendUrl: String,
        deviceToken: String,
        text: String,
        onDone: () -> Unit
    ): Boolean {
        stop()
        val file = withContext(Dispatchers.IO) { fetchWav(backendUrl, deviceToken, text) } ?: return false
        return try {
            var finished = false
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                finished = true
                stop()
                onDone()
            }
            mp.setOnErrorListener { _, _, _ ->
                finished = true
                stop()
                onDone()
                true
            }
            mp.prepare()
            mp.start()
            !finished || true // playback started
        } catch (e: Exception) {
            stop()
            false
        } finally {
            file.delete()
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    private fun fetchWav(backendUrl: String, deviceToken: String, text: String): File? {
        return try {
            val conn = (URL(backendUrl.trimEnd('/') + "/v1/speak").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000; readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $deviceToken")
            }
            conn.outputStream.use { it.write(JSONObject().put("text", text.take(2000)).toString().toByteArray()) }
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val f = File(context.cacheDir, "rout_tts_${System.currentTimeMillis()}.wav")
            conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            if (f.length() < 100) { f.delete(); null } else f
        } catch (e: Exception) { null }
    }
}
