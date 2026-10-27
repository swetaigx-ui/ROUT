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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rout.aiassistant.voice.AssistantState
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Siri-ditto orb Gyan asked for (2026-10-27 voice note): a dark disc with
 * Siri-style colours flowing INSIDE it - a slowly rotating rainbow swirl,
 * drifting dark clouds that carve depth, and a few bright hot spots blended
 * additively. Speed, swell and halo follow the REAL assistant state and the
 * REAL mic level; ERROR shifts the flow to ember reds. Canvas-only drawing
 * keeps it at full frame rate on budget phones.
 */
private val SiriPalette = listOf(
    Color(0xFF5EF2FF), // cyan
    Color(0xFF3B82F6), // blue
    Color(0xFF8B5CF6), // violet
    Color(0xFFD946EF), // magenta
    Color(0xFFFF5F7E), // pink
    Color(0xFF34D399), // teal
    Color(0xFFFFB547)  // amber
)
private val SiriErrorPalette = listOf(
    Color(0xFFFF9DB5), Color(0xFFFF5F7E), Color(0xFFB91C1C), Color(0xFFFFB547)
)

@Composable
fun SiriOrb(
    state: AssistantState,
    rms: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    // Test/preview hook: pin the animation time instead of running the clock.
    phaseOverride: Float? = null
) {
    val inf = rememberInfiniteTransition(label = "siri")
    val phase by inf.animateFloat(
        0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    val speed = when (state) {
        AssistantState.LISTENING -> 1.5f
        AssistantState.PROCESSING, AssistantState.EXECUTING -> 2.8f
        AssistantState.SPEAKING -> 1.9f
        AssistantState.ERROR -> 0.4f
        else -> 0.7f
    }
    val level = when (state) {
        AssistantState.LISTENING -> 0.15f + ((rms + 2f) / 12f).coerceIn(0f, 1f) * 0.65f
        AssistantState.SPEAKING -> 0.30f
        AssistantState.PROCESSING, AssistantState.EXECUTING -> 0.08f
        else -> 0.03f
    }
    val t = if (phaseOverride != null) phaseOverride * speed else if (reducedMotion) 4f else phase * speed
    val palette = if (state == AssistantState.ERROR) SiriErrorPalette else SiriPalette

    Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val c = Offset(w / 2f, w / 2f)
        val r = w * 0.30f * (1f + level * 0.10f)

        // Halo: the glow that bleeds just past the disc, stronger while listening/speaking.
        drawCircle(
            Brush.radialGradient(
                listOf(palette[2].copy(alpha = 0.16f + level * 0.34f), Color.Transparent),
                center = c, radius = w / 2f
            ),
            radius = w / 2f, center = c
        )

        val disc = Path().apply { addOval(Rect(c, r)) }
        clipPath(disc) {
            drawRect(Color(0xFF04050A))

            // 1) The signature Siri swirl: a rainbow sweep slowly rotating inside the disc.
            rotate(degrees = t * 45f, pivot = c) {
                drawRect(
                    Brush.sweepGradient(
                        0.00f to palette[0 % palette.size].copy(alpha = 0.50f),
                        0.17f to palette[2 % palette.size].copy(alpha = 0.50f),
                        0.36f to palette[3 % palette.size].copy(alpha = 0.50f),
                        0.53f to palette[4 % palette.size].copy(alpha = 0.46f),
                        0.70f to palette[1 % palette.size].copy(alpha = 0.46f),
                        0.86f to palette[5 % palette.size].copy(alpha = 0.42f),
                        1.00f to palette[0 % palette.size].copy(alpha = 0.50f),
                        center = c
                    )
                )
            }

            // 2) Drifting dark clouds carve the cloudy depth (they swallow the swirl where they pass).
            for (i in 0 until 3) {
                val a = t * (0.5f + i * 0.22f) + i * 2.1f
                val x = c.x + cos(a) * r * 0.55f
                val y = c.y + sin(a * 0.85f + i) * r * 0.55f
                val rad = r * (0.75f + 0.25f * sin(a * 1.3f))
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xE604050A), Color(0x0004050A)),
                        center = Offset(x, y), radius = rad
                    ),
                    radius = rad, center = Offset(x, y)
                )
            }

            // 3) Bright hot spots, additively blended, riding the swirl.
            palette.take(5).forEachIndexed { i, p ->
                val a = t * (0.8f + i * 0.30f) + i * 1.35f
                val x = c.x + cos(a) * r * (0.42f + 0.18f * sin(a * 0.6f + i)) * (1f + level * 0.4f)
                val y = c.y + sin(a * 1.15f + i) * r * (0.42f + 0.18f * cos(a * 0.8f)) * (1f + level * 0.4f)
                val rad = r * (0.62f + 0.18f * sin(a * 1.5f + i) + level * 0.55f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(p.copy(alpha = 0.55f), p.copy(alpha = 0f)),
                        center = Offset(x, y), radius = rad
                    ),
                    radius = rad, center = Offset(x, y), blendMode = BlendMode.Plus
                )
            }

            // 4) Soft white core: the "alive" centre of the Siri orb.
            val kr = r * (0.26f + level * 0.22f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.34f + level * 0.3f), Color.Transparent),
                    center = c, radius = kr
                ),
                radius = kr, center = c
            )

            // 5) Glass sheen from the upper-left, so the disc reads as a sphere.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(c.x - r * 0.35f, c.y - r * 0.38f), radius = r * 0.9f
                ),
                radius = r * 0.9f, center = Offset(c.x - r * 0.35f, c.y - r * 0.38f)
            )
        }

        // Rim + listening halo from the real mic level.
        drawCircle(Color.White.copy(alpha = 0.13f), radius = r, center = c, style = Stroke(width = 1.6f))
        if (state == AssistantState.LISTENING && level > 0.05f) {
            drawCircle(
                color = palette[0].copy(alpha = 0.30f * level),
                radius = r * (1.10f + level * 0.20f),
                center = c, style = Stroke(width = 5f)
            )
        }
    }
}
