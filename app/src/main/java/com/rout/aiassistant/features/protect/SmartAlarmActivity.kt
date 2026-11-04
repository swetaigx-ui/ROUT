package com.rout.aiassistant.features.protect

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen alarm: rings + vibrates over the lock screen until dismissed or snoozed. */
class SmartAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }
        val label = intent.getStringExtra("label") ?: "Alarm"
        FindPhoneController.start(this) // reuse ring+flash; stopped on dismiss/snooze
        setContent {
            MaterialTheme {
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffectNow { now = System.currentTimeMillis() }
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0A12)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = Color(0xFF7FD8FF), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now)),
                            color = Color.White, style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(48.dp))
                        Row {
                            Button(onClick = {
                                FindPhoneController.stop(this@SmartAlarmActivity)
                                SmartAlarmScheduler.schedule(this@SmartAlarmActivity, System.currentTimeMillis() + 9 * 60_000, label)
                                finish()
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A3A))) { Text("Snooze 9 min") }
                            Spacer(Modifier.width(24.dp))
                            Button(onClick = { FindPhoneController.stop(this@SmartAlarmActivity); finish() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3FA7D6))) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }
    @Composable
    private fun LaunchedEffectNow(block: () -> Unit) { LaunchedEffect(Unit) { block() } }
    override fun onDestroy() { FindPhoneController.stop(this); super.onDestroy() }
}
