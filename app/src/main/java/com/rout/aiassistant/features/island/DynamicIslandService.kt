package com.rout.aiassistant.features.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * Dynamic Island overlay: a small pill at the top-center (camera-cutout area) that
 * expands to show ROUT's current status (listening, speaking, timer, alarm, media).
 * Opt-in (default OFF); needs the overlay permission granted by the user.
 */
class DynamicIslandService : Service() {
    companion object {
        const val CH = "rout_island"
        const val EXTRA_TEXT = "text"
        const val EXTRA_STATE = "state" // idle | listening | speaking | working
        private var view: IslandView? = null

        fun update(ctx: Context, text: String, state: String) {
            val i = Intent(ctx, DynamicIslandService::class.java)
                .putExtra(EXTRA_TEXT, text).putExtra(EXTRA_STATE, state)
            ctx.startService(i)
        }
        fun stopOverlay(ctx: Context) = ctx.stopService(Intent(ctx, DynamicIslandService::class.java))
        fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)
    }

    private lateinit var wm: WindowManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "Dynamic Island", NotificationManager.IMPORTANCE_MIN))
        val notif: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ROUT Island").setSilent(true).build()
        startForeground(72, notif)

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val state = intent?.getStringExtra(EXTRA_STATE) ?: "idle"
        if (view == null) {
            val v = IslandView(this)
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 12 }
            wm.addView(v, lp)
            view = v
        }
        view?.setStatus(text, state)
        if (state == "idle" && text.isBlank()) { removeView(); stopSelf() }
        return START_STICKY
    }

    private fun removeView() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }

    override fun onDestroy() { removeView(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
