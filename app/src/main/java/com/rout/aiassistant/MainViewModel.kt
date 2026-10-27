package com.rout.aiassistant

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rout.aiassistant.ai.ChatTurn
import com.rout.aiassistant.ai.Enrollment
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.commands.ConfirmRequest
import com.rout.aiassistant.commands.VisionDoc
import com.rout.aiassistant.commands.VisionShare
import com.rout.aiassistant.data.*
import com.rout.aiassistant.service.FloatingOrbService
import com.rout.aiassistant.service.ListeningService
import com.rout.aiassistant.util.NetworkStatus
import com.rout.aiassistant.voice.AssistantState
import com.rout.aiassistant.voice.VoiceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = RoutDatabase.get(app)
    val prefs = Prefs(app)

    private val conversationId = MutableStateFlow(0L)

    val messages: StateFlow<List<Message>> = conversationId.flatMapLatest { cid ->
        if (cid == 0L) flowOf(emptyList()) else db.conversations().messages(cid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes = db.notes().notes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Note>())
    val tasks = db.tasks().tasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<TaskItem>())
    val facts = db.memory().facts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<MemoryFact>())
    val routines = db.routines().routines().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Routine>())

    fun addRoutine(trigger: String, steps: List<String>) {
        viewModelScope.launch {
            val tg = trigger.trim().lowercase()
            if (tg.isNotEmpty() && steps.isNotEmpty()) {
                db.routines().deleteByTrigger(tg)
                db.routines().insert(Routine(trigger = tg, steps = steps.joinToString("\n")))
            }
        }
    }
    fun deleteRoutine(id: Long) { viewModelScope.launch { db.routines().delete(id) } }
    fun setRoutineEnabled(id: Long, enabled: Boolean) { viewModelScope.launch { db.routines().setEnabled(id, enabled) } }

    val language = prefs.language.stateIn(viewModelScope, SharingStarted.Eagerly, "hi-IN")
    val speechRate = prefs.speechRate.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val wakeEnabled = prefs.wakeEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val orbEnabled = prefs.orbEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val backendUrl = prefs.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val weatherCity = prefs.weatherCity.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val onboarded = prefs.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, true) // default true until loaded
    val reducedMotion = prefs.reducedMotion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val devAiEnabled = prefs.devAiEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val translateMode = prefs.translateMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val userName = prefs.userName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setUserName(v: String) { viewModelScope.launch { prefs.setUserName(v) } }
    val personaMode = prefs.personaMode.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val warbyEmotions = prefs.warbyEmotions.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val deviceVoiceEnabled = prefs.deviceVoiceEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // One-shot prompt: floating LIVE needs the overlay permission; never drawn silently.
    private val _overlayPrompt = MutableStateFlow(false)
    val overlayPrompt: StateFlow<Boolean> = _overlayPrompt
    fun dismissOverlayPrompt() { _overlayPrompt.value = false }

    // Warby's current emotion, detected from the user's actual words (his warby-orb.js rules).
    val emotion = kotlinx.coroutines.flow.MutableStateFlow<com.rout.aiassistant.ui.components.WarbyEmotion?>(null)

    // Command paused on a runtime permission; resumes automatically after the grant (bug 3).
    @Volatile private var pendingAfterPermission: String? = null
    fun onPermissionsResult() {
        val pending = pendingAfterPermission ?: return
        pendingAfterPermission = null
        onUserText(pending, fromVoice = true)
    }
    val batteryAlert = prefs.batteryAlert.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val flipSilent = prefs.flipSilent.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val bedtime = prefs.bedtime.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _confirm = MutableStateFlow<ConfirmRequest?>(null)
    val confirm: StateFlow<ConfirmRequest?> = _confirm

    val online: StateFlow<Boolean> = NetworkStatus.online

    private val _aiStatus = MutableStateFlow("AI setup required")
    val aiStatus: StateFlow<String> = _aiStatus

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner

    val enrollment = Enrollment(app)

    private val _enrollState = MutableStateFlow("idle") // idle | waiting | enrolled | failed
    val enrollState: StateFlow<String> = _enrollState

    val engine = VoiceEngine(app, onFinalText = { text -> onUserText(text, fromVoice = true) }).also { e ->
        e.backendSpeaker = com.rout.aiassistant.voice.BackendSpeaker(app)
        e.deviceTokenProvider = { enrollment.deviceToken }
        viewModelScope.launch { backendUrl.collect { e.backendUrl = it } }
        viewModelScope.launch { prefs.voiceName.collect { e.voiceName = it } }
        viewModelScope.launch { prefs.deviceVoiceEnabled.collect { e.deviceVoiceEnabled = it } }
    }
    val assistantState: StateFlow<AssistantState> = engine.state
    val rms: StateFlow<Float> = engine.rms
    val partial: StateFlow<String> = engine.partial
    val voiceMode: StateFlow<String> = engine.voiceMode
    val voiceDetail: StateFlow<String> = engine.voiceDetail
    // Application-owned (see RoutApplication): survives Back so the floating LIVE UI keeps working.
    val live: com.rout.aiassistant.voice.LiveSession get() = RoutApplication.live
    val liveState: StateFlow<String> = live.state
    val liveTranscript: StateFlow<String> = live.transcript
    val liveError: StateFlow<String?> = live.error
    fun toggleLive() {
        val app = getApplication<Application>()
        if (live.state.value == "live" || live.state.value == "connecting") {
            live.stop()
            app.startService(Intent(app, com.rout.aiassistant.service.LiveOverlayService::class.java)
                .setAction(com.rout.aiassistant.service.LiveOverlayService.ACTION_STOP))
            return
        }
        engine.stopSpeaking(); engine.stopListening()
        // Mic foreground service must start while the app is on screen: it keeps the
        // mic legally alive for LIVE after Back and hosts the floating orb UI.
        if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(app, Intent(app, com.rout.aiassistant.service.LiveOverlayService::class.java))
        }
        // Floating orb needs an explicit user grant of "display over other apps".
        // Without it LIVE still runs (in-app + notification with STOP); we ask, never draw silently.
        if (!android.provider.Settings.canDrawOverlays(app)) _overlayPrompt.value = true
        viewModelScope.launch {
            live.start(backendUrl.value, enrollment.deviceToken ?: "", personaMode.value, prefs.voiceName.first())
        }
    }

    val recognitionAvailable get() = engine.recognitionAvailable
    val onDeviceRecognitionAvailable get() = engine.onDeviceRecognitionAvailable

    private lateinit var router: CommandRouter

    /** Photo/PDF the user explicitly shared; attached to AI answers until cleared or replaced. */
    @Volatile var visionDoc: VisionDoc? = null
        private set

    init {
        viewModelScope.launch {
            // one active conversation per launch; history stays browsable in DB
            val cid = db.conversations().insertConversation(Conversation(title = "Today"))
            conversationId.value = cid
        }
        viewModelScope.launch {
            combine(language, speechRate) { l, r -> l to r }.collect { (l, r) ->
                engine.languageTag = l; engine.speechRate = r
            }
        }
        viewModelScope.launch {
            combine(backendUrl, devAiEnabled) { b, d -> b to d }.collect { (b, d) ->
                _aiStatus.value = providerManager().statusLabel()
            }
        }
        NetworkStatus.start(app)
        viewModelScope.launch {
            // restore wake session if it was enabled
            if (prefs.wakeEnabled.first()) startWakeSession()
        }
        router = CommandRouter(
            context = app, db = db, prefs = prefs,
            providerManager = { providerManager() },
            history = { messages.value.takeLast(12).map { ChatTurn(it.role, it.text) } },
            requestConfirmation = { req ->
                _confirm.value = req
                engine.setState(AssistantState.WAITING_CONFIRMATION)
            },
            onPermissionNeeded = { cmd -> pendingAfterPermission = cmd },
            visionImages = { visionDoc?.images ?: emptyList() },
            onClearVision = { visionDoc = null }
        )
        engine.initTts()
        viewModelScope.launch {
            // Persona mode switches ROUT's talking style AND its voice (Ruby pattern).
            personaMode.collect { id ->
                val p = com.rout.aiassistant.ai.Personas.byId(id)
                if (p.id.isNotBlank()) engine.voiceName = p.voice
            }
        }
    }

    fun setPersona(id: String) {
        viewModelScope.launch {
            prefs.setPersonaMode(id)
            val p = com.rout.aiassistant.ai.Personas.byId(id)
            if (p.id.isNotBlank()) prefs.setVoiceName(p.voice)
        }
    }

    fun providerManager(): ProviderManager = ProviderManager(backendUrl.value, prefs.devApiKey, devAiEnabled.value, enrollment, personaMode.value)

    fun isDeviceEnrolled(): Boolean = enrollment.isEnrolledFor(backendUrl.value)

    fun enrollDevice(openUrl: (String) -> Unit) {
        val base = backendUrl.value
        if (base.isBlank()) return
        viewModelScope.launch {
            _enrollState.value = "waiting"
            val start = enrollment.start(base)
            if (start == null) { _enrollState.value = "failed"; return@launch }
            openUrl(start.approveUrl)
            val ok = enrollment.complete(base, start.enrollId, start.clientSecret)
            _enrollState.value = if (ok) "enrolled" else "failed"
            _aiStatus.value = providerManager().statusLabel()
        }
    }

    fun revokeDevice() {
        viewModelScope.launch {
            enrollment.revoke(backendUrl.value)
            _enrollState.value = "idle"
            _aiStatus.value = providerManager().statusLabel()
        }
    }

    fun onUserText(text: String, fromVoice: Boolean) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val cid = conversationId.value
            // Voice confirmation for a pending call/message: "haan" confirms, "nahi" cancels.
            if (_confirm.value != null) {
                val t = text.trim().lowercase().trimEnd('.', '!')
                val yes = setOf("haan", "ha", "han", "haan ji", "han ji", "yes", "ok", "okay", "kar do", "haan kar do", "confirm", "theek hai", "haan bolo")
                val no = setOf("nahi", "nahin", "na", "no", "cancel", "rehne do", "mat karo", "ruk", "ruko", "nahi karna")
                when {
                    t in yes -> {
                        db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = text))
                        confirmPending()
                        return@launch
                    }
                    t in no -> {
                        db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = text))
                        _confirm.value = null
                        db.conversations().insertMessage(Message(conversationId = cid, role = "rout", text = "Theek hai, cancel kar diya."))
                        engine.speak("Theek hai, cancel kar diya.")
                        engine.setState(AssistantState.IDLE)
                        return@launch
                    }
                }
            }
            // A new command while a confirm was pending cancels the stale confirm (bug: it used to swallow speech).
            if (_confirm.value != null) _confirm.value = null
            // Warby reacts to the user's actual words (his warby-orb.js rules), voice or typed.
            com.rout.aiassistant.ui.components.Warby.autoEmotion(text)?.let { emotion.value = it }
            db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = text))
            engine.setState(AssistantState.EXECUTING)
            val reply = try { router.handle(text) } catch (e: Exception) {
                "Something went wrong while handling that: ${e.javaClass.simpleName}"
            }
            // PDF answers always disclose how many pages were actually processed (never imply full coverage).
            val docNow = visionDoc
            val finalReply = if (docNow != null && docNow.kind == "pdf") docNow.disclosure() + " " + reply else reply
            db.conversations().insertMessage(Message(conversationId = cid, role = "rout", text = finalReply))
            if (assistantState.value != AssistantState.WAITING_CONFIRMATION) {
                // Bug 1+2: every action says what it is doing first, then the result - voice or typed.
                router.consumePreAction()?.let { engine.speak(it) }
                engine.speak(reply.take(400))
            }
            // #21: while ROUT is in the background, the reply also lands as a
            // notification with an inline voice/text reply action.
            if (!RoutApplication.foreground) {
                try { com.rout.aiassistant.service.RoutNotify.postReply(getApplication(), reply) } catch (_: Exception) {}
            }
        }
    }

    fun confirmPending() {
        val req = _confirm.value ?: return
        _confirm.value = null
        viewModelScope.launch {
            engine.setState(AssistantState.EXECUTING)
            val result = try { req.action() } catch (e: Exception) { "Action failed: ${e.javaClass.simpleName}" }
            db.conversations().insertMessage(Message(conversationId = conversationId.value, role = "rout", text = result))
            engine.speak(result.take(300))
        }
    }

    fun cancelPending() {
        _confirm.value = null
        viewModelScope.launch {
            db.conversations().insertMessage(Message(conversationId = conversationId.value, role = "rout", text = "Cancelled."))
            engine.setState(AssistantState.IDLE)
        }
    }

    fun onMicTap() {
        when (assistantState.value) {
            AssistantState.LISTENING -> engine.stopListening()
            AssistantState.SPEAKING -> engine.stopSpeaking()
            else -> engine.startListening()
        }
    }

    fun stopAll() {
        engine.stopSpeaking(); engine.stopListening()
        if (wakeEnabled.value) stopWakeSession()
        _confirm.value = null
        engine.setState(AssistantState.IDLE)
    }

    fun replay(text: String) = engine.speak(text)

    fun startWakeSession() {
        val app = getApplication<Application>()
        ContextCompat.startForegroundService(app, Intent(app, ListeningService::class.java))
        engine.startWakeSession()
        viewModelScope.launch { prefs.setWakeEnabled(true) }
    }

    fun stopWakeSession() {
        val app = getApplication<Application>()
        app.startService(Intent(app, ListeningService::class.java).setAction(ListeningService.ACTION_STOP))
        engine.stopWakeSession()
        viewModelScope.launch { prefs.setWakeEnabled(false) }
    }

    fun startOrb() {
        val app = getApplication<Application>()
        app.startService(Intent(app, FloatingOrbService::class.java))
        viewModelScope.launch { prefs.setOrbEnabled(true) }
    }

    fun stopOrb() {
        val app = getApplication<Application>()
        app.stopService(Intent(app, FloatingOrbService::class.java))
        viewModelScope.launch { prefs.setOrbEnabled(false) }
    }

    fun addNote(title: String, body: String) = viewModelScope.launch {
        db.notes().insert(Note(title = title.ifBlank { body.take(40) }, body = body))
    }
    fun deleteNote(id: Long) = viewModelScope.launch { db.notes().delete(id) }
    fun addTask(title: String) = viewModelScope.launch { db.tasks().insert(TaskItem(title = title)) }
    fun setTaskDone(id: Long, done: Boolean) = viewModelScope.launch { db.tasks().setDone(id, done) }
    fun deleteTask(id: Long) = viewModelScope.launch { db.tasks().delete(id) }
    fun deleteFact(id: Long) = viewModelScope.launch { db.memory().delete(id) }

    val conversations = db.conversations().conversations()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun openConversation(id: Long) { conversationId.value = id }

    fun newConversation() = viewModelScope.launch {
        conversationId.value = db.conversations().insertConversation(Conversation(title = "Today"))
    }

    fun renameConversation(id: Long, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) db.conversations().rename(id, title.trim().take(60))
    }

    fun deleteConversation(id: Long) = viewModelScope.launch {
        db.conversations().deleteMessages(id)
        db.conversations().deleteConversation(id)
        if (conversationId.value == id) {
            conversationId.value = db.conversations().insertConversation(Conversation(title = "Today"))
        }
    }

    suspend fun searchHistory(query: String): List<String> =
        if (query.isBlank()) emptyList() else db.conversations().searchUserMessages(query.trim())

    fun clearHistory() = viewModelScope.launch {
        db.conversations().clearAllMessages(); db.conversations().clearAllConversations()
        conversationId.value = db.conversations().insertConversation(Conversation(title = "Today"))
    }
    fun clearNotes() = viewModelScope.launch { db.notes().clear() }
    fun clearTasks() = viewModelScope.launch { db.tasks().clear() }
    fun clearMemory() = viewModelScope.launch { db.memory().clear() }

    suspend fun exportMemoryText(): String {
        val sb = StringBuilder("ROUT memory export\n\n")
        db.memory().all().forEach { sb.append("- ").append(it.text).append('\n') }
        return sb.toString()
    }

    fun setBanner(text: String?) { _banner.value = text }

    // LIVE intentionally NOT stopped here: it keeps running (with the floating orb)
    // after Back until the user ends it. Engine (tap-to-talk) still tears down.
    override fun onCleared() { engine.destroy() }
    /** Entry point for an explicit photo/PDF share into ROUT (system share sheet). */
    fun handleShared(uri: android.net.Uri, mimeType: String?) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val cid = conversationId.value
            val mime = (mimeType ?: "").lowercase()
            when {
                mime.startsWith("image/") -> {
                    val img = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        VisionShare.loadSharedImage(app, uri)
                    }
                    if (img == null) { shareError("Ye photo nahi khul paayi."); return@launch }
                    visionDoc = VisionDoc("photo", "photo", 1, 1, listOf(img))
                    db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = "\uD83D\uDCF7 Photo share ki"))
                    onUserText("Ye photo dekh kar batao isme kya dikh raha hai, thoda detail mein.", fromVoice = false)
                }
                mime == "application/pdf" -> {
                    val doc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        VisionShare.loadSharedPdf(app, uri, uri.lastPathSegment?.substringAfterLast('/'))
                    }
                    if (doc == null || doc.images.isEmpty()) { shareError("Ye PDF nahi khul paayi - ho sakta hai password-protected ho."); return@launch }
                    visionDoc = doc
                    db.conversations().insertMessage(Message(conversationId = cid, role = "user",
                        text = "\uD83D\uDCC4 PDF: ${doc.label} (${doc.totalPages} pages)"))
                    onUserText("Maine tumhe PDF '${doc.label}' ke pehle ${doc.shownPages} panne bheje hain (kul ${doc.totalPages} panne hain). " +
                        "Pehle batao tum kitne panne dekh rahe ho, fir unka summary do.", fromVoice = false)
                }
                else -> shareError("Is type ki file abhi support nahi hai - photo ya PDF bhejo.")
            }
        }
    }

    private fun shareError(s: String) {
        engine.speak(s)
        viewModelScope.launch {
            db.conversations().insertMessage(Message(conversationId = conversationId.value, role = "rout", text = s))
        }
    }
}
