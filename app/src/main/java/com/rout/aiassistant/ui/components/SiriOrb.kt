package com.rout.aiassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rout.aiassistant.R
import com.rout.aiassistant.voice.AssistantState

/**
 * The hero orb: Gyan's own supplied ribbon-orb image (approved 2026-10-29 in
 * the home11 mockup). The bitmap pixels are always shown exactly as supplied -
 * never tinted. Reactivity stays honest and subtle: a warm halo that breathes
 * with the REAL assistant state and mic level (ember red on ERROR), a gentle
 * scale swell while listening/speaking, and a thin listening ring driven by
 * the real rms. Image keeps full fidelity on budget phones; only the halo
 * animates.
 */
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
    val inf = rememberInfiniteTransition(label = "orbHalo")
    val phase by inf.animateFloat(
        0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    val level = when (state) {
        AssistantState.LISTENING -> 0.15f + ((rms + 2f) / 12f).coerceIn(0f, 1f) * 0.65f
        AssistantState.SPEAKING -> 0.30f
        AssistantState.PROCESSING, AssistantState.EXECUTING -> 0.08f
        AssistantState.ERROR -> 0.10f
        else -> 0.03f
    }
    val t = if (phaseOverride != null) phaseOverride else if (reducedMotion) 4f else phase
    val breathe = if (reducedMotion && phaseOverride == null) 0f else (kotlin.math.sin(t * 6.2831f / 7f) + 1f) * 0.5f
    val haloColor = if (state == AssistantState.ERROR) Color(0xFFFF5F7E) else Color(0xFFFFC36B)

    Box(modifier.size(size)) {
        // Warm halo bleeding past the orb, stronger while listening/speaking.
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension
            val c = Offset(w / 2f, w / 2f)
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        haloColor.copy(alpha = 0.14f + level * 0.30f + breathe * 0.05f),
                        Color.Transparent
                    ),
                    center = c, radius = w / 2f
                ),
                radius = w / 2f, center = c
            )
            if (state == AssistantState.LISTENING && level > 0.05f) {
                drawCircle(
                    color = haloColor.copy(alpha = 0.30f * level),
                    radius = w * 0.36f * (1.10f + level * 0.20f),
                    center = c, style = Stroke(width = 4f)
                )
            }
        }
        // The supplied ribbon-orb image, exact pixels, gently swelling with state.
        Image(
            painter = painterResource(R.drawable.orb_ribbon),
            contentDescription = "ROUT",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .scale(1f + level * 0.08f + breathe * 0.01f)
        )
    }
}
