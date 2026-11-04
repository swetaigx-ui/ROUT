package com.rout.aiassistant.features.protect

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Smart Alarm: exact alarms with full-screen wake + dismiss, rescheduled on boot.
 * Separate from ROUT's Clock-app intents - this one fires inside ROUT so "alarm bajao 6 baje"
 * works even if no clock app handles it.
 */
object SmartAlarmStore {
    private const val FILE = "rout_smart_alarms"
    private const val KEY = "alarms"

    data class Alarm(val id: Int, val triggerAt: Long, val label: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Alarm> {
        val arr = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); Alarm(o.getInt("id"), o.getLong("at"), o.optString("label")) }
            .filter { it.triggerAt > System.currentTimeMillis() }
    }

    fun add(ctx: Context, a: Alarm) {
        val arr = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
        arr.put(JSONObject().put("id", a.id).put("at", a.triggerAt).put("label", a.label))
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun remove(ctx: Context, id: Int) {
        val arr = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
        val out = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).getInt("id") != id) out.put(arr.getJSONObject(i))
        prefs(ctx).edit().putString(KEY, out.toString()).apply()
    }
}

object SmartAlarmScheduler {
    fun schedule(ctx: Context, triggerAt: Long, label: String): Int {
        val id = (triggerAt / 1000).toInt()
        SmartAlarmStore.add(ctx, SmartAlarmStore.Alarm(id, triggerAt, label))
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pi(ctx, id, label)
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        return id
    }

    fun cancel(ctx: Context, id: Int, label: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, id, label))
        SmartAlarmStore.remove(ctx, id)
    }

    fun rescheduleAll(ctx: Context) {
        for (a in SmartAlarmStore.all(ctx)) schedule(ctx, a.triggerAt, a.label)
    }

    private fun pi(ctx: Context, id: Int, label: String): PendingIntent {
        val i = Intent(ctx, SmartAlarmReceiver::class.java).putExtra("label", label)
        return PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class SmartAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "Alarm"
        val i = Intent(context, SmartAlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("label", label)
        context.startActivity(i)
    }
}
