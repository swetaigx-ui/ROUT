package com.rout.aiassistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rout.aiassistant.ui.theme.RoutCyan
import com.rout.aiassistant.ui.theme.RoutError
import com.rout.aiassistant.ui.theme.RoutIce
import com.rout.aiassistant.ui.theme.RoutViolet
import com.rout.aiassistant.voice.AssistantState

/**
 * The ROUT orb (design: user's reference page - glowing "R" core with two
 * counter-rotating gradient arc rings). Every state is driven by real
 * assistant state; ring speed and glow reflect what ROUT is actually doing.
 * Canvas-only drawing keeps it at full frame rate on budget phones.
 */
@Composable
fun OrbView(
    state: AssistantState,
    rms: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val slow = if (reducedMotion) 3 else 1
    val dur1 = when (state) {
        AssistantState.LISTENING -> 1500
        AssistantState.PROCESSING -> 2600
        AssistantState.SPEAKING -> 2200
        AssistantState.EXECUTING -> 2000
        AssistantState.ERROR -> 12000
        else -> 7000
    } * slow
    val dur2 = when (state) {
        AssistantState.LISTENING -> 2800
        AssistantState.PROCESSING -> 4200
        AssistantState.SPEAKING -> 3600
        AssistantState.EXECUTING -> 3400
        AssistantState.ERROR -> 16000
        else -> 10000
    } * slow

    val infinite = rememberInfiniteTransition(label = "orb")
    val sweep1 by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(dur1, easing = LinearEasing)),
        label = "sweep1"
    )
    val sweep2 by infinite.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(dur2, easing = LinearEasing)),
        label = "sweep2"
    )

    val (ringColor, glowColor) = when (state) {
        AssistantState.IDLE -> RoutCyan to RoutCyan.copy(alpha = 0.16f)
        AssistantState.LISTENING -> RoutCyan to RoutCyan.copy(alpha = 0.38f)
        AssistantState.PROCESSING -> RoutIce to RoutIce.copy(alpha = 0.26f)
        AssistantState.SPEAKING -> RoutViolet to RoutViolet.copy(alpha = 0.34f)
        AssistantState.EXECUTING -> RoutCyan to RoutCyan.copy(alpha = 0.38f)
        AssistantState.WAITING_CONFIRMATION -> RoutViolet to RoutViolet.copy(alpha = 0.22f)
        AssistantState.ERROR -> RoutError to RoutError.copy(alpha = 0.28f)
    }

    val level = ((rms + 2f) / 12f).coerceIn(0f, 1f)
    val listenBoost = if (state == AssistantState.LISTENING) 1.04f + level * 0.05f else 1f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = this.size.minDimension / 2f
            val orbR = base * 0.76f * listenBoost

            // ambient glow
            drawCircle(
                brush = Brush.radialGradient(listOf(glowColor, Color.Transparent), c, base),
                radius = base
            )
            // orb body: deep radial gradient, light from upper-left
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF284F80), Color(0xFF101D42), Color(0xFF030814)),
                    center = Offset(c.x - orbR * 0.28f, c.y - orbR * 0.30f),
                    radius = orbR * 1.45f
                ),
                radius = orbR
            )
            // inner glow wash
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ringColor.copy(alpha = 0.22f), Color.Transparent),
                    center = c, radius = orbR
                ),
                radius = orbR
            )
            // body rim
            drawCircle(color = ringColor.copy(alpha = 0.85f), radius = orbR, style = Stroke(width = 2.5f))
            // listening halo driven by live mic level
            if (state == AssistantState.LISTENING && level > 0.02f) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.35f * level),
                    radius = orbR * (1.10f + level * 0.22f),
                    style = Stroke(width = 6f)
                )
            }
            // ring 1 (outer-far, forward)
            val r1 = base * 0.895f
            drawArc(ringColor, sweep1, 95f, false, Offset(c.x - r1, c.y - r1),
                androidx.compose.ui.geometry.Size(r1 * 2, r1 * 2), style = Stroke(5f, cap = StrokeCap.Round))
            drawArc(RoutViolet.copy(alpha = 0.85f), sweep1 + 180f, 70f, false, Offset(c.x - r1, c.y - r1),
                androidx.compose.ui.geometry.Size(r1 * 2, r1 * 2), style = Stroke(5f, cap = StrokeCap.Round))
            // ring 2 (outermost, reverse)
            val r2 = base * 0.975f
            drawArc(RoutViolet.copy(alpha = 0.7f), sweep2, 65f, false, Offset(c.x - r2, c.y - r2),
                androidx.compose.ui.geometry.Size(r2 * 2, r2 * 2), style = Stroke(4f, cap = StrokeCap.Round))
            drawArc(ringColor.copy(alpha = 0.6f), sweep2 + 180f, 45f, false, Offset(c.x - r2, c.y - r2),
                androidx.compose.ui.geometry.Size(r2 * 2, r2 * 2), style = Stroke(4f, cap = StrokeCap.Round))
        }
        Text(
            "R",
            style = TextStyle(
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFB7FAFF),
                shadow = Shadow(color = ringColor, blurRadius = 34f)
            )
        )
    }
}
