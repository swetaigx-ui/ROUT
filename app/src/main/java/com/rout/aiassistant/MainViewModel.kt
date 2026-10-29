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
    val personality = prefs.personality.stateIn(viewModelScope, SharingStarted.Eagerly, com.rout.aiassistant.ai.PersonalitySettings())
    internal val personalityCtl = com.rout.aiassistant.ai.PersonalityController()
    val assistantName = prefs.assistantName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    suspend fun setAssistantName(v: String): Boolean = prefs.setAssistantName(v)
    private val liveGate = com.rout.aiassistant.voice.LiveAddressGate()
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
    val notifReply = prefs.notifReplyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val screenImage = prefs.screenImageFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val screenCloud = prefs.screenCloudFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val flipSilent = prefs.flipSilent.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val bedtime = prefs.bedtime.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val confirmSlot = com.rout.aiassistant.commands.ConfirmSlot()
    private val _confirm get() = confirmSlot.state
    val confirm: StateFlow<ConfirmRequest?> get() = confirmSlot.state

    val online: StateFlow<Boolean> = NetworkStatus.online

    private val _aiStatus = MutableStateFlow("AI setup required")
    val aiStatus: StateFlow<String> = _aiStatus

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner

    val enrollment = Enrollment(app)

    /** LIVE can be started from a name hit only when enrolled (it already has the user's mic + AI consent from the Live flow). */
    private fun handoffReady(): Boolean = backendUrl.value.isNotBlank() && !(enrollment.deviceToken.isNullOrBlank()) &&
        ContextCompat.checkSelfPermission(getApplication<Application>(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val handoff = com.rout.aiassistant.voice.LiveHandoff(
        pauseRecognizer = { engine.pauseForLive() },
        // The mic foreground service that is already running (listening service) keeps the mic legal: no activity needed.
        startLive = { viewModelScope.launch { live.start(backendUrl.value, enrollment.deviceToken ?: "", personaMode.value, prefs.voiceName.first()) } },
        stopLive = { live.stop() },
        resumeRecognizer = { engine.resumeAfterLive() },
        onUserStopped = { engine.resumeAfterLive(); stopWakeSession() }
    )

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
    val recognizerMode: StateFlow<String> = engine.recognizerMode
    val wakeBlocked: StateFlow<String> = engine.wakeBlocked
    val voiceMode: StateFlow<String> = engine.voiceMode
    val voiceDetail: StateFlow<String> = engine.voiceDetail
    // Application-owned (see RoutApplication): survives Back so the floating LIVE UI keeps working.
    val live: com.rout.aiassistant.voice.LiveSession get() = RoutApplication.live
    val liveState: StateFlow<String> = live.state
    val liveTranscript: StateFlow<String> = live.transcript
    val liveHeard: StateFlow<String> = live.heard
    val liveError: StateFlow<String?> = live.error
    /** Tile entry: start LIVE only when it is not already running (never toggles it off by accident). */
    fun startLiveIfIdle() {
        val st = live.state.value
        if (st != "live" && st != "connecting") toggleLive()
    }
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

    /**
     * A completed LIVE user utterance (from the model's input transcription).
     * Pending confirmations resolve first ("haan"/"nahi"); otherwise clear device
     * commands execute via the local router. General conversation is NOT routed -
     * the LIVE model already answered it in its own voice.
     */
    // Assembly + dedup live in LiveTurnFlow (LiveSession): proto ordering does
    // not guarantee transcription-before-turnComplete, and retries can re-deliver
    // an utterance; the flow buffers late tails and suppresses 8s duplicates.
    internal fun onLiveUserTurn(utter: String) {
        if (utter.isBlank()) return
        viewModelScope.launch {
            val cid = conversationId.value
            if (personalityTurn(utter, fromLive = true, stopOnly = true)) return@launch
            if (_confirm.value != null) {
                val t = utter.trim().lowercase().trimEnd('.', '!')
                val yes = setOf("haan", "ha", "han", "haan ji", "han ji", "yes", "ok", "okay", "kar do", "haan kar do", "confirm", "theek hai", "haan bolo")
                val no = setOf("nahi", "nahin", "na", "no", "cancel", "rehne do", "mat karo", "ruk", "ruko", "nahi karna")
                when {
                    t in yes -> { db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = utter)); confirmPending(viaVoice = true); return@launch }
                    t in no -> { db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = utter)); cancelPending(); return@launch }
                }
            }
            // With a saved assistant name, LIVE commands run only when addressed to that name (or as a short follow-up).
            val toRun = liveGate.accept(utter, assistantName.value, System.currentTimeMillis()) ?: run {
                // Not addressed to the saved name: nothing runs. If it sounded like a phone command, say why instead of staying silent.
                if (com.rout.aiassistant.commands.SpokenCommands.looksLikeCommand(com.rout.aiassistant.commands.HindiNormal.latinize(utter.lowercase()))) {
                    db.conversations().insertMessage(Message(conversationId = cid, role = "rout",
                        text = "Command nahi chala: saved naam \"${assistantName.value}\" se shuru nahi hua. Aise bolo: \"${assistantName.value}, ${utter.trim()}\"."))
                    live.sendContextNote("[ROUT device action result]: KUCH NAHI CHALA - user ne command saved naam se shuru nahi ki. Politely batao ki phone action ke liye \"${assistantName.value}\" se shuru karke bole. Kabhi mat bolo ki action ho gaya.")
                }
                return@launch
            }
            if (personalityTurn(toRun, fromLive = true)) return@launch
            val reply = router.handle(toRun, localOnly = true)
            if (reply.isBlank() && com.rout.aiassistant.commands.SpokenCommands.looksLikeCommand(com.rout.aiassistant.commands.HindiNormal.latinize(toRun.lowercase()))) {
                // Nothing ran: not a known local command and the validated PLAN path
                // returned nothing (unenrolled / busy / no valid plan). The model
                // already acked in this turn - it must NOT let that stand as done.
                live.sendContextNote("[ROUT device action result]: KUCH NAHI HUA - ye command phone par nahi chali (command samajh nahi aayi ya plan nahi ban saka). User ko honestly short mein batao ki action nahi hua, aur use dobara ya simple shabdon mein bolne ko kaho. Kabhi mat bolo ki kaam ho gaya.")
            }
            if (reply.isNotBlank()) {
                db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = utter))
                db.conversations().insertMessage(Message(conversationId = cid, role = "rout", text = reply))
                // The model must report the ACTUAL outcome - never its own assumption.
                // Dispatch-level truth: the router's message is what the phone DID
                // (e.g. startActivity succeeded) - not visual confirmation. The note
                // says so, and the model is told not to upgrade it into a promise.
                live.sendContextNote(router.liveNoteFor(utter, reply))
            }
        }
    }


    // ---- Native device-control engine (plans from the backend, validated here; this app stays the authority) ----
    private val controlEngine: com.rout.aiassistant.control.ControlEngine by lazy {
        com.rout.aiassistant.control.ControlEngine(
            com.rout.aiassistant.control.AndroidExecutor(app, onCancelRequested = { controlEngine.stop() }, routineRunner = { n -> router.runSavedRoutine(n) }),
            confirm = { desc -> controlConfirm(desc) })
    }
    val controlSnapshot: StateFlow<com.rout.aiassistant.control.ControlSnapshot> get() = controlEngine.snapshot
    fun stopAutomation() { controlEngine.stop(); confirmSlot.clear(); engine.setState(AssistantState.IDLE) }
    fun dismissControl() = controlEngine.reset()
    internal fun controlEngineForRender() = controlEngine

    /** One confirmation through the normal single pending slot. Declines on cancel, replacement, Stop or 90 s. */
    private suspend fun controlConfirm(desc: String): Boolean {
        val d = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val req = ConfirmRequest("control", desc) { d.complete(true); "Theek hai, chala rahi hoon." }
        confirmSlot.arm(req)
        engine.setState(AssistantState.WAITING_CONFIRMATION)
        val watcher = viewModelScope.launch {
            confirmSlot.state.first { it !== req }
            kotlinx.coroutines.delay(600)
            d.complete(false)
        }
        val ok = kotlinx.coroutines.withTimeoutOrNull(90_000L) { d.await() } ?: false
        watcher.cancel()
        if (!ok && confirmSlot.state.value === req) confirmSlot.clear()
        return ok
    }

    /** Backend-planned execution for a command the local routes did not understand. Null = nothing planned (caller falls through). */
    private suspend fun controlPlan(text: String): String? {
        if (!isDeviceEnrolled() || controlEngine.isRunning()) return null
        val id = com.rout.aiassistant.control.PlanClient.newRequestId()
        controlEngine.planning(id)
        return when (val r = com.rout.aiassistant.control.PlanClient.fetch(backendUrl.value, enrollment.deviceToken, id, text)) {
            is com.rout.aiassistant.control.PlanClient.Result.Plan -> when (val o = controlEngine.run(r.plan)) {
                is com.rout.aiassistant.control.ControlOutcome.Done -> o.message
                is com.rout.aiassistant.control.ControlOutcome.Rejected -> o.reason
            }
            is com.rout.aiassistant.control.PlanClient.Result.Failure -> {
                // Undeployed endpoint / no valid plan: quiet reset so normal chat continues. Real errors stay visible in the strip.
                if (r.reason.contains("deploy nahi") || r.reason.contains("valid plan nahi")) controlEngine.reset() else controlEngine.planningFailed(r.reason)
                null
            }
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
        viewModelScope.launch { prefs.assistantName.collect { engine.wakeName = it; engine.requireOnDevice = it.isNotBlank() } }
        viewModelScope.launch { engine.wakeBlocked.collect { if (it.isNotBlank()) stopWakeSession() } }
        engine.liveRunning = { live.state.value == "live" || live.state.value == "connecting" }
        engine.onNameOnly = {
            // Only the on-device name-waiting session (saved name) can hand off; the legacy unnamed session never does.
            if (engine.requireOnDevice && engine.recognizerMode.value == "on-device" && handoffReady()) handoff.onNameHeard() else false
        }
        viewModelScope.launch {
            live.state.collect { st ->
                if (st == "off") handoff.onLiveEnded(failed = false)
                else if (st == "failed") handoff.onLiveEnded(failed = true)
            }
        }
        viewModelScope.launch { live.heard.collect { handoff.onActivity() } }
        viewModelScope.launch { live.transcript.collect { handoff.onActivity() } }
        viewModelScope.launch { live.speaking.collect { handoff.onActivity() } }
        viewModelScope.launch { while (true) { kotlinx.coroutines.delay(1000); handoff.tick() } }
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
                confirmSlot.arm(req)
                engine.setState(AssistantState.WAITING_CONFIRMATION)
                // Image / screen-text previews expire: the pending object (and the image bytes in it) is dropped, not just refused later.
                if (confirmSlot.requiresButton(req)) viewModelScope.launch {
                    kotlinx.coroutines.delay(com.rout.aiassistant.commands.ConfirmSlot.IMAGE_TTL_MS)
                    if (confirmSlot.expireIfDue()) engine.setState(AssistantState.IDLE)
                }
            },
            styleNote = { personalityCtl.styleNote(personalityCtl.effective(personality.value, personaMode.value), userName.value) },
            onPermissionNeeded = { cmd -> pendingAfterPermission = cmd },
            visionImages = { visionDoc?.images ?: emptyList() },
            onClearVision = { visionDoc = null },
            controlPlanner = { text -> controlPlan(text) },
            newsBackend = { q ->
                if (isDeviceEnrolled()) com.rout.aiassistant.commands.NewsClient.fetch(backendUrl.value, enrollment.deviceToken, q)
                else com.rout.aiassistant.commands.NewsClient.Result.Failure("Backend enroll nahi hai.")
            }
        )
        engine.initTts()
        // LIVE voice turns: a completed user utterance runs clear device commands
        // locally (app opens, timers, DND...) while the model handles conversation.
        live.onUserTurn = { utter -> onLiveUserTurn(utter) }
        // Tone-only personality settings reach the LIVE model as one compact note when a session is up and whenever they change.
        viewModelScope.launch {
            combine(live.state, personality, userName, personaMode) { st, ps, nm, pid -> Triple(st, personalityCtl.effective(ps, pid), nm) }
                .distinctUntilChanged().collect { (st, ps, nm) ->
                    if (st == "live") personalityCtl.styleNote(ps, nm, compact = true).takeIf { it.isNotEmpty() }?.let { live.sendContextNote(it, cap = 700) }
                }
        }
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
            if (personalityTurn(text, fromLive = false)) return@launch
            // Voice confirmation for a pending call/message: "haan" confirms, "nahi" cancels.
            if (_confirm.value != null) {
                val t = text.trim().lowercase().trimEnd('.', '!')
                val yes = setOf("haan", "ha", "han", "haan ji", "han ji", "yes", "ok", "okay", "kar do", "haan kar do", "confirm", "theek hai", "haan bolo")
                val no = setOf("nahi", "nahin", "na", "no", "cancel", "rehne do", "mat karo", "ruk", "ruko", "nahi karna")
                when {
                    t in yes -> {
                        db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = text))
                        confirmPending(viaVoice = true)
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

    // ---- Personality layer (tone only; everything OFF by default) ----

    fun setPersonality(s: com.rout.aiassistant.ai.PersonalitySettings) { viewModelScope.launch { prefs.setPersonality(s) } }
    fun personalityStop() = viewModelScope.launch { applyPersonalityStop(personality.value.copy(suspended = true), "Theek hai. Ruk gayi. Ab normal baat karte hain.", false) }
    fun personalityResume() { setPersonality(personality.value.copy(suspended = false)) }

    private suspend fun applyPersonalityStop(newSettings: com.rout.aiassistant.ai.PersonalitySettings, ack: String, fromLive: Boolean) {
        prefs.setPersonality(newSettings)
        // STOP outranks everything: silence now, drop queued lines, and clear any pending confirm/draft.
        engine.stopSpeaking()
        live.interruptSpeech()
        confirmSlot.clear()
        if (::router.isInitialized) router.clearDraft()
        engine.setState(AssistantState.IDLE)
        db.conversations().insertMessage(Message(conversationId = conversationId.value, role = "rout", text = ack))
        if (!fromLive) engine.speak(ack)
    }

    /** Handles stop/resume/mode commands and local expressive replies. True when the utterance was fully handled here. */
    internal suspend fun personalityTurn(text: String, fromLive: Boolean, stopOnly: Boolean = false): Boolean {
        val raw = prefs.personalityNow()
        val s = personalityCtl.effective(raw, personaMode.value)   // a calm persona gates what is said; the stored choices are never overwritten by it
        val speaking = assistantState.value == AssistantState.SPEAKING || live.speaking.value
        // LIVE, before the saved-name gate: only STOP is accepted (safety). Every other personality command waits for the gate.
        if (stopOnly && !personalityCtl.isStop(text, s, speaking)) return false
        val out = personalityCtl.decide(text, s, userName.value, speaking, localOnly = fromLive) ?: return false
        val cid = conversationId.value
        db.conversations().insertMessage(Message(conversationId = cid, role = "user", text = text))
        when (out) {
            is com.rout.aiassistant.ai.PersonalityOutcome.Stop -> applyPersonalityStop(raw.copy(suspended = true), out.ack, fromLive)
            is com.rout.aiassistant.ai.PersonalityOutcome.Control -> {
                val rawOut = (personalityCtl.decide(text, raw, userName.value, speaking, localOnly = fromLive) as? com.rout.aiassistant.ai.PersonalityOutcome.Control) ?: out
                prefs.setPersonality(rawOut.settings)
                db.conversations().insertMessage(Message(conversationId = cid, role = "rout", text = out.ack))
                if (!fromLive) engine.speak(out.ack)
            }
            is com.rout.aiassistant.ai.PersonalityOutcome.Reply -> {
                db.conversations().insertMessage(Message(conversationId = cid, role = "rout", text = out.text))
                if (!fromLive) engine.speak(out.text.take(400))
            }
        }
        return true
    }

    fun confirmPending(viaVoice: Boolean = false) {
        val req = when (val t = confirmSlot.take(viaVoice)) {
            is com.rout.aiassistant.commands.ConfirmSlot.Take.Run -> t.req
            com.rout.aiassistant.commands.ConfirmSlot.Take.None -> return
            com.rout.aiassistant.commands.ConfirmSlot.Take.NeedsButton -> {
                // A spoken "haan" must never approve sending screen content: the exact text/image has to be seen and tapped.
                val m = "Ye bhejne ke liye screen par dikh rahi image/text dekh kar Confirm button dabao - bolkar haan se nahi hoga."
                viewModelScope.launch {
                    db.conversations().insertMessage(Message(conversationId = conversationId.value, role = "rout", text = m))
                    engine.speak(m)
                }
                return
            }
            com.rout.aiassistant.commands.ConfirmSlot.Take.Expired -> {
                viewModelScope.launch {
                    val m = "Wo preview purana ho gaya (90 second), isliye kuch nahi bheja. Dobara bolo."
                    db.conversations().insertMessage(Message(conversationId = conversationId.value, role = "rout", text = m))
                    engine.speak(m); engine.setState(AssistantState.IDLE)
                }
                return
            }
        }
        viewModelScope.launch {
            engine.setState(AssistantState.EXECUTING)
            val result = try { req.action() } catch (e: Exception) { "Action failed: ${e.javaClass.simpleName}" }
            db.conversations().insertMessage(Message(conversationId = conversationId.value, role = "rout", text = result))
            engine.speak(result.take(300))
        }
    }

    fun cancelPending() {
        _confirm.value = null
        if (::router.isInitialized) router.clearDraft()
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
        if (::router.isInitialized) router.clearDraft()
        engine.setState(AssistantState.IDLE)
    }

    fun replay(text: String) = engine.speak(text)

    fun startWakeSession() {
        val app = getApplication<Application>()
        engine.requireOnDevice = assistantName.value.isNotBlank()
        // Name-gated session: refused (no service, no mic) when on-device recognition is unavailable. Never falls back to cloud.
        if (!engine.startWakeSession()) { viewModelScope.launch { prefs.setWakeEnabled(false) }; return }
        ContextCompat.startForegroundService(app, Intent(app, ListeningService::class.java))
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
