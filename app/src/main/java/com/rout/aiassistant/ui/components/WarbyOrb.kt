package com.rout.aiassistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rout.aiassistant.voice.AssistantState
import kotlin.math.sin

private val WarbyPink = Color(0xFFFF5CE1)
private val WarbyPurple = Color(0xFFB18CFF)
private val WarbyBlue = Color(0xFF4D7CFF)
private val WarbyCyan = Color(0xFF4DE8FF)
private val WarbyWhite = Color(0xFFFFFFFF)
private val WarbyDeep = Color(0xFF0B0724)

/** Live visual mode: assistant state overrides emotion (his JS: listening/talking win). */
enum class WarbyMode { IDLE, LISTENING, TALKING, THINKING,
    HAPPY, LOVE, SAD, ANGRY, EXCITED, SLEEPY, CURIOUS }

/**
 * LIVE WARBY - DESIGN 05 "PLAYFUL VIBES".
 * Neon-glass cat-face orb: glowing cat-ear head, two big eyes, voice-synced mouth,
 * two tilted orbit rings, hearts/sparkles/Zzz per emotion. Canvas-only = full frame rate.
 */
@Composable
fun WarbyOrb(
    state: AssistantState,
    emotion: WarbyEmotion?,
    rms: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val mode: WarbyMode = when (state) {
        AssistantState.LISTENING -> WarbyMode.LISTENING
        AssistantState.SPEAKING -> WarbyMode.TALKING
        AssistantState.PROCESSING, AssistantState.EXECUTING -> WarbyMode.THINKING
        AssistantState.WAITING_CONFIRMATION -> WarbyMode.CURIOUS
        AssistantState.ERROR -> WarbyMode.SAD
        AssistantState.IDLE -> when (emotion) {
            WarbyEmotion.HAPPY -> WarbyMode.HAPPY
            WarbyEmotion.LOVE -> WarbyMode.LOVE
            WarbyEmotion.SAD -> WarbyMode.SAD
            WarbyEmotion.ANGRY -> WarbyMode.ANGRY
            WarbyEmotion.EXCITED -> WarbyMode.EXCITED
            WarbyEmotion.SLEEPY -> WarbyMode.SLEEPY
            WarbyEmotion.CURIOUS -> WarbyMode.CURIOUS
            WarbyEmotion.THINKING -> WarbyMode.THINKING
            null -> WarbyMode.IDLE
        }
    }

    val slow = if (reducedMotion) 3 else 1
    val infinite = rememberInfiniteTransition(label = "warby")

    // breathing: gentle scale pulse (idle/sleepy slow, excited fast)
    val breathDur = when (mode) {
        WarbyMode.EXCITED -> 900; WarbyMode.ANGRY -> 800
        WarbyMode.SLEEPY -> 4200; WarbyMode.LISTENING -> 1400
        else -> 2600
    } * slow
    val breath by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(breathDur, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath"
    )
    // float: soft vertical drift
    val floatDur = when (mode) { WarbyMode.EXCITED -> 700; WarbyMode.SLEEPY -> 3600; else -> 2200 } * slow
    val floatT by infinite.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(floatDur, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float"
    )
    // orbit ring rotation
    val ringDur = when (mode) {
        WarbyMode.ANGRY -> 1600; WarbyMode.EXCITED -> 2200; WarbyMode.LISTENING -> 3000
        WarbyMode.SLEEPY -> 14000; WarbyMode.THINKING -> 9000; else -> 6000
    } * slow
    val ringSweep by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(ringDur, easing = LinearEasing)), label = "ring"
    )
    // talking mouth open/close (voice-synced rhythm)
    val mouthTick by infinite.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(280, easing = LinearEasing)), label = "mouth"
    )
    // hearts / zzz drift
    val drift by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400 * slow, easing = LinearEasing)), label = "drift"
    )

    val level = ((rms + 2f) / 12f).coerceIn(0f, 1f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = this.size.minDimension / 2f
            val floatAmp = base * 0.03f
            val dy = floatT * floatAmp
            val breathScale = 1f + (breath - 0.5f) * 0.05f
            val headR = base * 0.58f * breathScale

            // mood palette
            val (accent, glow) = when (mode) {
                WarbyMode.ANGRY -> Color(0xFFFF4D6D) to Color(0xFFFF4D6D).copy(alpha = 0.40f)
                WarbyMode.SAD -> WarbyBlue to WarbyBlue.copy(alpha = 0.16f)
                WarbyMode.SLEEPY -> WarbyBlue to WarbyBlue.copy(alpha = 0.14f)
                WarbyMode.LOVE -> WarbyPink to WarbyPink.copy(alpha = 0.34f)
                WarbyMode.HAPPY -> WarbyPink to WarbyPink.copy(alpha = 0.28f)
                WarbyMode.EXCITED -> WarbyPink to WarbyPink.copy(alpha = 0.38f)
                WarbyMode.LISTENING -> WarbyCyan to WarbyCyan.copy(alpha = 0.34f)
                WarbyMode.TALKING -> WarbyPurple to WarbyPurple.copy(alpha = 0.32f)
                WarbyMode.THINKING -> WarbyPurple to WarbyPurple.copy(alpha = 0.20f)
                WarbyMode.CURIOUS -> WarbyCyan to WarbyCyan.copy(alpha = 0.30f)
                WarbyMode.IDLE -> WarbyCyan to WarbyCyan.copy(alpha = 0.22f)
            }

            translate(top = dy) {
                // ambient glow
                drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(c.x, c.y), base), radius = base, center = Offset(c.x, c.y))

                // ---- orbit rings (tilted ellipses, rotating highlight arcs) ----
                val ringExpand = if (mode == WarbyMode.CURIOUS) 1.10f else 1f
                val ringStroke = if (mode == WarbyMode.LISTENING) 6f + level * 5f else 5f
                drawOrbit(c, base * 0.86f * ringExpand, 0.34f, ringSweep, accent, WarbyPurple, ringStroke)
                drawOrbit(c, base * 0.97f * ringExpand, 0.28f, -ringSweep * 0.7f, WarbyCyan, WarbyPink, 4.5f)

                // ---- cat head: ears + glass skull ----
                val earW = headR * 0.62f
                val earH = headR * 0.78f
                val earY = c.y - headR * 0.62f
                // left ear
                val earL = Path().apply {
                    moveTo(c.x - headR * 0.86f, earY + earH * 0.5f)
                    lineTo(c.x - headR * 0.52f, earY - earH)
                    lineTo(c.x - headR * 0.16f, earY + earH * 0.30f)
                    close()
                }
                // right ear
                val earR = Path().apply {
                    moveTo(c.x + headR * 0.86f, earY + earH * 0.5f)
                    lineTo(c.x + headR * 0.52f, earY - earH)
                    lineTo(c.x + headR * 0.16f, earY + earH * 0.30f)
                    close()
                }
                val neon = Brush.linearGradient(listOf(WarbyPink, WarbyPurple, WarbyBlue, WarbyCyan),
                    start = Offset(c.x - headR, c.y - headR), end = Offset(c.x + headR, c.y + headR))
                drawPath(earL, Brush.radialGradient(listOf(WarbyPurple.copy(alpha = 0.5f), WarbyDeep), Offset(c.x - headR * 0.5f, earY), earH * 1.4f))
                drawPath(earR, Brush.radialGradient(listOf(WarbyBlue.copy(alpha = 0.5f), WarbyDeep), Offset(c.x + headR * 0.5f, earY), earH * 1.4f))
                drawPath(earL, neon, style = Stroke(width = 5f))
                drawPath(earR, neon, style = Stroke(width = 5f))
                // inner ear glow
                drawPath(earL, WarbyPink.copy(alpha = 0.25f))
                drawPath(earR, WarbyCyan.copy(alpha = 0.25f))

                // skull: deep glass + neon rim
                drawCircle(Brush.radialGradient(
                    listOf(Color(0xFF2A1B52), Color(0xFF150D33), WarbyDeep),
                    center = Offset(c.x - headR * 0.25f, c.y - headR * 0.3f), radius = headR * 1.5f
                ), radius = headR, center = c)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), c, headR), radius = headR, center = c)
                drawCircle(neon, radius = headR, center = c, style = Stroke(width = 5f))

                // ---- eyes ----
                val eyeY = c.y - headR * 0.08f
                val eyeDX = headR * 0.38f
                val eyeR = headR * (if (mode == WarbyMode.CURIOUS) 0.24f else 0.205f)
                drawEyes(c, eyeY, eyeDX, eyeR, mode, accent)

                // ---- mouth ----
                val mouthY = c.y + headR * 0.36f
                drawMouth(c, mouthY, headR, mode, mouthTick, accent)

                // ---- extras per emotion ----
                when (mode) {
                    WarbyMode.LOVE, WarbyMode.HAPPY -> {
                        drawHeart(c.x - headR * 1.28f, c.y - headR * 0.5f - drift * base * 0.08f, headR * 0.16f, WarbyPink)
                        drawHeart(c.x + headR * 1.28f, c.y + headR * 0.1f - drift * base * 0.06f, headR * 0.13f, WarbyPink)
                        if (mode == WarbyMode.LOVE) {
                            drawHeart(c.x + headR * 0.9f, c.y - headR * 0.95f - drift * base * 0.05f, headR * 0.12f, WarbyPink)
                            // blush
                            drawCircle(WarbyPink.copy(alpha = 0.35f), headR * 0.14f, Offset(c.x - headR * 0.52f, c.y + headR * 0.22f))
                            drawCircle(WarbyPink.copy(alpha = 0.35f), headR * 0.14f, Offset(c.x + headR * 0.52f, c.y + headR * 0.22f))
                        }
                    }
                    WarbyMode.EXCITED -> {
                        drawSparkle(c.x - headR * 1.15f, c.y - headR * 0.85f, headR * 0.14f, WarbyWhite)
                        drawSparkle(c.x + headR * 1.2f, c.y - headR * 0.4f, headR * 0.11f, WarbyPink)
                        drawSparkle(c.x + headR * 0.9f, c.y + headR * 0.85f, headR * 0.09f, WarbyCyan)
                    }
                    WarbyMode.SLEEPY -> {
                        drawZzz(c.x + headR * 1.02f, c.y - headR * 0.95f - drift * base * 0.05f, headR, WarbyCyan)
                    }
                    WarbyMode.THINKING -> {
                        drawCircle(WarbyWhite.copy(alpha = 0.5f), headR * 0.05f, Offset(c.x + headR * 0.85f, c.y - headR * 0.9f))
                        drawCircle(WarbyWhite.copy(alpha = 0.4f), headR * 0.07f, Offset(c.x + headR * 1.05f, c.y - headR * 1.1f))
                    }
                    else -> {}
                }

                // listening halo from live mic level
                if (mode == WarbyMode.LISTENING && level > 0.02f) {
                    drawCircle(accent.copy(alpha = 0.30f * level), headR * (1.18f + level * 0.25f), c, style = Stroke(5f))
                }
            }
        }
    }
}

