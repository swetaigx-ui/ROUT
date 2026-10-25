package com.rout.aiassistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-register location reminders after a reboot - geofences do not survive it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GeofenceManager.reRegisterAll(context)
        }
    }
}
