package com.rout.aiassistant.features.briefing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Morning briefing: daily exact alarm that speaks weather + news headlines aloud. Opt-in time; OFF when unset. */
object BriefingPrefs {
    private const val FILE = "rout_briefing"
    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    var Context.briefingMinutes: Int  // minutes since midnight, -1 = off
        get() = p(this).getInt("at", -1)
        set(v) = p(this).edit().putInt("at", v).apply()
}

object BriefingScheduler {
    fun schedule(ctx: Context, minutes: Int) {
        ctx.alarmManager().cancel(pi(ctx))
        if (minutes < 0) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && ctx.alarmManager().canScheduleExactAlarms())
                ctx.alarmManager().setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi(ctx))
            else ctx.alarmManager().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi(ctx))
        } catch (_: SecurityException) {
            ctx.alarmManager().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi(ctx))
        }
    }
    private fun pi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 9042, Intent(ctx, BriefingReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun Context.alarmManager() = getSystemService(Context.ALARM_SERVICE) as AlarmManager
}

class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // re-arm for tomorrow
        val at = with(BriefingPrefs) { context.briefingMinutes }
        BriefingScheduler.schedule(context, at)
        if (at < 0) return
        BriefingService.start(context)
    }
}
