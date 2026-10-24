package com.rout.aiassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rout.aiassistant.MainActivity
import com.rout.aiassistant.R

/**
 * Foreground microphone session. Started ONLY from the foreground, when the user
 * explicitly enables the "Hey ROUT" session in the app. Android 12+ background
 * microphone starts and Android 15 BOOT_COMPLETED mic-FSG launches are prohibited,
 * and ROUT does not attempt them.
 */
class ListeningService : Service() {

    companion object {
        const val ACTION_STOP = "com.rout.aiassistant.STOP_LISTENING"
        var running = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(); return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, "listening")
            .setSmallIcon(R.drawable.ic_stat_rout)
            .setContentTitle("ROUT is listening")
            .setContentText("Hey ROUT session active. Tap STOP to end.")
            .setContentIntent(open)
            .addAction(0, "STOP", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
