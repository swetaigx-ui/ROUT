package com.rout.aiassistant.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Frosted warm-glass material for the approved home11 look.
 *
 * The home backdrop is a static painted composition, so the frosted effect is
 * honest and cheap: paint that backdrop once into a small bitmap, blur it in
 * software (3-pass box blur ~ gaussian), then each glass panel samples its own
 * region of the blurred backdrop and layers milk tint, sheen and hairline edge
 * on top. No RenderEffect, no per-frame blur, works from minSdk 26. Panels
 * without a backdrop provider fall back to the plain warm veil.
 */

internal data class FrostedBackdrop(val bmp: ImageBitmap, val originInRoot: Offset)

internal val LocalFrostedBackdrop = compositionLocalOf<FrostedBackdrop?> { null }

internal const val BackdropScale = 0.25f
private const val BlurRadius = 12 // in downscaled pixels (~48px full-res)

internal val WarmVeil = Brush.verticalGradient(
    listOf(Color(0x4DFFDFA8), Color(0x2BFFB872))
)
internal val HeroVeil = Brush.verticalGradient(
    listOf(Color(0x2EFFF0D2), Color(0x14FFDDB0))
)
internal val DarkVeil = Brush.verticalGradient(
    listOf(Color(0x7A1C1108), Color(0x5E120B04))
)
internal val WarmEdge = Brush.linearGradient(
    listOf(Color(0x4DFFF6E6), Color(0x1FFFE9CC)),
    start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

/** 3-pass sliding-window box blur over ARGB pixels (approximates gaussian). */
private fun boxBlur3(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
    var a = src
    var b = IntArray(src.size)
    val d = 2 * radius + 1
    repeat(3) {
        // horizontal pass
        for (y in 0 until h) {
            val row = y * w
            var al = 0; var r = 0; var g = 0; var bl = 0
            for (x in -radius..radius) {
                val p = a[row + x.coerceIn(0, w - 1)]
                al += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF
            }
            for (x in 0 until w) {
                b[row + x] = (al / d shl 24) or (r / d shl 16) or (g / d shl 8) or (bl / d)
                val po = a[row + (x - radius).coerceIn(0, w - 1)]
                val pn = a[row + (x + radius + 1).coerceIn(0, w - 1)]
                al += (pn ushr 24) - (po ushr 24)
                r += ((pn shr 16) and 0xFF) - ((po shr 16) and 0xFF)
                g += ((pn shr 8) and 0xFF) - ((po shr 8) and 0xFF)
                bl += (pn and 0xFF) - (po and 0xFF)
            }
        }
        // vertical pass
        for (x in 0 until w) {
            var al = 0; var r = 0; var g = 0; var bl = 0
            for (y in -radius..radius) {
                val p = b[y.coerceIn(0, h - 1) * w + x]
                al += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF
            }
            for (y in 0 until h) {
                a[y * w + x] = (al / d shl 24) or (r / d shl 16) or (g / d shl 8) or (bl / d)
                val po = b[(y - radius).coerceIn(0, h - 1) * w + x]
                val pn = b[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                al += (pn ushr 24) - (po ushr 24)
                r += ((pn shr 16) and 0xFF) - ((po shr 16) and 0xFF)
                g += ((pn shr 8) and 0xFF) - ((po shr 8) and 0xFF)
                bl += (pn and 0xFF) - (po and 0xFF)
            }
        }
    }
    return a
}

/** Paint the exact home backdrop composition into a small blurred bitmap. */
internal fun buildBlurredHomeBackdrop(wPx: Int, hPx: Int): ImageBitmap {
    val w = (wPx * BackdropScale).toInt().coerceAtLeast(1)
    val h = (hPx * BackdropScale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Keep in sync with the Compose-side painting in RoutHomeBackground.
    c.drawColor(0xFF100C0A.toInt())
    fun pool(cx: Float, cy: Float, r: Float, c0: Long, c1: Long) {
        paint.shader = RadialGradient(cx, cy, r,
            intArrayOf(c0.toInt(), c1.toInt(), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
    }
    pool(w * 0.52f, h * 0.24f, w * 0.66f, 0x80FAF0D6, 0x40FAF0D6)
    pool(w * 0.50f, h * 0.70f, w * 0.75f, 0x88EE9E4A, 0x50EE9E4A)
    pool(w * 0.80f, h * 0.10f, w * 0.44f, 0x3DBAA880, 0x22BAA880)
    // Same bottom dark veil as the Compose-side background, pre-blur.
    paint.shader = LinearGradient(0f, h * 0.70f, 0f, h.toFloat(),
        intArrayOf(0, 0xD90A0807.toInt()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, h * 0.70f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null

    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val blurred = boxBlur3(px, w, h, BlurRadius)
    bmp.setPixels(blurred, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}

/** Warm frosted-glass panel: blurred-backdrop sample + milk tint + sheen + hairline edge. */
@Composable
internal fun WarmGlassBox(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    hero: Boolean = false,
    dark: Boolean = false,
    tex: Int? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalFrostedBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Approved home11 glass slice, stretched to this panel's live bounds.
    val texBmp = tex?.let { androidx.compose.ui.graphics.ImageBitmap.imageResource(id = it) }
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .drawBehind {
                if (texBmp != null) {
                    drawImage(texBmp,
                        srcOffset = IntOffset.Zero, srcSize = IntSize(texBmp.width, texBmp.height),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.High)
                    return@drawBehind
                }
                val r = radius.toPx()
                val bmp = backdrop?.bmp
                if (backdrop != null && bmp != null) {
                    clipPath(Path().apply {
                        addRoundRect(RoundRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size), r, r))
                    }) {
                        val s = BackdropScale
                        val rel = origin - backdrop.originInRoot
                        val sox = (rel.x * s).toInt().coerceIn(0, bmp.width - 1)
                        val soy = (rel.y * s).toInt().coerceIn(0, bmp.height - 1)
                        val ssw = (size.width * s).toInt().coerceIn(1, bmp.width - sox)
                        val ssh = (size.height * s).toInt().coerceIn(1, bmp.height - soy)
                        drawImage(
                            bmp,
                            srcOffset = IntOffset(sox, soy),
                            srcSize = IntSize(ssw, ssh),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.High
                        )
                    }
                }
                // milk tint
                drawRect(if (dark) DarkVeil else if (hero) HeroVeil else WarmVeil)
                if (hero) {
                    // baked curved milk streaks, like the mockup's refracted sheets
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color(0x2EFFFFFF), Color.Transparent),
                            start = Offset(size.width * 0.55f, 0f),
                            end = Offset(size.width * 0.95f, size.height * 0.9f)
                        )
                    )
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color(0x14FFFFFF), Color.Transparent),
                            start = Offset(size.width * 0.10f, 0f),
                            end = Offset(size.width * 0.45f, size.height)
                        )
                    )
                }
                // soft top-left bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x33FFFFFF), Color.Transparent),
                        center = Offset(size.width * 0.16f, 0f),
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.16f, 0f)
                )
                // top catch-light
                drawLine(
                    Brush.horizontalGradient(listOf(Color(0x3DFFFFFF), Color(0x14FFFFFF))),
                    Offset(size.width * 0.08f, 1f), Offset(size.width * 0.92f, 1f),
                    strokeWidth = 2f
                )
            }
            .then(if (texBmp != null) Modifier else Modifier.border(1.dp, WarmEdge, RoundedCornerShape(radius))),
        content = content
    )
}
