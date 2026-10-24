package com.rout.aiassistant.voice

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * Natural voice via the ROUT backend (/v1/speak, Gemini TTS). Used only when
 * the device is enrolled; any failure returns false so the caller falls back
 * to on-device TTS.
 *
 * Latency: the reply is split into sentences; the first sentence is fetched
 * and started as early as possible while the next chunk prefetches during
 * playback, so long replies start speaking much sooner.
 *
 * Glitch fix: a short fade-in/fade-out is applied to the PCM samples of each
 * fetched WAV, removing the click at the start and the cut-off pop at the end
 * of spoken replies. Chunks are handed to a fresh MediaPlayer only after the
 * previous one completed, so no tail is clipped.
 */
class BackendSpeaker(private val context: Context) {

    @Volatile private var player: MediaPlayer? = null
    @Volatile private var stopped = false

    suspend fun speak(
        backendUrl: String,
        deviceToken: String,
        voice: String,
        text: String,
        onDone: () -> Unit
    ): Boolean = coroutineScope {
        stop()
        stopped = false
        val parts = splitSentences(text)
        if (parts.isEmpty()) return@coroutineScope false
        var current = withContext(Dispatchers.IO) { fetchWav(backendUrl, deviceToken, voice, parts[0]) }
            ?: return@coroutineScope false
        for (i in parts.indices) {
            if (stopped) { current.delete(); return@coroutineScope true }
            val prefetch = if (i + 1 < parts.size) {
                async(Dispatchers.IO) { fetchWav(backendUrl, deviceToken, voice, parts[i + 1]) }
            } else null
            val played = playFile(current)
            current.delete()
            if (!played && !stopped) { prefetch?.await()?.delete(); return@coroutineScope false }
            if (i + 1 < parts.size) {
                current = prefetch?.await() ?: return@coroutineScope true // tail failed: end cleanly after what played
            }
        }
        if (!stopped) onDone()
        true
    }

    /** Suspend until the file finishes playing (or playback is stopped/errors). */
    private suspend fun playFile(file: File): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                if (cont.isActive) cont.resume(true)
                releasePlayer(mp)
            }
            mp.setOnErrorListener { _, _, _ ->
                if (cont.isActive) cont.resume(false)
                releasePlayer(mp)
                true
            }
            mp.prepare()
            mp.start()
            cont.invokeOnCancellation { releasePlayer(mp) }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }

    private fun releasePlayer(mp: MediaPlayer) {
        try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
        if (player === mp) player = null
    }

    fun stop() {
        stopped = true
        try { player?.let { if (it.isPlaying) it.stop() } } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    private fun fetchWav(backendUrl: String, deviceToken: String, voice: String, text: String): File? {
        return try {
            val conn = (URL(backendUrl.trimEnd('/') + "/v1/speak").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000; readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $deviceToken")
            }
            val body = JSONObject().put("text", text.take(2000)).put("voice", voice).toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.size < 200) return null
            val f = File(context.cacheDir, "rout_tts_${System.nanoTime()}.wav")
            f.writeBytes(applyFades(bytes))
            f
        } catch (e: Exception) { null }
    }

    /**
     * Fade the first ~15ms and last ~60ms of 16-bit PCM samples to/from zero.
     * Kills the start click and end pop. Safe no-op on unexpected formats.
     */
    private fun applyFades(wav: ByteArray): ByteArray {
        if (wav.size < 100) return wav
        // Locate the "data" chunk of a standard RIFF/WAVE file.
        var off = 12
        var dataOff = -1
        var dataLen = 0
        while (off + 8 <= wav.size) {
            val id = String(wav, off, 4)
            val len = (wav[off + 4].toInt() and 0xFF) or
                ((wav[off + 5].toInt() and 0xFF) shl 8) or
                ((wav[off + 6].toInt() and 0xFF) shl 16) or
                ((wav[off + 7].toInt() and 0xFF) shl 24)
            if (id == "data") { dataOff = off + 8; dataLen = min(len, wav.size - dataOff); break }
            off += 8 + len + (len and 1)
        }
        if (dataOff < 0 || dataLen < 4096) return wav
        val fadeIn = min(720, dataLen / 8)   // ~15 ms at 24 kHz/16-bit/mono
        val fadeOut = min(2880, dataLen / 4) // ~60 ms
        fun setSample(i: Int, v: Int) {
            val c = v.coerceIn(-32768, 32767)
            wav[dataOff + i] = (c and 0xFF).toByte()
            wav[dataOff + i + 1] = ((c shr 8) and 0xFF).toByte()
        }
        fun getSample(i: Int) = (wav[dataOff + i].toInt() and 0xFF) or (wav[dataOff + i + 1].toInt() shl 8)
        var i = 0
        while (i < fadeIn) {
            setSample(i, (getSample(i) * i) / fadeIn)
            i += 2
        }
        var j = 0
        while (j < fadeOut) {
            val idx = dataLen - 2 - j
            if (idx < 0) break
            setSample(idx, (getSample(idx) * j) / fadeOut)
            j += 2
        }
        return wav
    }

    private fun splitSentences(text: String): List<String> {
        val raw = text.trim().take(2000)
        if (raw.length <= 220) return listOf(raw)
        val parts = raw.split(Regex("(?<=[.!?।])\u00A0?\\s+|(?<=[.!?।])\\s+"))
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return listOf(raw)
        // Merge tiny fragments so we do not make a request per few words.
        val merged = mutableListOf<String>()
        var buf = StringBuilder()
        for (p in parts) {
            if (buf.length + p.length > 220 && buf.isNotEmpty()) { merged.add(buf.toString()); buf = StringBuilder() }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(p)
        }
        if (buf.isNotEmpty()) merged.add(buf.toString())
        return merged
    }
}
