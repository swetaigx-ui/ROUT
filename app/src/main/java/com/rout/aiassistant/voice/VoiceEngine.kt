package com.rout.aiassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

enum class AssistantState { IDLE, LISTENING, PROCESSING, SPEAKING, EXECUTING, WAITING_CONFIRMATION, ERROR }

class VoiceEngine(
    private val context: Context,
    private val onFinalText: (String) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state

    private val _rms = MutableStateFlow(0f)
    val rms: StateFlow<Float> = _rms

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial

    /** True when the continuous "Hey ROUT" foreground session is running. */
    /** "natural" (backend voice), "device" (on-device TTS), "unavailable". */
    private val _voiceMode = MutableStateFlow("device")
    val voiceMode: StateFlow<String> = _voiceMode
    private val _voiceDetail = MutableStateFlow("")
    /** Human-readable, honest description of which voice last spoke and why a fallback happened. */
    val voiceDetail: StateFlow<String> = _voiceDetail

    /** Name saved in Settings (blank = default "ROUT"). Only this name addresses the assistant in a listening session. */
    @Volatile var wakeName: String = ""
    private val _wakeSession = MutableStateFlow(false)
    val wakeSession: StateFlow<Boolean> = _wakeSession

    private var recognizer: SpeechRecognizer? = null
    private var recognizerOnDevice = false
    /** True when a custom assistant name is saved: the name-waiting session then runs ONLY on the on-device recogniser (no cloud fallback). */
    @Volatile var requireOnDevice = false
    private val _wakeBlocked = MutableStateFlow("")
    /** Non-empty when the name-waiting session was refused because on-device recognition is unavailable/unsupported. */
    val wakeBlocked: StateFlow<String> = _wakeBlocked
    /** Seam for tests; production asks the system. */
    internal var onDeviceProbe: () -> Boolean = { Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
    fun onDeviceWakeSupported(): Boolean = onDeviceProbe()
    private fun blockWake() {
        _wakeBlocked.value = "Is phone par on-device awaaz pehchaan nahi chal rahi, isliye naam wala listening session band hai (cloud par nahi bhejungi). LIVE ya mic button haath se use kar sakte ho."
        _recognizerMode.value = "unavailable"
        stopWakeSession()
        setState(AssistantState.ERROR)
    }
    @Volatile private var onDeviceFailed = false
    private val _recognizerMode = MutableStateFlow("not started")
    /** Which recogniser the last session used: "on-device" or "system-default (may use cloud)". Honest label, shown in Settings. */
    val recognizerMode: StateFlow<String> = _recognizerMode
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    var languageTag: String = "hi-IN"
    var speechRate: Float = 1.0f

    val recognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    val onDeviceRecognitionAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun initTts(onReady: ((Boolean) -> Unit)? = null) {
        if (tts != null) { onReady?.invoke(ttsReady); return }
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val locale = Locale.forLanguageTag(languageTag)
                val res = tts?.setLanguage(locale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.setSpeechRate(speechRate)
            }
            onReady?.invoke(ttsReady)
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                scope.launch { if (_state.value == AssistantState.SPEAKING) setState(AssistantState.IDLE) }
            }
            @Deprecated("deprecated")
            override fun onError(id: String?) {
                scope.launch { if (_state.value == AssistantState.SPEAKING) setState(AssistantState.IDLE) }
            }
        })
    }

    fun setState(s: AssistantState) { _state.value = s }

    /** Natural backend voice. Set when the device is enrolled. */
    var backendSpeaker: BackendSpeaker? = null
    /**
     * Explicit user opt-in for the robotic on-device voice (Settings > Voice).
     * Gyan rejected the robotic voice (2026-10-27 voice note), so when this is
     * false the device voice NEVER plays: not as a backend-failure fallback and
     * not when no backend is configured. Honest status + text reply instead.
     */
    var deviceVoiceEnabled: Boolean = false
    var backendUrl: String = ""
    var voiceName: String = "Achernar"
    var deviceTokenProvider: () -> String = { "" }

    // Speaks everything it is given, in order: a pre-action ack is never
    // clipped by the post-action result. stopSpeaking() clears the queue.
    private val speakQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    @Volatile private var backendSpeaking = false

    fun speak(text: String) {
        if (text.isBlank()) return
        speakQueue.add(text)
        drainSpeakQueue()
    }

    private fun drainSpeakQueue() {
        val bs = backendSpeaker
        val token = deviceTokenProvider()
        val backendReady = bs != null && backendUrl.isNotBlank() && token.isNotBlank()
        if (backendReady) {
            if (backendSpeaking) return
            val next = speakQueue.poll() ?: return
            backendSpeaking = true
            setState(AssistantState.SPEAKING)
            scope.launch {
                val ok = bs!!.speak(backendUrl, token, voiceName, next) {
                    scope.launch { if (_state.value == AssistantState.SPEAKING) setState(AssistantState.IDLE) }
                }
                if (ok) {
                    _voiceMode.value = "natural"; _voiceDetail.value = "Natural voice (server)"
                } else if (bs.chunksPlayed > 0) {
                    // Part of the reply already played in the natural voice: do not repeat it in another voice.
                    _voiceMode.value = "natural"; _voiceDetail.value = "Natural voice cut off (" + (bs.lastFailure ?: "unknown") + ")"
                } else {
                    // Gyan's decision (2026-10-27, voice note via main): the robotic
                    // device voice must NOT auto-play as a fallback. Natural voice
                    // unavailable -> honest status line only; the text reply stays in
                    // chat. Device voice plays only via explicit Settings opt-in.
                    _voiceMode.value = "unavailable"
                    _voiceDetail.value = "Natural voice unavailable: " + (bs.lastFailure ?: "unknown") + " - jawab text mein hai (device voice off)"
                }
                backendSpeaking = false
                if (_state.value == AssistantState.SPEAKING) setState(AssistantState.IDLE)
                drainSpeakQueue()
            }
        } else if (deviceVoiceEnabled) {
            // On-device voice only because the user explicitly enabled it.
            // On-device TTS queues natively (QUEUE_ADD keeps the order).
            while (true) {
                val next = speakQueue.poll() ?: break
                speakOnDevice(next, queue = true)
            }
        } else {
            // No backend voice AND no device-voice opt-in: stay silent, be honest.
            // The text reply stays in chat; nothing plays in the robotic voice.
            speakQueue.clear()
            _voiceMode.value = "unavailable"
            _voiceDetail.value = "Voice off: natural voice unavailable - jawab text mein hai (robotic device voice off; Settings > Voice se chalu kar sakte ho)"
            if (_state.value == AssistantState.SPEAKING) setState(AssistantState.IDLE)
        }
    }

    private fun speakOnDevice(text: String, queue: Boolean = false, reason: String? = null) {
        _voiceMode.value = "device"
        _voiceDetail.value = if (reason != null) "Device voice (natural voice failed: $reason)" else "Device voice (no server connection)"
        initTts()
        if (!ttsReady) { _voiceMode.value = "unavailable"; setState(AssistantState.IDLE); return }
        setState(AssistantState.SPEAKING)
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "rout-" + UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        speakQueue.clear()
        backendSpeaker?.stop()
        tts?.stop()
        backendSpeaking = false
        if (_state.value == AssistantState.SPEAKING) setState(AssistantState.IDLE)
    }

    fun startListening() {
        if (!recognitionAvailable) { setState(AssistantState.ERROR); return }
        stopSpeaking()
        if (recognizer == null) {
            // The name-waiting session prefers the ON-DEVICE recognizer (Android 13+, only when the system reports it available).
            // Otherwise the system default recognizer is used and is labelled honestly: it may send audio to the cloud.
            val onDevice = _wakeSession.value && Build.VERSION.SDK_INT >= 31 && !onDeviceFailed &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            if (requireOnDevice && !onDevice) { blockWake(); return }
            recognizer = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
            recognizerOnDevice = onDevice
            _recognizerMode.value = if (onDevice) "on-device" else "system-default (may use cloud)"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN", "en-US"))
            if (_wakeSession.value) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        setState(AssistantState.LISTENING)
        try {
            recognizer?.setRecognitionListener(listener)
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            setState(AssistantState.ERROR)
        }
    }

    fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        if (_state.value == AssistantState.LISTENING) setState(AssistantState.IDLE)
        _rms.value = 0f
    }

    fun destroy() {
        _wakeSession.value = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        backendSpeaker?.stop()
        tts?.stop(); tts?.shutdown(); tts = null
    }

    /** Continuous foreground "Hey ROUT" session. Runs only while the caller keeps it alive. */
    fun startWakeSession(): Boolean {
        if (_wakeSession.value) return true
        _wakeBlocked.value = ""
        if (requireOnDevice && !onDeviceWakeSupported()) { blockWake(); return false }
        _wakeSession.value = true
        scope.launch {
            while (_wakeSession.value) {
                if (_state.value == AssistantState.IDLE || _state.value == AssistantState.ERROR) {
                    startListening()
                }
                delay(800)
            }
            stopListening()
        }
        return true
    }

    fun stopWakeSession() {
        _wakeSession.value = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null; recognizerOnDevice = false
        stopListening()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { _rms.value = rmsdB.coerceIn(-2f, 10f) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _rms.value = 0f; if (_state.value == AssistantState.LISTENING) setState(AssistantState.PROCESSING) }
        override fun onError(error: Int) {
            _rms.value = 0f
            // On-device recogniser cannot serve this language/model: drop it and fall back to the labelled system default.
            if (recognizerOnDevice && (error == 12 || error == 13) && requireOnDevice) { blockWake(); return }
            if (recognizerOnDevice && (error == 12 || error == 13)) {
                onDeviceFailed = true
                try { recognizer?.destroy() } catch (_: Exception) {}
                recognizer = null; recognizerOnDevice = false
                setState(AssistantState.IDLE); return
            }
            if (_wakeSession.value && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                setState(AssistantState.IDLE) // loop re-arms
            } else if (_wakeSession.value && error == SpeechRecognizer.ERROR_CLIENT) {
                setState(AssistantState.IDLE)
            } else {
                setState(AssistantState.ERROR)
            }
        }
        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val said = texts?.firstOrNull()?.trim().orEmpty()
            _partial.value = ""
            if (said.isEmpty()) { setState(AssistantState.IDLE); return }
            if (_wakeSession.value) {
                val rest = WakeName.match(said, wakeName)
                if (rest == null) { setState(AssistantState.IDLE); return } // not addressed to the assistant; keep loop
                if (rest.isEmpty()) { speak("Haan ji, bolo"); return }
                setState(AssistantState.PROCESSING)
                onFinalText(rest)
            } else {
                setState(AssistantState.PROCESSING)
                onFinalText(said)
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            _partial.value = texts?.firstOrNull().orEmpty()
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
