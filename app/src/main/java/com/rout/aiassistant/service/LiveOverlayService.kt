package com.rout.aiassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.graphics.PixelFormat
import androidx.core.app.NotificationCompat
import com.rout.aiassistant.MainActivity
import com.rout.aiassistant.R
import com.rout.aiassistant.RoutApplication
import com.rout.aiassistant.ui.LiveFloatView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Floating LIVE companion. Started ONLY from the foreground when the user taps
 * LIVE (explicit activation - never silent). While LIVE runs and the app is not
 * on screen, a small Siri-style flowing orb floats over other apps: the mic
 * keeps streaming (this service holds the mic foreground type), replies keep
 * playing, tap reopens ROUT, long-press ends LIVE. When the app returns to the
 * foreground the overlay hides itself. The service follows the LIVE state and
 * stops itself when LIVE ends or fails.
 */
class LiveOverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.rout.aiassistant.STOP_LIVE"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wm: WindowManager? = null
    private var floatView: LiveFloatView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            RoutApplication.live.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LiveOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, "listening")
            .setSmallIcon(R.drawable.ic_stat_rout)
            .setContentTitle("ROUT LIVE on")
            .setContentText("Back dabaoge to bhi ROUT sunta rahega. STOP se band karein.")
            .setContentIntent(open)
            .addAction(0, "STOP", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(3, n)
        }
        wm = getSystemService(WindowManager::class.java)

        // Follow the LIVE state: end of the session ends this service.
        scope.launch {
            RoutApplication.live.state.collect { st ->
                if (st == "off" || st == "failed") stopSelf()
            }
        }
        // Overlay follows app visibility; orb brightness follows live speaking.
        scope.launch {
            while (isActive) {
                syncOverlay()
                delay(400)
            }
        }
        return START_STICKY
    }

    private fun syncOverlay() {
        val st = RoutApplication.live.state.value
        val active = st == "live" || st == "connecting"
        val wantOverlay = active && !RoutApplication.foreground && Settings.canDrawOverlays(this)
        val have = floatView != null
        if (wantOverlay && !have) {
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 320 }
            val view = LiveFloatView(this)
            view.onTap = {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            view.onLongPress = { RoutApplication.live.stop() }
            view.onDragStart = { showDismiss() }
            view.onDragMove = { x, y -> dismissView?.active = com.rout.aiassistant.ui.DismissTargetView.isOver(x, y, screenW(), screenH()) }
            view.onDragEnd = { x, y ->
                val over = com.rout.aiassistant.ui.DismissTargetView.isOver(x, y, screenW(), screenH())
                hideDismiss()
                // Dropped on X: end LIVE (stops the mic session and websocket) and close this service.
                if (over) { RoutApplication.live.stop(); stopSelf() }
            }
            view.attachDrag(wm!!, lp)
            try { wm?.addView(view, lp); floatView = view } catch (_: Exception) {}
        } else if (!wantOverlay && have) {
            try { wm?.removeView(floatView) } catch (_: Exception) {}
            floatView = null
        }
        floatView?.setMode(when {
            RoutApplication.live.speaking.value -> 2
            st == "live" -> 1
            else -> 0
        })
    }

    private var dismissView: com.rout.aiassistant.ui.DismissTargetView? = null
    private fun screenW() = resources.displayMetrics.widthPixels
    private fun screenH() = resources.displayMetrics.heightPixels
    private fun showDismiss() {
        if (dismissView != null) return
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val sz = com.rout.aiassistant.ui.DismissTargetView.SIZE
        val lp = WindowManager.LayoutParams(sz, sz, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = screenW() / 2 - sz / 2; y = screenH() - 200 - sz / 2 }
        val v = com.rout.aiassistant.ui.DismissTargetView(this)
        try { wm?.addView(v, lp); dismissView = v } catch (_: Exception) {}
    }
    private fun hideDismiss() {
        dismissView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        dismissView = null
    }

    override fun onDestroy() {
        hideDismiss()
        floatView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        floatView = null
        super.onDestroy()
    }
}
