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
        val runCommand = intent.getStringExtra("run_command")
        val openIntent = Intent(context, MainActivity::class.java)
        if (!runCommand.isNullOrBlank()) openIntent.putExtra("run_command", runCommand)
        val open = PendingIntent.getActivity(
            context, 0, openIntent,
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
        // Daily briefings re-arm themselves for tomorrow.
        if (!runCommand.isNullOrBlank() && intent.hasExtra("hour")) {
            val am = context.getSystemService(android.app.AlarmManager::class.java)
            val next = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, intent.getIntExtra("hour", 7))
                set(java.util.Calendar.MINUTE, intent.getIntExtra("minute", 0))
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val again = PendingIntent.getBroadcast(
                context, 987654321,
                Intent(context, ReminderReceiver::class.java)
                    .putExtra("title", title).putExtra("run_command", runCommand)
                    .putExtra("hour", intent.getIntExtra("hour", 7))
                    .putExtra("minute", intent.getIntExtra("minute", 0)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try { am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next.timeInMillis, again) } catch (_: Exception) {}
        }
    }
}
