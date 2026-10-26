package com.rout.aiassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rout.aiassistant.voice.AssistantState
import kotlin.math.cos
import kotlin.math.sin

/**
 * The glowing round orb from Gyan's reference design (Ruby Assistant v2): a dark disc filled with five
 * slowly orbiting colour blobs blended additively, a soft white core, a faint rim and a violet halo.
 * Speed and swell follow the real assistant state and the real mic level (rms 0..1).
 */
private val Palette = listOf(
    Color(94, 234, 212), Color(59, 130, 246), Color(168, 85, 247), Color(236, 72, 153), Color(251, 191, 36)
)

@Composable
fun RubyOrb(state: AssistantState, rms: Float, reducedMotion: Boolean, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val inf = rememberInfiniteTransition(label = "orb")
    val phase by inf.animateFloat(
        0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    val speed = when (state) {
        AssistantState.LISTENING -> 1.2f
        AssistantState.PROCESSING, AssistantState.EXECUTING -> 2.6f
        AssistantState.SPEAKING -> 1.5f
        else -> 0.5f
    }
    val level = when (state) {
        AssistantState.LISTENING -> (0.1f + ((rms + 2f) / 12f).coerceIn(0f, 1f) * 0.6f)
        AssistantState.SPEAKING -> 0.2f
        AssistantState.PROCESSING, AssistantState.EXECUTING -> 0.05f
        else -> 0.02f
    }
    val t = if (reducedMotion) 3f else phase * speed
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val c = Offset(w / 2f, w / 2f)
        val r = w * 0.3f
        // halo
        drawCircle(
            Brush.radialGradient(
                listOf(Palette[2].copy(alpha = 0.16f + level * 0.3f), Color.Transparent),
                center = c, radius = w / 2f
            ), radius = w / 2f, center = c
        )
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(disc) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0D1428), Color.Black), center = c, radius = r), radius = r, center = c)
            Palette.forEachIndexed { i, p ->
                val a = t * (1f + i * 0.25f) + i * 1.3f
                val x = c.x + cos(a) * r * (0.3f + 0.2f * sin(a * 0.6f + i)) * (1f + level * 0.5f)
                val y = c.y + sin(a * 1.2f + i) * r * (0.3f + 0.2f * cos(a * 0.8f)) * (1f + level * 0.5f)
                val rad = r * (0.85f + 0.2f * sin(a * 1.5f + i) + level * 0.7f)
                drawCircle(
                    Brush.radialGradient(listOf(p.copy(alpha = 0.85f), p.copy(alpha = 0f)), center = Offset(x, y), radius = rad),
                    radius = rad, center = Offset(x, y), blendMode = BlendMode.Plus
                )
            }
            val kr = r * (0.3f + level * 0.25f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), center = c, radius = kr), radius = kr, center = c)
        }
        drawCircle(Color.White.copy(alpha = 0.14f), radius = r, center = c, style = Stroke(width = 2.dp.toPx() * 0.7f))
    }
}