private fun DrawScope.drawOrbit(c: Offset, r: Float, squash: Float, sweep: Float, colA: Color, colB: Color, stroke: Float) {
    val rect = Rect(c.x - r, c.y - r * squash, c.x + r, c.y + r * squash)
    rotate(degrees = -12f, pivot = c) {
        drawArc(colA.copy(alpha = 0.55f), 0f, 360f, false, rect.topLeft, rect.size, style = Stroke(stroke * 0.7f))
        drawArc(colA, sweep, 80f, false, rect.topLeft, rect.size, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(colB.copy(alpha = 0.8f), sweep + 180f, 55f, false, rect.topLeft, rect.size, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEyes(c: Offset, eyeY: Float, dx: Float, r: Float, mode: WarbyMode, accent: Color) {
    val pupilUp = if (mode == WarbyMode.THINKING) -r * 0.35f else 0f
    when (mode) {
        WarbyMode.SLEEPY, WarbyMode.SAD -> {
            // closed / droopy: gentle downward arcs
            val y = eyeY + if (mode == WarbyMode.SAD) r * 0.4f else 0f
            for (sx in listOf(-1f, 1f)) {
                val p = Path().apply {
                    moveTo(c.x + sx * dx - r, y)
                    quadraticTo(c.x + sx * dx, y + r * (if (mode == WarbyMode.SAD) -0.8f else 0.9f), c.x + sx * dx + r, y)
                }
                drawPath(p, accent.copy(alpha = 0.9f), style = Stroke(4f, cap = StrokeCap.Round))
            }
        }
        WarbyMode.ANGRY -> {
            // sharp slanted slits
            for (sx in listOf(-1f, 1f)) {
                val p = Path().apply {
                    moveTo(c.x + sx * dx - r, eyeY - r * 0.5f)
                    lineTo(c.x + sx * dx + r, eyeY + r * 0.3f)
                }
                drawPath(p, accent, style = Stroke(6f, cap = StrokeCap.Round))
                drawCircle(accent, r * 0.22f, Offset(c.x + sx * dx, eyeY + r * 0.1f))
            }
        }
        else -> {
            val winkRight = mode == WarbyMode.HAPPY || mode == WarbyMode.LOVE
            for (sx in listOf(-1f, 1f)) {
                val center = Offset(c.x + sx * dx, eyeY + pupilUp)
                if (sx > 0 && winkRight) {
                    val p = Path().apply {
                        moveTo(center.x - r, center.y)
                        quadraticTo(center.x, center.y - r * 1.1f, center.x + r, center.y)
                    }
                    drawPath(p, accent.copy(alpha = 0.95f), style = Stroke(4.5f, cap = StrokeCap.Round))
                } else {
                    drawCircle(WarbyWhite.copy(alpha = 0.95f), r, center)
                    drawCircle(WarbyDeep, r * 0.55f, center)
                    drawCircle(accent, r * 0.62f, center, style = Stroke(2.5f))
                    drawCircle(WarbyWhite, r * 0.22f, Offset(center.x - r * 0.25f, center.y - r * 0.28f))
                }
            }
        }
    }
}

private fun DrawScope.drawMouth(c: Offset, y: Float, headR: Float, mode: WarbyMode, tick: Float, accent: Color) {
    val w = headR * 0.34f
    when (mode) {
        WarbyMode.TALKING -> {
            // voice-synced open/close (pink, per mouth detail on his sheet)
            val open = 0.25f + 0.75f * (0.5f + 0.5f * sin(tick))
            drawOval(
                Brush.verticalGradient(listOf(WarbyPink, WarbyPink.copy(alpha = 0.6f))),
                topLeft = Offset(c.x - w, y - w * 0.5f * open),
                size = Size(w * 2, w * open)
            )
        }
        WarbyMode.SAD -> {
            val p = Path().apply {
                moveTo(c.x - w, y + w * 0.3f)
                quadraticTo(c.x, y - w * 0.5f, c.x + w, y + w * 0.3f)
            }
            drawPath(p, accent.copy(alpha = 0.8f), style = Stroke(4f, cap = StrokeCap.Round))
        }
        WarbyMode.ANGRY -> {
            drawLine(accent, Offset(c.x - w, y), Offset(c.x + w, y), strokeWidth = 5f, cap = StrokeCap.Round)
        }
        WarbyMode.SLEEPY -> {
            drawOval(accent.copy(alpha = 0.7f), topLeft = Offset(c.x - w * 0.3f, y - w * 0.25f), size = Size(w * 0.6f, w * 0.5f), style = Stroke(3.5f))
        }
        else -> {
            // smile
            val p = Path().apply {
                moveTo(c.x - w, y - w * 0.2f)
                quadraticTo(c.x, y + w * 0.9f, c.x + w, y - w * 0.2f)
            }
            drawPath(p, WarbyPink, style = Stroke(4.5f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawHeart(x: Float, y: Float, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y + r)
        cubicTo(x - r * 1.6f, y - r * 0.2f, x - r * 0.7f, y - r * 1.4f, x, y - r * 0.5f)
        cubicTo(x + r * 0.7f, y - r * 1.4f, x + r * 1.6f, y - r * 0.2f, x, y + r)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawSparkle(x: Float, y: Float, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y - r); lineTo(x + r * 0.25f, y - r * 0.25f); lineTo(x + r, y)
        lineTo(x + r * 0.25f, y + r * 0.25f); lineTo(x, y + r); lineTo(x - r * 0.25f, y + r * 0.25f)
        lineTo(x - r, y); lineTo(x - r * 0.25f, y - r * 0.25f); close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawZzz(x: Float, y: Float, headR: Float, color: Color) {
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.argb(230, 77, 232, 255)
        textSize = headR * 0.30f
        isFakeBoldText = true
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText("Z", x, y, paint)
    drawContext.canvas.nativeCanvas.drawText("z", x + headR * 0.25f, y - headR * 0.3f,
        android.graphics.Paint(paint).apply { textSize = headR * 0.20f })
}
