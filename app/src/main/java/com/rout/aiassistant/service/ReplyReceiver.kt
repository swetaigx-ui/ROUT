package com.rout.aiassistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.rout.aiassistant.ai.Enrollment
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import com.rout.aiassistant.voice.BackendSpeaker
import com.rout.aiassistant.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles an inline notification reply (voice via keyboard mic, or typed):
 * routes it through the normal command path, speaks the answer, and posts it
 * back as another replyable notification so the conversation continues
 * without the app ever opening.
 */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(RoutNotify.KEY_REPLY)?.toString()?.trim()
        if (text.isNullOrBlank()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                val prefs = Prefs(context)
                val en = Enrollment(context)
                val db = RoutDatabase.get(context)
                val router = CommandRouter(
                    context = context, db = db, prefs = prefs,
                    providerManager = { ProviderManager(Prefs.DEFAULT_BACKEND_URL, prefs.devApiKey, false, en) },
                    history = { emptyList() },
                    requestConfirmation = { }
                )
                val reply = try { router.handle(text) } catch (ex: Exception) { "Kuch gadbad ho gayi: ${ex.javaClass.simpleName}" }
                val pre = router.consumePreAction()

                // Speak through the normal voice path (playback is allowed from background).
                val engine = VoiceEngine(context, onFinalText = {})
                engine.backendSpeaker = BackendSpeaker(context)
                engine.deviceTokenProvider = { en.deviceToken ?: "" }
                engine.backendUrl = prefs.backendUrl.first()
                engine.voiceName = prefs.voiceName.first()
                engine.languageTag = prefs.language.first()
                engine.initTts()
                pre?.let { engine.speak(it) }
                engine.speak(reply.take(400))

                RoutNotify.postReply(context, reply)
            } finally {
                pending.finish()
            }
        }
    }
}
