package com.rout.aiassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

/**
 * Floating glass dock from Gyan's reference page: three tabs (Home / Chat /
 * Settings), icon above label, the active tab in a soft vertical-gradient pill.
 *
 * Touch feel: a translucent "liquid" glow follows the finger across the dock
 * while pressed and springs back toward the active tab on release; the touched
 * tab compresses slightly under the finger. Honest scope: a translucent radial
 * glow layered inside the glass - When the screen provides a frosted backdrop (Home), the dock
 * samples the real blurred backdrop; elsewhere it falls back to the warm veil.
 */
@Composable
fun RoutDock(current: String, nav: NavController, modifier: Modifier = Modifier) {
    val tabs = listOf(
        Triple("home", RefIcons.Home, "Home"),
        Triple("chat", RefIcons.Chat, "Chat"),
        Triple("settings", Icons.Default.Settings, "Settings")
    )
    // Finger position across the dock width as a 0..1 fraction; -1 = no touch.
    val glow = remember { Animatable(-1f) }
    val glowAlpha = remember { Animatable(0f) }
    var pressedTab by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()
    val activeIndex = tabs.indexOfFirst { it.first == current }.coerceAtLeast(0)

    WarmGlassBox(modifier.fillMaxWidth(0.82f).widthIn(max = 340.dp), radius = 30.dp, dark = true, tex = com.rout.aiassistant.R.drawable.glass_dock) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp)
                .drawBehind {
                    if (glowAlpha.value > 0.01f && glow.value >= 0f) {
                        val c = Offset(glow.value * size.width, size.height / 2f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0x55FFB04A), Color(0x22FFB04A), Color.Transparent),
                                center = c, radius = size.height * 1.15f
                            ),
                            center = c, radius = size.height * 1.15f, alpha = glowAlpha.value
                        )
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false)
                        val startFrac = (d.position.x / size.width).coerceIn(0f, 1f)
                        scope.launch { glow.snapTo(startFrac) }
                        scope.launch { glowAlpha.animateTo(1f, spring(stiffness = 600f)) }
                        pressedTab = (startFrac * 3).toInt().coerceIn(0, 2)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val change = ev.changes.firstOrNull() ?: break
                            if (change.changedToUp()) {
                                // Liquid settle: spring back to the active tab and fade.
                                scope.launch { glow.animateTo((activeIndex + 0.5f) / 3f, spring(dampingRatio = 0.55f, stiffness = 220f)) }
                                scope.launch { glowAlpha.animateTo(0f, spring(stiffness = 140f)) }
                                pressedTab = -1
                                break
                            }
                            if (change.pressed) {
                                val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                                scope.launch { glow.snapTo(frac) }
                                pressedTab = (frac * 3).toInt().coerceIn(0, 2)
                                if (change.positionChange() != Offset.Zero) change.consume()
                            }
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { i, (route, icon, label) ->
                val on = route == current
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .background(
                            if (on) Brush.verticalGradient(listOf(Color(0x2EFFB04A), Color(0x0FFFB04A)))
                            else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable {
                            if (!on) nav.navigate(route) {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        .padding(vertical = if (pressedTab == i) 9.dp else 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            icon, label,
                            tint = if (on) Color(0xFFFFB04A) else Color(0xFFC9BBA8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            color = if (on) Color(0xFFFDF8F1) else Color(0xFFC9BBA8),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
