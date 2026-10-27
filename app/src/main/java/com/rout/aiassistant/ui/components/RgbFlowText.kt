package com.rout.aiassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * "Powered by Gyan" flowing RGB glow from Gyan's reference page (.rgb / rgbFlow):
 * a wide rainbow gradient scrolls INSIDE the letters (6s linear loop), never a
 * border or light around the text. Honours Reduce motion by freezing the flow.
 */
private val RgbFlowColors = listOf(
    Color(0xFFFF5F7E), Color(0xFFFFB547), Color(0xFFC6F36B),
    Color(0xFF5EF2FF), Color(0xFF7C8BFF), Color(0xFFD77BFF)
)

@Composable
fun RgbFlowText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = 2.2.sp,
    reducedMotion: Boolean = false
) {
    val tile = 480f
    val shift = if (reducedMotion) 0f else {
        val inf = rememberInfiniteTransition(label = "rgbflow")
        val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t")
        t
    }
    // Palette doubled so shifting by exactly one palette width wraps seamlessly.
    val brush = Brush.linearGradient(
        RgbFlowColors + RgbFlowColors,
        start = Offset(shift * tile, 0f),
        end = Offset(shift * tile + tile, 0f)
    )
    Text(
        text,
        modifier = modifier,
        style = TextStyle(brush = brush, fontSize = fontSize, fontWeight = fontWeight, letterSpacing = letterSpacing)
    )
}
