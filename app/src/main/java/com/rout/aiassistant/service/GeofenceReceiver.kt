package com.rout.aiassistant.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rout.aiassistant.MainActivity
import com.rout.aiassistant.R

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER && transition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        for (g in event.triggeringGeofences ?: emptyList()) {
            val entry = GeofenceStore.find(context, g.requestId) ?: continue
            val open = PendingIntent.getActivity(
                context, entry.id.hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val where = if (entry.onExit) "se nikal gaye" else "pahunch gaye"
            val n = NotificationCompat.Builder(context, "reminders")
                .setSmallIcon(R.drawable.ic_stat_rout)
                .setContentTitle("ROUT location reminder")
                .setContentText("${entry.place} $where: ${entry.task}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("${entry.place} $where: ${entry.task}"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            try { NotificationManagerCompat.from(context).notify(entry.id.hashCode(), n) } catch (_: SecurityException) {}
            // One-shot: fire once, then remove.
            GeofenceManager.cancel(context, entry.id)
        }
    }
}
