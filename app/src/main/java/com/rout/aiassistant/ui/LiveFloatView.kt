package com.rout.aiassistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The floating LIVE orb: a small Siri-style disc with colours flowing inside.
 * Drawn only while the user-started LIVE session runs and the app is not on
 * screen. Drag to move, tap to reopen ROUT, long-press ends LIVE.
 */
@SuppressLint("ViewConstructor")
class LiveFloatView(context: Context) : View(context) {

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    private val palette = intArrayOf(
        Color.parseColor("#5EF2FF"), Color.parseColor("#3B82F6"), Color.parseColor("#8B5CF6"),
        Color.parseColor("#D946EF"), Color.parseColor("#FF5F7E")
    )
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F004050A"); style = Paint.Style.FILL }
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    @Volatile private var speaking = false
    private var phase = 0f
    private var wm: WindowManager? = null
    private var lp: WindowManager.LayoutParams? = null

    private val tick = object : Runnable {
        override fun run() {
            phase += if (speaking) 0.09f else 0.035f
            invalidate()
            postOnAnimation(this)
        }
    }

    fun setSpeaking(s: Boolean) { speaking = s }

    fun attachDrag(wm: WindowManager, lp: WindowManager.LayoutParams) {
        this.wm = wm; this.lp = lp
        removeCallbacks(tick); postOnAnimation(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(150, 150) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = width / 2f
        val r = 58f
        canvas.drawCircle(c, c, r + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(c, c, r + 14f, palette[2], Color.TRANSPARENT, Shader.TileMode.CLAMP)
            alpha = if (speaking) 90 else 50
        })
        canvas.save()
        canvas.clipPath(android.graphics.Path().apply { addCircle(c, c, r, android.graphics.Path.Direction.CW) })
        canvas.drawCircle(c, c, r, base)
        for (i in palette.indices) {
            val a = phase * (1f + i * 0.3f) + i * 1.3f
            val x = c + cos(a) * r * 0.45f
            val y = c + sin(a * 1.15f + i) * r * 0.45f
            val rad = r * (0.7f + 0.2f * sin(a * 1.5f + i))
            blob.shader = RadialGradient(x, y, rad, palette[i], Color.TRANSPARENT, Shader.TileMode.CLAMP)
            blob.alpha = if (speaking) 210 else 170
            canvas.drawCircle(x, y, rad, blob)
        }
        val kr = r * 0.28f
        core.shader = RadialGradient(c, c, kr, Color.parseColor("#BFFFFFFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(c, c, kr, core)
        canvas.restore()
        canvas.drawCircle(c, c, r, rim)
    }

    private var downX = 0f; private var downY = 0f; private var moved = false
    private var downTime = 0L

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val params = lp ?: return false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; moved = false; downTime = System.currentTimeMillis(); return true }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.rawX - downX) > 12 || abs(e.rawY - downY) > 12) moved = true
                if (moved) {
                    params.x += (e.rawX - downX).toInt()
                    params.y += (e.rawY - downY).toInt()
                    downX = e.rawX; downY = e.rawY
                    wm?.updateViewLayout(this, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    if (System.currentTimeMillis() - downTime > 500) onLongPress?.invoke() else onTap?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
