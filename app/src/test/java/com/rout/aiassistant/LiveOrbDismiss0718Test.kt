package com.rout.aiassistant

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ui.DismissTargetView
import com.rout.aiassistant.ui.LiveFloatView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class LiveOrbDismiss0718Test {

    @Test fun dismissZoneHitTest() {
        val w = 1080; val h = 2400
        assertTrue(DismissTargetView.isOver(w / 2f, h - 200f, w, h))
        assertTrue(DismissTargetView.isOver(w / 2f + 100f, h - 200f + 60f, w, h))
        assertFalse(DismissTargetView.isOver(w / 2f, h / 2f, w, h))          // middle of the screen
        assertFalse(DismissTargetView.isOver(40f, h - 200f, w, h))           // far left of X
        assertFalse(DismissTargetView.isOver(-1f, -1f, w, h))                // cancelled gesture never dismisses
    }

    @Test fun dragEndCallbacksFireOnlyAfterRealDrag() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val v = LiveFloatView(ctx)
        val lp = android.view.WindowManager.LayoutParams()
        v.attachDrag(ctx.getSystemService(android.view.WindowManager::class.java), lp)
        var starts = 0; var ends = 0; var taps = 0
        v.onDragStart = { starts++ }; v.onDragEnd = { _, _ -> ends++ }; v.onTap = { taps++ }
        fun ev(a: Int, x: Float, y: Float) = android.view.MotionEvent.obtain(0, 0, a, x, y, 0)
        // plain tap: no drag callbacks, tap fires
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_DOWN, 100f, 100f))
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_UP, 100f, 100f))
        assertEquals(0, starts); assertEquals(0, ends); assertEquals(1, taps)
        // real drag: start once, end once, no tap
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_DOWN, 100f, 100f))
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_MOVE, 100f, 400f))
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_MOVE, 120f, 900f))
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_UP, 120f, 900f))
        assertEquals(1, starts); assertEquals(1, ends); assertEquals(1, taps)
    }

    /** SYNTHETIC render: the real LiveFloatView/DismissTargetView drawn on a plain backdrop. Not a phone screenshot. */
    @Test fun renderStates() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = Bitmap.createBitmap(1000, 420, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.parseColor("#2A2F3A"))
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
        fun draw(v: View, x: Float, y: Float, label: String) {
            v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            c.save(); c.translate(x, y); v.draw(c); c.restore()
            c.drawText(label, x - 10, y + v.measuredHeight + 40f, t)
        }
        listOf(0 to "connecting", 1 to "listening", 2 to "speaking").forEachIndexed { i, (m, n) ->
            val v = LiveFloatView(ctx); v.setMode(m); draw(v, 40f + i * 200f, 60f, n)
        }
        val x0 = DismissTargetView(ctx); draw(x0, 660f, 60f, "X idle")
        val x1 = DismissTargetView(ctx); x1.active = true; draw(x1, 830f, 60f, "X active")
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        FileOutputStream(File(dir, "live_orb_states_0718.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
