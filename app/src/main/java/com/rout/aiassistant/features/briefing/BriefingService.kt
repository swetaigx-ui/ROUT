package com.rout.aiassistant.features.briefing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Speaks the morning briefing (weather + top headlines) with the device TTS, then stops. */
class BriefingService : Service() {
    companion object {
        const val CH = "rout_briefing"
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, BriefingService::class.java))
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tts: TextToSpeech? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "Morning briefing", NotificationManager.IMPORTANCE_LOW))
        val notif: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ROUT briefing").setSilent(true).build()
        startForeground(73, notif)

        scope.launch {
            val parts = ArrayList<String>()
            try {
                val city = com.rout.aiassistant.data.Prefs(this@BriefingService).weatherCity
                    .first()
                if (city.isNotBlank()) {
                    val geo = com.rout.aiassistant.commands.WeatherService.geocode(city)
                    val w = geo?.let { (lat, lon, label) -> com.rout.aiassistant.commands.WeatherService.current(lat, lon, label) }
                    if (w != null) parts.add("Weather ${w.place}: ${w.tempC.toInt()} degree, ${w.summary}.")
                }
            } catch (_: Exception) {}
            try {
                val heads = com.rout.aiassistant.commands.NewsService.headlines(hindi = true, limit = 3)
                if (heads.isNotEmpty()) parts.add("Aaj ki khabrein. " + heads.joinToString(". ") + ".")
            } catch (_: Exception) {}
            if (parts.isEmpty()) parts.add("Good morning! Aaj ka briefing abhi available nahi hai - internet check karo.")
            speak(parts.joinToString(" "))
        }
        return START_NOT_STICKY
    }

    private fun speak(text: String) {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { stopSelf() }
                    override fun onError(id: String?) { stopSelf() }
                })
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "briefing")
            } else stopSelf()
        }
    }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
