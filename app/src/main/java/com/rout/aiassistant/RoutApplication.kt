package com.rout.aiassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class RoutApplication : Application() {
    companion object {
        @Volatile var foreground: Boolean = false
        lateinit var instance: RoutApplication
            private set

        /**
         * The LIVE session lives on the Application, not the Activity ViewModel:
         * Gyan's ask (2026-10-27) is that starting LIVE and pressing Back leaves a
         * floating UI that keeps listening - so leaving the screen must not kill it.
         * It stops only on an explicit toggle/STOP or a connection failure.
         */
        val live: com.rout.aiassistant.voice.LiveSession by lazy { com.rout.aiassistant.voice.LiveSession(instance) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("listening", "Listening session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while ROUT is listening in a foreground session"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "ROUT task reminders"
            }
        )
    }
}
