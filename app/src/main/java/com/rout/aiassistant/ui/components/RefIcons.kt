package com.rout.aiassistant.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Stroke icons traced 1:1 from Gyan's reference Settings page (24x24 viewBox,
 * 2px round-cap strokes). Render with Icon(..., tint = ...) - the tint applies
 * to the strokes through the vector color filter.
 */
private fun refIcon(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach { d ->
        b.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}

object RefIcons {
    val Home = refIcon("home", "M3 11l9-7 9 7v8a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Chat = refIcon("chat", "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.4A8 8 0 1 1 21 12z")
    val Sliders = refIcon("sliders", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")
    val List = refIcon("list", "M9 6h12M9 12h12M9 18h12M4 6h.01M4 12h.01M4 18h.01")
    val X = refIcon("x", "M18 6 6 18M6 6l12 12")
    val User = refIcon("user", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4 21c0-4 3.6-6 8-6s8 2 8 6")
    val Link = refIcon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val Spark = refIcon("spark", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z")
    val Mic = refIcon("mic", "M9 6a3 3 0 0 1 3-3a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3a3 3 0 0 1-3-3z", "M5 11a7 7 0 0 0 14 0M12 18v3")
    val Zap = refIcon("zap", "M13 2L4 14h7l-1 8 9-12h-7z")
    val Shield = refIcon("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z")
    val Repeat = refIcon("repeat", "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3")
    val Cloud = refIcon("cloud", "M7 18a5 5 0 0 1-.6-10 6 6 0 0 1 11.6 1.6A4 4 0 0 1 18 18z")
    val Layers = refIcon("layers", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5")
    val Clock = refIcon("clock", "M12 3.7a8.3 8.3 0 1 1 0 16.6 8.3 8.3 0 1 1 0-16.6Z", "M12 7.2v4.8l3.4 2")
    val Timer = refIcon("timer", "M12 6a7.4 7.4 0 1 1 0 14.8A7.4 7.4 0 1 1 12 6Z", "M12 9.8v3.6l2.4 1.4", "M10 2.6h4", "M12 2.6v3.2")
    val NotePen = refIcon("notepen", "M4.5 19.5l.9-3.7L16.2 5a2.15 2.15 0 0 1 3 3L8.4 18.8l-3.9.7Z")
    val YouTube = refIcon("youtube", "M6.2 6h11.6a3.4 3.4 0 0 1 3.4 3.4v5.2a3.4 3.4 0 0 1-3.4 3.4H6.2a3.4 3.4 0 0 1-3.4-3.4V9.4A3.4 3.4 0 0 1 6.2 6Z", "M10.4 9.7v4.6l4.2-2.3Z")
    val Phone = refIcon("phone", "M5.2 3.5h3.4l1.6 4-2 1.5a12.5 12.5 0 0 0 5.7 5.7l1.5-2 4 1.6v3.4a1.9 1.9 0 0 1-2 2A16.8 16.8 0 0 1 3.2 5.5a1.9 1.9 0 0 1 2-2Z")
    val Info = refIcon("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 11v5M12 8h.01")
}
