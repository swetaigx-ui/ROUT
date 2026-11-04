package com.rout.aiassistant.features.protect

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.rout.aiassistant.features.protect.ProtectPrefs.failedUnlocks
import com.rout.aiassistant.features.protect.ProtectPrefs.intruderOn

/** Device admin: counts failed unlocks; on a successful unlock after failures, snaps a front-camera photo. */
class IntruderAdminReceiver : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent) {
        if (!context.intruderOn) return
        context.failedUnlocks = context.failedUnlocks + 1
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        if (!context.intruderOn) return
        if (context.failedUnlocks > 0) {
            IntruderCaptureService.start(context)
            context.failedUnlocks = 0
        }
    }
}
