package com.rout.aiassistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** The "X" circle shown at the bottom while the LIVE orb is dragged. Drop the orb on it to close LIVE and stop the mic. */
@SuppressLint("ViewConstructor")
class DismissTargetView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
    var active = false
        set(v) { if (field != v) { field = v; invalidate() } }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(SIZE, SIZE) }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        fill.color = if (active) Color.parseColor("#E6FF3B5C") else Color.parseColor("#B3101218")
        canvas.drawCircle(c, c, if (active) c else c * 0.82f, fill)
        val d = if (active) 26f else 20f
        canvas.drawLine(c - d, c - d, c + d, c + d, stroke)
        canvas.drawLine(c + d, c - d, c - d, c + d, stroke)
    }

    companion object {
        const val SIZE = 170
        /** True when the finger (raw screen coords) is within the X's catch radius. Bottom-centre, [bottomInset] px above the screen bottom. */
        fun isOver(x: Float, y: Float, screenW: Int, screenH: Int, bottomInset: Int = 200): Boolean {
            if (x < 0f || y < 0f) return false
            val cx = screenW / 2f; val cy = screenH - bottomInset.toFloat()
            val dx = x - cx; val dy = y - cy
            return dx * dx + dy * dy <= (SIZE * 0.9f) * (SIZE * 0.9f)
        }
    }
}
