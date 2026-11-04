package com.rout.aiassistant.features.protect

import android.content.Context
import com.rout.aiassistant.features.protect.ProtectPrefs.appLockOn
import com.rout.aiassistant.features.protect.ProtectPrefs.appLockPin
import com.rout.aiassistant.features.protect.ProtectPrefs.dhundoOn
import java.util.Calendar

/**
 * Voice/text commands for the protection bundle. Returns null when the input is not a protect command.
 * Triggered directly by the user's voice, so these run regardless of the opt-in toggles
 * (toggles gate the automatic triggers - SMS, failed unlock, app open).
 */
object ProtectCommands {
    fun handle(ctx: Context, raw: String): String? {
        val t = raw.lowercase().trim().trimEnd('.', '!')

        // Phone Dhundo
        if (t == "phone dhundo" || t == "phone bajao" || t == "find my phone" || t == "mera phone dhundo") {
            FindPhoneController.start(ctx)
            return "Phone baja rahi hoon - rokne ke liye 'dhundo band karo' bolo ya app mein Stop dabao."
        }
        if (t == "dhundo band karo" || t == "phone bajana band karo" || t == "ring band karo") {
            if (!FindPhoneController.isRinging()) return "Abhi kuch nahi baj raha."
            FindPhoneController.stop(ctx)
            return "Band kar diya."
        }

        // Smart Alarm: "smart alarm lagao 6 baje", "smart alarm 6:30 subah", "smart alarm set karo 7 pm"
        if (t.startsWith("smart alarm")) {
            val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(baje)?\\s*(am|pm|subah|shaam|sham|raat|dopahar)?")
                .find(t) ?: return "Smart alarm kab ka? Bolo jaise 'smart alarm lagao 6 baje subah'."
            var h = m.groupValues[1].toIntOrNull() ?: return "Time samajh nahi aaya."
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val mer = m.groupValues[4]
            if (h in 1..12 && (mer == "pm" || mer == "shaam" || mer == "sham" || mer == "raat" || mer == "dopahar") && h != 12) h += 12
            if (mer == "am" && h == 12) h = 0
            if (h > 23 || min > 59) return "Time samajh nahi aaya."
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            SmartAlarmScheduler.schedule(ctx, cal.timeInMillis, "Smart alarm")
            val when_ = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(cal.time)
            return "Smart alarm laga diya: $when_. Bajega to poori screen kholegi - Stop ya Snooze dabana."
        }
        if (t == "smart alarms dikhao" || t == "mere smart alarms") {
            val all = SmartAlarmStore.all(ctx)
            if (all.isEmpty()) return "Koi smart alarm set nahi hai."
            val fmt = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
            return all.joinToString("\n") { "- " + fmt.format(java.util.Date(it.triggerAt)) }
        }
        return null
    }
}
