package com.rout.aiassistant.service

import android.service.notification.NotificationListenerService

/**
 * MediaSession listener for media control, plus - only when the user asks by voice -
 * reading recent notifications aloud. Enabled explicitly by the user in system
 * notification-access settings. Notification contents are read on demand for the
 * "read my messages" command; they are never stored or uploaded.
 */
class RoutMediaListenerService : NotificationListenerService() {
    companion object {
        @Volatile var instance: RoutMediaListenerService? = null
        val isEnabled: Boolean get() = instance != null
    }

    override fun onListenerConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null }

    /** Recent visible notifications as "App: title - text" lines, newest first. */
    fun recentSummaries(limit: Int = 5): List<String> {
        val pm = packageManager
        return activeNotifications?.take(limit)?.mapNotNull { sbn ->
            try {
                val e = sbn.notification.extras
                val title = e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.take(60)
                val text = e.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.take(120)
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }
                    .getOrDefault(sbn.packageName)
                if (title == null && text == null) null
                else listOfNotNull(app, title, text).joinToString(": ")
            } catch (_: Exception) { null }
        } ?: emptyList()
    }
}
