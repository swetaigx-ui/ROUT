package com.rout.aiassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.rout.aiassistant.ui.theme.*

@Composable
fun ScreenScaffold(title: String, nav: NavController, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(RoutBlack)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = RoutCyan) }
            Text(title, style = MaterialTheme.typography.titleLarge, color = RoutText)
        }
        content()
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RoutCyan, unfocusedBorderColor = RoutDim,
    focusedTextColor = RoutText, unfocusedTextColor = RoutText
)
