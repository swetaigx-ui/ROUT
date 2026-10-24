package com.rout.aiassistant.commands

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.provider.Settings

object MediaControl {

    fun notificationAccessEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(context.packageName)
    }

    fun openAccessSettings(context: Context) {
        val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    /** Returns null when notification access is off or no controllable session exists. */
    private fun controller(context: Context): android.media.session.MediaController? {
        if (!notificationAccessEnabled(context)) return null
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val cn = ComponentName(context, com.rout.aiassistant.service.RoutMediaListenerService::class.java)
        return try {
            msm.getActiveSessions(cn).firstOrNull()
        } catch (e: SecurityException) { null }
    }

    fun playPause(context: Context): ActionResult {
        val c = controller(context)
            ?: return ActionResult(false, "Media control needs notification access permission, or nothing is playing.")
        val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        c.dispatchMediaButtonEvent(event)
        c.dispatchMediaButtonEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        return ActionResult(true, "Play/pause sent to " + (c.packageName ?: "media app"))
    }

    fun next(context: Context): ActionResult {
        val c = controller(context)
            ?: return ActionResult(false, "Media control needs notification access permission, or nothing is playing.")
        c.transportControls.skipToNext()
        return ActionResult(true, "Skipped to next track")
    }

    fun previous(context: Context): ActionResult {
        val c = controller(context)
            ?: return ActionResult(false, "Media control needs notification access permission, or nothing is playing.")
        c.transportControls.skipToPrevious()
        return ActionResult(true, "Back to previous track")
    }
}
