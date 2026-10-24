package com.rout.aiassistant.service

import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.app.Service
import com.rout.aiassistant.MainActivity
import com.rout.aiassistant.ui.OrbFloatView

/**
 * Floating ROUT orb. Created ONLY when the user enables it and grants the
 * overlay permission. Tap opens ROUT; long-press stops and removes the orb.
 */
class FloatingOrbService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private var wm: WindowManager? = null
    private var orb: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (orb != null) return START_STICKY
        wm = getSystemService(WindowManager::class.java)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 300 }

        val view = OrbFloatView(this)
        view.onTap = {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
        view.onLongPress = { stopSelf() }
        view.attachDrag(wm!!, lp)
        wm?.addView(view, lp)
        orb = view
        return START_STICKY
    }

    override fun onDestroy() {
        orb?.let { wm?.removeView(it) }
        orb = null
        super.onDestroy()
    }
}
