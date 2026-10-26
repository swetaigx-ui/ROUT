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

    private val _wakeSession = MutableStateFlow(false)
    val wakeSession: StateFlow<Boolean> = _wakeSession

    private var recognizer: SpeechRecognizer? = null
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

    /** Natural backend voice. Set when the device is enrolled; null = on-device TTS only. */
    var backendSpeaker: BackendSpeaker? = null
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
                    speakOnDevice(next, reason = bs.lastFailure ?: "unknown")
                }
                backendSpeaking = false
                if (_state.value == AssistantState.SPEAKING) setState(AssistantState.IDLE)
                drainSpeakQueue()
            }
        } else {
            // On-device TTS queues natively (QUEUE_ADD keeps the order).
            while (true) {
                val next = speakQueue.poll() ?: break
                speakOnDevice(next, queue = true)
            }
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
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN", "en-US"))
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
    fun startWakeSession() {
        if (_wakeSession.value) return
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
    }

    fun stopWakeSession() {
        _wakeSession.value = false
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
                val low = said.lowercase()
                val prefix = listOf("hey rout", "hey route", "hey rout,", "ok rout", "oye rout", "rout,", "rout ")
                    .firstOrNull { low.startsWith(it) }
                if (prefix == null) { setState(AssistantState.IDLE); return } // not addressed to ROUT; keep loop
                val rest = said.substring(prefix.length).trim()
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
