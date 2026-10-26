package com.rout.aiassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.rout.aiassistant.ui.components.RoutDock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rout.aiassistant.ui.components.GlassBox
import com.rout.aiassistant.ui.components.RoutBackground
import com.rout.aiassistant.ui.theme.*

/** Shared screen frame: pinned glass header over the ROUT backdrop. */
@Composable
fun ScreenScaffold(title: String, nav: NavController, content: @Composable () -> Unit) {
    RoutBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GlassBox(radius = 50.dp) {
                    Icon(
                        Icons.Default.Close, "Close", tint = Color(0xFFC9D2DE),
                        modifier = Modifier.size(46.dp)
                            .clickable { nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true } }
                            .padding(12.dp)
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            RoutDock(if (title == "Settings") "settings" else "", nav, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp, start = 16.dp, end = 16.dp))
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RoutCyan, unfocusedBorderColor = RoutDim,
    focusedTextColor = RoutText, unfocusedTextColor = RoutText
)
