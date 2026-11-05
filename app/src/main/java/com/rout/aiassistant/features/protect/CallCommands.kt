package com.rout.aiassistant.features.protect

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import com.rout.aiassistant.features.protect.ProtectPrefs.antiTheftOn

/** "call uthao" / "call kato" via Telecom API (needs ANSWER_PHONE_CALLS permission). */
object CallCommands {
    fun handle(ctx: Context, raw: String): String? {
        val t = raw.lowercase().trim().trimEnd('.', '!')
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        when (t) {
            "call uthao", "phone uthao", "call answer karo" -> {
                if (Build.VERSION.SDK_INT < 26) return "Is Android version par call uthana support nahi."
                return try {
                    tm.acceptRingingCall()
                    "Call utha li."
                } catch (e: SecurityException) { "Call answer ki permission nahi hai - Settings > Security se allow karo." }
            }
            "call kato", "call kaat do", "phone kaat do", "call end karo" -> {
                if (Build.VERSION.SDK_INT < 28) return "Is Android version par call kaatna support nahi."
                return try {
                    if (tm.endCall()) "Call kaat diya." else "Abhi koi active call nahi."
                } catch (e: SecurityException) { "Call end ki permission nahi hai - Settings > Security se allow karo." }
            }
        }
        return null
    }
}
