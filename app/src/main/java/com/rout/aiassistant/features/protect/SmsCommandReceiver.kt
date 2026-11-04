package com.rout.aiassistant.features.protect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.rout.aiassistant.features.protect.ProtectPrefs.antiTheftConfigured
import com.rout.aiassistant.features.protect.ProtectPrefs.secretWord
import com.rout.aiassistant.features.protect.ProtectPrefs.trustedNumber

/**
 * Anti-theft SMS commands from the trusted number (feature must be enabled in Settings):
 *   "<secret> dhundo" -> ring + flash until stopped
 *   "<secret> stop"   -> stop the alarm
 *   "<secret> location" -> reply SMS with last known location (best effort)
 * Sender number must match the saved trusted number.
 */
class SmsCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!context.antiTheftConfigured()) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val trusted = context.trustedNumber.filter { it.isDigit() }
        for (sms in msgs) {
            val from = (sms.originatingAddress ?: "").filter { it.isDigit() }
            if (trusted.isEmpty() || from.isEmpty() || !from.endsWith(trusted.takeLast(10))) continue
            val body = (sms.messageBody ?: "").trim().lowercase()
            val secret = context.secretWord
            if (!body.startsWith(secret)) continue
            val cmd = body.removePrefix(secret).trim()
            when {
                cmd.startsWith("dhundo") || cmd.startsWith("ring") -> {
                    FindPhoneController.start(context)
                    reply(context, sms.originatingAddress, "ROUT: phone baja diya. Stop ke liye '$secret stop' bhejo.")
                }
                cmd.startsWith("stop") -> {
                    FindPhoneController.stop(context)
                    reply(context, sms.originatingAddress, "ROUT: alarm band kar diya.")
                }
                cmd.startsWith("location") -> {
                    val loc = com.rout.aiassistant.util.NetworkStatus.lastKnownLocationText(context)
                    reply(context, sms.originatingAddress, "ROUT location: $loc")
                }
            }
        }
    }

    private fun reply(context: Context, to: String?, text: String) {
        if (to.isNullOrBlank()) return
        try {
            android.telephony.SmsManager.getDefault().sendTextMessage(to, null, text, null, null)
        } catch (_: Exception) {}
    }
}
