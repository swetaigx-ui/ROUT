package com.rout.aiassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RoutBlack = Color(0xFF050608)
val RoutGraphite = Color(0xFF0C0F14)
val RoutPanel = Color(0x33151B24)
val RoutCyan = Color(0xFF4DE8FF)
val RoutIce = Color(0xFFA5F3FF)
val RoutViolet = Color(0xFFB18CFF)
val RoutText = Color(0xFFE8F4F8)
val RoutDim = Color(0xFF8FA5B3)
val RoutError = Color(0xFFFF6B7A)

private val scheme = darkColorScheme(
    primary = RoutCyan,
    onPrimary = RoutBlack,
    secondary = RoutViolet,
    background = RoutBlack,
    onBackground = RoutText,
    surface = RoutGraphite,
    onSurface = RoutText,
    surfaceVariant = RoutPanel,
    onSurfaceVariant = RoutDim,
    error = RoutError
)

private val type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 4.sp, color = RoutText),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = RoutText),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = RoutText),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, color = RoutText),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = RoutText),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = RoutDim)
)

@Composable
fun RoutTheme(content: @Composable () -> Unit) {
    // ROUT is a dark-only visual identity.
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

val RoutBlue = Color(0xFF287DFF)
val RoutPurple = Color(0xFF8C4DFF)
val RoutGreen = Color(0xFF36FFB3)
val RoutBrandGradient = Brush.linearGradient(
    listOf(Color.White, Color(0xFF43EDFF), Color(0xFF5488FF))
)
