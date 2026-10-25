package com.rout.aiassistant.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.rout.aiassistant.R

/**
 * Posts a ROUT reply as a notification with an inline RemoteInput action -
 * the user can answer by voice (keyboard mic) or text without opening the app,
 * exactly like replying to a WhatsApp message.
 */
object RoutNotify {

    const val KEY_REPLY = "rout_reply"
    private const val CHANNEL = "chat"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm?.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL, "ROUT replies", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Assistant replies you can answer inline"
            }
        )
    }

    fun postReply(context: Context, replyText: String) {
        ensureChannel(context)
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Jawab bolo ya likho")
            .build()
        val replyIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ReplyReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_rout, "Jawab do", replyIntent
        ).addRemoteInput(remoteInput).build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rout)
            .setContentTitle("ROUT")
            .setContentText(replyText.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(replyText))
            .addAction(action)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(context).notify(4242, n) } catch (_: SecurityException) {}
    }
}
