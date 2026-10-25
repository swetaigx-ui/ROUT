package com.rout.aiassistant.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rout.aiassistant.MainActivity
import com.rout.aiassistant.R
import com.rout.aiassistant.commands.DeviceActions

/** Fires at the scheduled bedtime: turns DND on and re-arms for the next night. */
class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DeviceActions.setDnd(context, true)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_stat_rout)
            .setContentTitle("ROUT bedtime")
            .setContentText("Do Not Disturb on kar diya. Shubh ratri!")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(context).notify(777000111, n) } catch (_: SecurityException) {}
        // Re-arm for tomorrow.
        val hour = intent.getIntExtra("hour", 23); val minute = intent.getIntExtra("minute", 0)
        val next = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val again = PendingIntent.getBroadcast(
            context, 777000111,
            Intent(context, BedtimeReceiver::class.java).putExtra("hour", hour).putExtra("minute", minute),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            context.getSystemService(android.app.AlarmManager::class.java)
                .setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next.timeInMillis, again)
        } catch (_: Exception) {}
    }
}
