package com.rout.aiassistant.features.avatar

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** 3D avatar view: local three.js scene (bundled, offline) - an animated ROUT orb character. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AvatarScreen() {
    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A12)).statusBarsPadding().padding(16.dp)) {
        Text("ROUT 3D", color = Color(0xFFEEECF8), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                loadUrl("file:///android_asset/avatar.html")
            }
        }, modifier = Modifier.fillMaxSize())
    }
}
