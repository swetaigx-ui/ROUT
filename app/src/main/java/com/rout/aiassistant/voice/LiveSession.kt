package com.rout.aiassistant.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Live (streaming) voice conversation through the ROUT backend relay (/v1/live).
 * Mic PCM 16 kHz goes up, model PCM 24 kHz comes down and plays as it arrives.
 * The relay only accepts realtimeInput from this client; it cannot change the
 * model, voice or instructions. Live mode does not operate the phone: commands
 * still go through the normal command path. States: off | connecting | live | failed.
 */
class LiveSession {

    private val _state = MutableStateFlow("off")
    val state: StateFlow<String> = _state
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var ws: WebSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var micThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var ready = false
    @Volatile private var closed = true

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    companion object {
        fun wsUrl(backendUrl: String): String =
            backendUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/live"
        fun audioMessage(pcm: ByteArray, len: Int): String =
            JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
                .put("mimeType", "audio/pcm;rate=16000")
                .put("data", Base64.encodeToString(pcm, 0, len, Base64.NO_WRAP)))).toString()
    }

    @SuppressLint("MissingPermission")
    fun start(backendUrl: String, deviceToken: String) {
        if (!closed) return
        if (backendUrl.isBlank() || deviceToken.isBlank()) {
            _state.value = "failed"; _error.value = "Live needs an enrolled device."; return
        }
        closed = false; ready = false; running = true
        _state.value = "connecting"; _error.value = null; _transcript.value = ""
        val req = Request.Builder().url(wsUrl(backendUrl))
            .header("Sec-WebSocket-Protocol", "rout-live, bearer.$deviceToken").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail(if (response?.code == 401) "Device not authorised. Re-enrol in Settings." else "Live connection lost.")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { stop() }
        })
    }

    private fun handle(text: String) {
        try {
            val m = JSONObject(text)
            if (m.has("setupComplete")) { ready = true; _state.value = "live"; startMic(); return }
            val sc = m.optJSONObject("serverContent") ?: return
            if (sc.optBoolean("interrupted")) track?.pause().also { track?.flush(); track?.play() }
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data").orEmpty()
                    if (d.isNotEmpty()) play(Base64.decode(d, Base64.DEFAULT))
                }
            }
            sc.optJSONObject("outputTranscription")?.optString("text")?.let { _transcript.value = (_transcript.value + it).takeLast(600) }
            if (sc.optBoolean("turnComplete")) _transcript.value = ""
        } catch (_: Exception) {}
    }

    private fun play(pcm: ByteArray) {
        var t = track
        if (t == null) {
            val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 24000 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            t.play(); track = t
        }
        t.write(pcm, 0, pcm.size)
    }

    @SuppressLint("MissingPermission")
    private fun startMic() {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 3200))
        } catch (e: Exception) { fail("Microphone unavailable."); return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); fail("Microphone unavailable."); return }
        if (AcousticEchoCanceler.isAvailable()) try { AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true } catch (_: Exception) {}
        record = rec
        rec.startRecording()
        micThread = Thread {
            val buf = ByteArray(3200) // 100 ms
            while (running && ready) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) ws?.send(audioMessage(buf, n)) else if (n < 0) break
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun fail(msg: String) { _error.value = msg; stop(); _state.value = "failed" }

    fun stop() {
        if (closed && _state.value != "connecting") return
        running = false; ready = false; closed = true
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        ws = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        if (_state.value != "failed") _state.value = "off"
    }
}
