package com.rout.aiassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class RoutApplication : Application() {
    companion object {
        @Volatile var foreground: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
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
