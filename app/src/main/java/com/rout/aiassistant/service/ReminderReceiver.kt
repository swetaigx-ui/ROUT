package com.rout.aiassistant.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rout.aiassistant.MainActivity
import com.rout.aiassistant.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_stat_rout)
            .setContentTitle("ROUT reminder")
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(context).notify(title.hashCode(), n) } catch (_: SecurityException) {}
    }
}
