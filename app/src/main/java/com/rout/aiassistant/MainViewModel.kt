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

    val language = prefs.language.stateIn(viewModelScope, SharingStarted.Eagerly, "hi-IN")
    val speechRate = prefs.speechRate.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val wakeEnabled = prefs.wakeEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val orbEnabled = prefs.orbEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val backendUrl = prefs.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val weatherCity = prefs.weatherCity.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val onboarded = prefs.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, true) // default true until loaded
    val reducedMotion = prefs.reducedMotion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val devAiEnabled = prefs.devAiEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _confirm = MutableStateFlow<ConfirmRequest?>(null)
    val confirm: StateFlow<ConfirmRequest?> = _confirm

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    private val _aiStatus = MutableStateFlow("AI setup required")
    val aiStatus: StateFlow<String> = _aiStatus

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner

    val enrollment = Enrollment(app)

    private val _enrollState = MutableStateFlow("idle") // idle | waiting | enrolled | failed
    val enrollState: StateFlow<String> = _enrollState

    val engine = VoiceEngine(app, onFinalText = { text -> onUserText(text, fromVoice = true) })
    val assistantState: StateFlow<AssistantState> = engine.state
    val rms: StateFlow<Float> = engine.rms
    val partial: StateFlow<String> = engine.partial

    val recognitionAvailable get() = engine.recognitionAvailable
    val onDeviceRecognitionAvailable get() = engine.onDeviceRecognitionAvailable

    private lateinit var router: CommandRouter

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
        viewModelScope.launch {
            while (true) { _online.value = NetworkStatus.isOnline(app); delay(5000) }
        }
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
            }
        )
        engine.initTts()
    }

    fun providerManager(): ProviderManager = ProviderManager(backendUrl.value, prefs.devApiKey, devAiEnabled.value, enrollment)

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
            db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = text))
            engine.setState(AssistantState.EXECUTING)
            val reply = try { router.handle(text) } catch (e: Exception) {
                "Something went wrong while handling that: ${e.javaClass.simpleName}"
            }
            db.conversations().insertMessage(Message(conversationId = cid, role = "rout", text = reply))
            if (assistantState.value != AssistantState.WAITING_CONFIRMATION) {
                if (fromVoice) engine.speak(reply.take(400)) else engine.setState(AssistantState.IDLE)
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

    override fun onCleared() { engine.destroy() }
}
