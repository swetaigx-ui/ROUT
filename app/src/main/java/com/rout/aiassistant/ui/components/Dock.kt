package com.rout.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/** Floating glass dock from the reference design: HOME / CHAT / SETTINGS. [current] is the highlighted route. */
@Composable
fun RoutDock(current: String, nav: NavController, modifier: Modifier = Modifier) {
    GlassBox(modifier.fillMaxWidth().widthIn(max = 380.dp), radius = 50.dp) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("HOME" to "home", "CHAT" to "chat", "SETTINGS" to "settings").forEach { (label, route) ->
                val on = route == current
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (on) Color(0x1FFFFFFF) else Color.Transparent)
                        .clickable {
                            if (!on) nav.navigate(route) {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (on) Color.White else Color(0xFF8D97A6), fontSize = 12.sp, letterSpacing = 1.9.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}
