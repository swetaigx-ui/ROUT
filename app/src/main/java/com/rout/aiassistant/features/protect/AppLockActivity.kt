package com.rout.aiassistant.features.protect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rout.aiassistant.features.protect.ProtectPrefs.appLockPin

/** PIN gate shown over the app when App Lock is enabled. */
class AppLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var entered by remember { mutableStateOf("") }
                var error by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0A12)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("ROUT Locked", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(if (error) "Galat PIN. Phir se." else "PIN daalo", color = Color(0xFFB0B0C0), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Text("●".repeat(entered.length) + "○".repeat((4 - entered.length).coerceAtLeast(0)),
                            color = Color(0xFF7FD8FF), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(24.dp))
                        val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","⌫"))
                        for (row in rows) {
                            Row {
                                for (k in row) {
                                    if (k.isEmpty()) { Spacer(Modifier.size(72.dp)); continue }
                                    OutlinedButton(
                                        onClick = {
                                            error = false
                                            if (k == "⌫") entered = entered.dropLast(1)
                                            else if (entered.length < 6) {
                                                entered += k
                                                if (entered.length == appLockPin.length && entered == appLockPin) { finish() }
                                                else if (entered.length >= appLockPin.length) { error = true; entered = "" }
                                            }
                                        },
                                        modifier = Modifier.size(72.dp).padding(4.dp)
                                    ) { Text(k, color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun maybeShow(host: ComponentActivity) {
            with(ProtectPrefs) {
                if (host.appLockOn && host.appLockPin.isNotBlank())
                    host.startActivity(Intent(host, AppLockActivity::class.java))
            }
        }
    }
}
