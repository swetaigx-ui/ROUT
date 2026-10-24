package com.rout.aiassistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rout.aiassistant.ui.theme.RoutCyan
import com.rout.aiassistant.ui.theme.RoutError
import com.rout.aiassistant.ui.theme.RoutIce
import com.rout.aiassistant.ui.theme.RoutViolet
import com.rout.aiassistant.voice.AssistantState
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ROUT orb. Every state is driven by real assistant state - no simulated activity.
 * Waveform bars reflect the live microphone RMS while listening.
 */
@Composable
fun OrbView(
    state: AssistantState,
    rms: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val breathe by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reducedMotion) 4000 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "breathe"
    )
    val sweep by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(6000, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse = if (reducedMotion) 1f else breathe

    val (ringColor, glowColor, scale) = when (state) {
        AssistantState.IDLE -> Triple(RoutCyan.copy(alpha = 0.7f), RoutCyan.copy(alpha = 0.15f), 1f)
        AssistantState.LISTENING -> Triple(RoutCyan, RoutCyan.copy(alpha = 0.3f), pulse)
        AssistantState.PROCESSING -> Triple(RoutIce, RoutIce.copy(alpha = 0.25f), 1f + (pulse - 1f) * 0.5f)
        AssistantState.SPEAKING -> Triple(RoutViolet, RoutViolet.copy(alpha = 0.3f), pulse)
        AssistantState.EXECUTING -> Triple(RoutCyan, RoutCyan.copy(alpha = 0.35f), 1f + (pulse - 1f) * 0.7f)
        AssistantState.WAITING_CONFIRMATION -> Triple(RoutViolet, RoutViolet.copy(alpha = 0.2f), 1f)
        AssistantState.ERROR -> Triple(RoutError, RoutError.copy(alpha = 0.25f), 1f)
    }

    val level = ((rms + 2f) / 12f).coerceIn(0f, 1f)

    Canvas(modifier = modifier.size(size)) {
        val c = center
        val base = size.toPx() / 2f
        val r = base * 0.62f * scale

        // halo
        drawCircle(brush = Brush.radialGradient(listOf(glowColor, Color.Transparent), c, base), radius = base)
        // outer rotating arc
        drawArc(
            color = ringColor.copy(alpha = 0.45f),
            startAngle = sweep, sweepAngle = 250f, useCenter = false,
            topLeft = Offset(c.x - r - 14f, c.y - r - 14f),
            size = androidx.compose.ui.geometry.Size((r + 14f) * 2f, (r + 14f) * 2f),
            style = Stroke(width = 3f)
        )
        // main broken ring
        drawArc(
            color = ringColor, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
            style = Stroke(width = 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        // core
        val coreR = r * 0.34f * (if (state == AssistantState.LISTENING) 1f + level * 0.5f else 1f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), ringColor, Color.Transparent), c, coreR * 1.6f),
            radius = coreR * 1.6f
        )
        // waveform ticks while listening
        if (state == AssistantState.LISTENING) {
            val bars = 24
            for (i in 0 until bars) {
                val ang = (2 * Math.PI * i / bars)
                val wob = 1f + level * 0.5f * (0.5f + 0.5f * sin(ang * 3 + sweep * 0.1f)).toFloat()
                val r1 = r + 26f
                val r2 = r + 26f + 22f * wob
                drawLine(
                    color = ringColor.copy(alpha = 0.6f),
                    start = Offset(c.x + (r1 * cos(ang)).toFloat(), c.y + (r1 * sin(ang)).toFloat()),
                    end = Offset(c.x + (r2 * cos(ang)).toFloat(), c.y + (r2 * sin(ang)).toFloat()),
                    strokeWidth = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}
