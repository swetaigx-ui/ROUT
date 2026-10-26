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
 * ROUT liquid glass: a layered translucent gradient fill with a bright
 * top-edge catch-light and a hairline gradient border, floating over the
 * deep radial backdrop. No GPU blur pass - the glass read comes from real
 * transparency over the scene gradient, so it stays smooth on budget phones.
 */
val GlassFill = Color(0x00000000)
val GlassFillStrong = Color(0x0AFFFFFF)
private val GlassSheen = Brush.linearGradient(
    listOf(Color(0x1CFFFFFF), Color(0x06FFFFFF)),
    start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)
val GlassEdge = Brush.linearGradient(
    listOf(Color(0x1CFFFFFF), Color(0x1CFFFFFF)),
    start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    fill: Color = GlassFill,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(fill)
            .background(GlassSheen)
            .drawBehind {
                // top catch-light: the bright edge that sells the glass
                drawLine(
                    Brush.horizontalGradient(listOf(Color(0x2EFFFFFF), Color(0x2EFFFFFF))),
                    Offset(size.width * 0.08f, 1f), Offset(size.width * 0.92f, 1f),
                    strokeWidth = 2f
                )
            }
            .border(1.dp, GlassEdge, RoundedCornerShape(radius)),
        content = content
    )
}

/** Reference backdrop: deep space radial (navy core at 42% height over near-black), with faint cyan/violet auras. */
@Composable
fun RoutBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Color.Black)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x247C61FF), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.36f),
                    radius = size.maxDimension * 0.58f
                ),
                radius = size.maxDimension * 0.58f,
                center = Offset(size.width / 2f, size.height * 0.36f)
            )
        },
        content = content
    )
}
