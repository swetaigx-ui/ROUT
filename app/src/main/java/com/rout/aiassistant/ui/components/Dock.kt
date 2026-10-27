package com.rout.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * Floating glass dock from Gyan's reference page: three tabs (Home / Chat /
 * Settings), icon above label, the active tab in a soft vertical-gradient pill
 * with the accent-coloured icon.
 */
@Composable
fun RoutDock(current: String, nav: NavController, modifier: Modifier = Modifier) {
    GlassBox(modifier.fillMaxWidth().widthIn(max = 380.dp), radius = 28.dp) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple("home", RefIcons.Home, "Home"),
                Triple("chat", RefIcons.Chat, "Chat"),
                Triple("settings", RefIcons.Sliders, "Settings")
            ).forEach { (route, icon, label) ->
                val on = route == current
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .background(
                            if (on) Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF)))
                            else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable {
                            if (!on) nav.navigate(route) {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            icon, label,
                            tint = if (on) Color(0xFF67F0FF) else Color(0xFFA3A0BD),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            color = if (on) Color.White else Color(0xFFA3A0BD),
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
