package com.rout.aiassistant.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
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
 * model, voice or instructions. States: off | connecting | live | failed.
 *
 * Phone actions in LIVE (Gyan's 2026-10-27 report: "orb aata hai lekin commands
 * sun nahi raha / kuch nahi karta"): the app reads the model's INPUT
 * transcription of what the user said and, when a turn completes, runs clear
 * device commands through the normal local CommandRouter (localOnly - never the
 * AI fallback). `heard` shows what the model heard, so "sun nahi raha" is
 * diagnosable on screen instead of guesswork.
 */
class LiveSession(private val appContext: Context) {

    private val _state = MutableStateFlow("off")
    val state: StateFlow<String> = _state
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _speaking = MutableStateFlow(false)
    /** True while model audio is arriving - drives the floating orb's glow. */
    val speaking: StateFlow<Boolean> = _speaking

    private val _heard = MutableStateFlow("")
    /** Live input transcription: what the model is hearing from the mic right now. */
    val heard: StateFlow<String> = _heard

    /** Completed user utterance (per turn) for local device-command execution. */
    var onUserTurn: ((String) -> Unit)? = null
    private val userBuf = StringBuilder()

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
        fun wsUrl(backendUrl: String, persona: String = "", voice: String = ""): String {
            val base = backendUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/live"
            val q = StringBuilder()
            if (persona.isNotBlank()) q.append("?persona=").append(java.net.URLEncoder.encode(persona, "UTF-8"))
            if (voice.isNotBlank()) q.append(if (q.isEmpty()) "?" else "&").append("voice=").append(java.net.URLEncoder.encode(voice, "UTF-8"))
            return base + q
        }
        fun audioMessage(pcm: ByteArray, len: Int): String =
            JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
                .put("mimeType", "audio/pcm;rate=16000")
                .put("data", Base64.encodeToString(pcm, 0, len, Base64.NO_WRAP)))).toString()
    }

    @SuppressLint("MissingPermission")
    fun start(backendUrl: String, deviceToken: String, persona: String = "", voice: String = "") {
        if (!closed) return
        if (backendUrl.isBlank() || deviceToken.isBlank()) {
            _state.value = "failed"; _error.value = "Live needs an enrolled device."; return
        }
        closed = false; ready = false; running = true
        userBuf.setLength(0); _heard.value = ""
        _state.value = "connecting"; _error.value = null; _transcript.value = ""
        val req = Request.Builder().url(wsUrl(backendUrl, persona, voice))
            .header("Sec-WebSocket-Protocol", "rout-live, bearer.$deviceToken").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail(if (response?.code == 401) "Device not authorised. Re-enrol in Settings." else "Live connection lost.")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { stop() }
        })
    }

    internal fun handle(text: String) {
        try {
            val m = JSONObject(text)
            if (m.has("setupComplete")) { ready = true; _state.value = "live"; startMic(); return }
            // Upstream fatal errors used to be swallowed silently - surface them.
            m.optJSONObject("error")?.let { e ->
                fail("Live error: " + e.optString("message", "upstream error").take(120)); return
            }
            val sc = m.optJSONObject("serverContent") ?: return
            if (sc.optBoolean("interrupted")) { _speaking.value = false; track?.pause().also { track?.flush(); track?.play() } }
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data").orEmpty()
                    if (d.isNotEmpty()) { _speaking.value = true; play(Base64.decode(d, Base64.DEFAULT)) }
                }
            }
            // What the model heard from the user's mic - drives the on-screen
            // "Sun raha:" line and the per-turn local command execution.
            sc.optJSONObject("inputTranscription")?.optString("text")?.let {
                userBuf.append(it); _heard.value = userBuf.toString().takeLast(200)
            }
            sc.optJSONObject("outputTranscription")?.optString("text")?.let { _transcript.value = (_transcript.value + it).takeLast(600) }
            if (sc.optBoolean("turnComplete")) {
                _transcript.value = ""; _speaking.value = false
                val said = userBuf.toString().trim()
                userBuf.setLength(0); _heard.value = ""
                if (said.isNotEmpty()) onUserTurn?.invoke(said)
            }
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
        // VOICE_COMMUNICATION on Vivo/other OEMs needs the matching audio mode or
        // the mic can deliver silence ("sun nahi raha").
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {}
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

    /**
     * Ground the model's speech in reality: after a local device action runs, its
     * RESULT goes back into the live session as a text note, so the model reports
     * what actually happened instead of claiming success on its own.
     */
    fun sendContextNote(note: String) {
        if (_state.value != "live") return
        try {
            ws?.send(JSONObject().put("realtimeInput", JSONObject()
                .put("text", note.take(400))).toString())
        } catch (_: Exception) {}
    }

    private fun fail(msg: String) { _error.value = msg; stop(); _state.value = "failed" }

    fun stop() {
        if (closed && _state.value != "connecting") return
        running = false; ready = false; closed = true
        _speaking.value = false
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        ws = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.mode == AudioManager.MODE_IN_COMMUNICATION) am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        if (_state.value != "failed") _state.value = "off"
    }
}
