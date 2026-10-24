package com.rout.aiassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassBox(radius = 50.dp) {
                    Icon(
                        Icons.Default.ArrowBack, "Back", tint = RoutCyan,
                        modifier = Modifier.size(40.dp)
                            .clickable { nav.popBackStack() }
                            .padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, letterSpacing = 1.sp),
                    color = RoutText
                )
            }
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RoutCyan, unfocusedBorderColor = RoutDim,
    focusedTextColor = RoutText, unfocusedTextColor = RoutText
)
