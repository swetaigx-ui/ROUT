package com.rout.aiassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.app.Service
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rout.aiassistant.MainActivity
import com.rout.aiassistant.R
import com.rout.aiassistant.ai.Enrollment
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.commands.ConfirmRequest
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import com.rout.aiassistant.ui.OrbFloatView
import com.rout.aiassistant.voice.AssistantState
import com.rout.aiassistant.voice.BackendSpeaker
import com.rout.aiassistant.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Floating ROUT orb: full voice conversation WITHOUT opening the app.
 * Tap = start/stop talking; replies are spoken from the overlay itself.
 * Drag to move; long-press dismisses. Created only when the user enables it.
 *
 * Honest Android note: the mic needs a foreground mic-session, so tapping the
 * orb upgrades this service to a mic foreground service for the turn only,
 * then drops back. If the system refuses, the app opens as the legal fallback.
 */
class FloatingOrbService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wm: WindowManager? = null
    private var orb: OrbFloatView? = null
    private var engine: VoiceEngine? = null
    private var router: CommandRouter? = null
    private var enrollment: Enrollment? = null
    private var pendingConfirm: ConfirmRequest? = null
    private var micForeground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (orb != null) return START_STICKY
        wm = getSystemService(WindowManager::class.java)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 300 }

        val view = OrbFloatView(this)
        view.onTap = { toggleTalk() }
        view.onLongPress = { stopSelf() }
        view.attachDrag(wm!!, lp)
        wm?.addView(view, lp)
        orb = view
        return START_STICKY
    }

    private fun buildEngine(): VoiceEngine {
        val en = Enrollment(this).also { enrollment = it }
        val prefs = Prefs(this)
        val db = RoutDatabase.get(this)
        val e = VoiceEngine(this, onFinalText = { text -> handleOverlayText(text) })
        e.backendSpeaker = BackendSpeaker(this)
        e.deviceTokenProvider = { en.deviceToken ?: "" }
        scope.launch { prefs.backendUrl.collect { e.backendUrl = it } }
        scope.launch { prefs.voiceName.collect { e.voiceName = it } }
        scope.launch { prefs.language.collect { e.languageTag = it } }
        scope.launch {
            e.state.collect { st ->
                orb?.setAssistantState(st)
                if (micForeground && (st == AssistantState.IDLE || st == AssistantState.ERROR)) dropMicForeground()
            }
        }
        e.initTts()
        val base = Prefs.DEFAULT_BACKEND_URL
        router = CommandRouter(
            context = this, db = db, prefs = prefs,
            providerManager = {
                val pid = kotlinx.coroutines.runBlocking { prefs.personaMode.first() }
                ProviderManager(base, prefs.devApiKey, false, en, pid)
            },
            history = { emptyList() },
            requestConfirmation = { req -> pendingConfirm = req }
        )
        engine = e
        return e
    }

    private fun toggleTalk() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Mic permission can only come from the app UI - open it, honestly.
            openApp()
            return
        }
        val e = engine ?: buildEngine()
        when (e.state.value) {
            AssistantState.LISTENING -> e.stopListening()
            AssistantState.SPEAKING -> { e.stopSpeaking() }
            else -> {
                // Mic from an overlay needs a foreground mic session. Try it;
                // if the system refuses, the app opens as the legal route.
                try {
                    val n: Notification = NotificationCompat.Builder(this, "listening")
                        .setSmallIcon(R.drawable.ic_stat_rout)
                        .setContentTitle("ROUT sun raha hai")
                        .setContentText("Orb se baat chal rahi hai.")
                        .setOngoing(true)
                        .build()
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    } else {
                        startForeground(2, n)
                    }
                    micForeground = true
                    e.startListening()
                } catch (ex: Exception) {
                    micForeground = false
                    openApp()
                }
            }
        }
    }

    private fun dropMicForeground() {
        micForeground = false
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH)
            else @Suppress("DEPRECATION") stopForeground(false)
        } catch (_: Exception) {}
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun handleOverlayText(text: String) {
        if (text.isBlank()) return
        val e = engine ?: return
        val r = router ?: return
        // Voice confirm/cancel for a pending overlay action - same words as in-app.
        val pc = pendingConfirm
        if (pc != null) {
            val t = text.trim().lowercase().trimEnd('.', '!')
            val yes = setOf("haan", "ha", "han", "yes", "ok", "okay", "kar do", "theek hai", "confirm")
            val no = setOf("nahi", "nahin", "na", "no", "cancel", "rehne do", "mat karo")
            when {
                t in yes -> {
                    pendingConfirm = null
                    scope.launch {
                        val result = try { pc.action() } catch (ex: Exception) { "Action failed: ${ex.javaClass.simpleName}" }
                        e.speak(result.take(300))
                    }
                    return
                }
                t in no -> {
                    pendingConfirm = null
                    e.speak("Theek hai, cancel kar diya.")
                    return
                }
                else -> pendingConfirm = null
            }
        }
        scope.launch {
            val reply = try { r.handle(text) } catch (ex: Exception) { "Kuch gadbad ho gayi: ${ex.javaClass.simpleName}" }
            r.consumePreAction()?.let { e.speak(it) }
            if (pendingConfirm == null) e.speak(reply.take(400)) else e.speak(reply.take(400))
        }
    }

    override fun onDestroy() {
        engine?.stopListening(); engine?.stopSpeaking()
        orb?.let { wm?.removeView(it) }
        orb = null
        super.onDestroy()
    }
}
