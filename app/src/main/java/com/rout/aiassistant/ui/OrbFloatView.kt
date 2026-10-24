package com.rout.aiassistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class OrbFloatView(context: Context) : View(context) {
    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DE8FF"); style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4DE8FF"); style = Paint.Style.FILL }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC050608"); style = Paint.Style.FILL }
    private val arc = RectF()

    private var wm: WindowManager? = null
    private var lp: WindowManager.LayoutParams? = null
    private var downX = 0f; private var downY = 0f; private var moved = false
    private var downTime = 0L

    fun attachDrag(wm: WindowManager, lp: WindowManager.LayoutParams) { this.wm = wm; this.lp = lp }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(140, 140) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = width / 2f
        canvas.drawCircle(c, c, 62f, bg)
        arc.set(c - 44f, c - 44f, c + 44f, c + 44f)
        canvas.drawArc(arc, -60f, 300f, false, ring)
        canvas.drawCircle(c, c, 16f, core)
    }

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
