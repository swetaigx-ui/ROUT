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

    /** Reply-capable notification threads. Called only when the in-app reply approval is ON. Nothing is stored or uploaded. */
    fun replyTargets(): List<com.rout.aiassistant.commands.ReplyTarget> {
        val pm = packageManager
        return activeNotifications?.mapNotNull { sbn ->
            try {
                val n = sbn.notification
                val has = n.actions?.any { a -> a.remoteInputs?.isNotEmpty() == true } == true
                if (!has) return@mapNotNull null
                val e = n.extras
                val sender = e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.take(60) ?: return@mapNotNull null
                val group = e.getBoolean(android.app.Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
                    e.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE) != null
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
                com.rout.aiassistant.commands.ReplyTarget(sbn.key, sbn.packageName, app, sender, group)
            } catch (_: Exception) { null }
        } ?: emptyList()
    }

    /** Sends [words] through the thread's own reply action. Returns false if the thread or action is gone. */
    fun sendReply(key: String, words: String): Boolean {
        val sbn = activeNotifications?.firstOrNull { it.key == key } ?: return false
        val action = sbn.notification.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true } ?: return false
        return try {
            val intent = android.content.Intent()
            val b = android.os.Bundle()
            for (ri in action.remoteInputs) b.putCharSequence(ri.resultKey, words)
            android.app.RemoteInput.addResultsToIntent(action.remoteInputs, intent, b)
            action.actionIntent.send(this, 0, intent)
            true
        } catch (_: Exception) { false }
    }
}
