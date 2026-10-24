package com.rout.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ROUT liquid glass: translucent fill + hairline gradient border over a
 * static depth background. Deliberately no blur effect (GPU cost on budget
 * phones); translucency over the gradient gives the glass read.
 */
val GlassFill = Color(0x0FFFFFFF)
val GlassFillStrong = Color(0x14FFFFFF)
val GlassEdge = Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x14FFFFFF)))

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fill: Color = GlassFill,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(fill).border(1.dp, GlassEdge, RoundedCornerShape(radius)),
        content = content
    )
}

/** Approved minimal-liquid-glass backdrop: near-black, faint cyan glow up top, graphite falloff. Static. */
@Composable
fun RoutBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Color(0xFF050608))
            drawCircle(Color(0x0D4DE8FF), radius = size.minDimension * 0.75f, center = Offset(size.width / 2f, size.height * 0.30f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0A0E14)), startY = size.height * 0.55f))
        },
        content = content
    )
}
