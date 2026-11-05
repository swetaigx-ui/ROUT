package com.rout.aiassistant.features.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** Rounded black pill that expands with status text and an RGB glow edge. */
class IslandView(context: Context) : View(context) {
    private var text = ""
    private var state = "idle"
    private var anim = 0f
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6101018") }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setStatus(t: String, s: String) {
        text = t; state = s
        requestLayout(); invalidate()
    }

    private val ticker = object : Runnable {
        override fun run() {
            anim += 0.04f
            invalidate()
            if (state != "idle") postDelayed(this, 33)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (text.isBlank()) 260 else (textPaint.measureText(text) + 96).toInt().coerceAtMost(900)
        setMeasuredDimension(w, if (text.isBlank()) 72 else 88)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(4f, 4f, width - 4f, height - 4f)
        val hue = ((anim * 90) % 360).toFloat()
        val c1 = Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))
        val c2 = Color.HSVToColor(floatArrayOf((hue + 120) % 360, 0.85f, 1f))
        glowPaint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, c1, c2, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, bgPaint)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, glowPaint)
        if (text.isNotBlank()) {
            val short = if (text.length > 42) text.take(41) + "…" else text
            canvas.drawText(short, width / 2f, height / 2f + 11f, textPaint)
        }
        if (state != "idle") postOnAnimation(ticker)
    }
}
