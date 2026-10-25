package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rout.aiassistant.ui.components.WarbyEmotion
import com.rout.aiassistant.ui.components.WarbyOrb
import com.rout.aiassistant.ui.theme.RoutCyan
import com.rout.aiassistant.ui.theme.RoutDim
import com.rout.aiassistant.ui.theme.RoutIce
import com.rout.aiassistant.voice.AssistantState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** CONCEPT render (not shipping UI): redesigned Settings screen for Gyan's approval. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsConceptRenderTest {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun settingsConcept() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF050608))) {
                // ambient neon glow behind content, like Home
                Box(
                    Modifier
                        .size(700.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 180.dp, y = (-160).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x33B18CFF), Color(0x00050608))
                            )
                        )
                )
                Box(
                    Modifier
                        .size(600.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-200).dp, y = 160.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x2E4DE8FF), Color(0x00050608))
                            )
                        )
                )
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WarbyOrb(state = AssistantState.IDLE, emotion = WarbyEmotion.HAPPY, rms = 0f, reducedMotion = true, modifier = Modifier.size(80.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Settings", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Text("ROUT ko apne hisaab se set karo", color = RoutDim, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.height(30.dp))

                    // Connection status
                    ConceptCard(accent = Color(0xFF4DFFA6)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF4DFFA6)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Connected via ROUT backend", color = RoutIce, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text("Device enrolled - sab ready hai", color = RoutDim, fontSize = 13.sp)
                            }
                            Text("Manage", color = RoutCyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(24.dp))

                    // Persona
                    ConceptCard(accent = Color(0xFFFF5CE1)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ROUT persona", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("Sweetheart", color = Color(0xFFFF5CE1), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Kaise baat karti hai + kaunsi voice", color = RoutDim, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            ConceptChip("Sweetheart", true, Color(0xFFFF5CE1)); ConceptChip("Professional", false, RoutCyan)
                            ConceptChip("Waifu", false, RoutCyan); ConceptChip("Coach", false, RoutCyan)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            ConceptChip("Hacker", false, RoutCyan); ConceptChip("Therapist", false, RoutCyan)
                            ConceptChip("Dost", false, RoutCyan); ConceptChip("Shayar", false, RoutCyan)
                        }
                    }
                    Spacer(Modifier.height(24.dp))

                    // Voice
                    ConceptCard(accent = Color(0xFFB18CFF)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ROUT voice", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("Aoede", color = Color(0xFFB18CFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            ConceptChip("Achernar", false, RoutCyan); ConceptChip("Aoede", true, Color(0xFFB18CFF))
                            ConceptChip("Despina", false, RoutCyan); ConceptChip("Zephyr", false, RoutCyan)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Persona ka apna voice hota hai - yahan se bhi badal sakte ho", color = RoutDim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(24.dp))

                    // Quick controls
                    ConceptCard(accent = RoutCyan) {
                        Text("Quick controls", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        ConceptToggle("Floating orb - kahin bhi baat", true)
                        ConceptToggle("Battery full/low alert", true)
                        ConceptToggle("Flip phone to silent", false)
                        ConceptToggle("Bedtime mode (DND)", false)
                    }
                    Spacer(Modifier.height(24.dp))

                    // Appearance
                    ConceptCard(accent = Color(0xFFB18CFF)) {
                        Text("Appearance", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        ConceptToggle("Cat orb emotions (Warby)", true)
                        ConceptToggle("Reduce motion", false)
                        ConceptRow("Language", "Hinglish")
                    }
                    Spacer(Modifier.height(24.dp))

                    // Permissions & privacy
                    ConceptCard(accent = Color(0xFFFFB84D)) {
                        Text("Permissions and privacy", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        ConceptRow("Mic, contacts, location", "Granted")
                        ConceptRow("Notification access", "Manage")
                        ConceptRow("Export my data", "")
                    }
                    Spacer(Modifier.height(24.dp))

                    // Routines
                    ConceptCard(accent = Color(0xFF4DFFA6)) {
                        Text("Routines", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        ConceptRow("Good morning briefing", "7:00 AM")
                        ConceptRow("Aaj ka plan", "On")
                        ConceptRow("Add routine", "+")
                    }
                    Spacer(Modifier.height(24.dp))

                    // About
                    ConceptCard(accent = RoutIce) {
                        Text("About ROUT", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        ConceptRow("Version", "0.6.2")
                        ConceptRow("Source code (GitHub)", "Open")
                        ConceptRow("Help and commands list", "Open")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("ROUT AI - POWERED BY GYAN", color = RoutDim, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val specW = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
            val specH = android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY)
            decor.measure(specW, specH)
            decor.layout(0, 0, 1080, 2400)
            val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, "settings_concept.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        composeRule.waitForIdle()
    }
}

@androidx.compose.runtime.Composable
private fun ConceptCard(accent: Color, content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x10FFFFFF))))
            .border(1.dp, Color(0x2E4DE8FF), RoundedCornerShape(24.dp))
    ) {
        Box(Modifier.width(5.dp).height(6.dp)) // spacer keeps row non-empty pre-layout
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp), content = content)
    }
}

@androidx.compose.runtime.Composable
private fun ConceptChip(label: String, selected: Boolean, accent: Color) {
    Box(
        Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) accent else Color(0x1AFFFFFF))
            .border(1.dp, if (selected) accent else Color(0x2EFFFFFF), RoundedCornerShape(50.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (selected) Color(0xFF050608) else Color.White, fontSize = 14.sp)
    }
}

@androidx.compose.runtime.Composable
private fun ConceptRow(label: String, action: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (action.isNotBlank()) Text(action, color = RoutCyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@androidx.compose.runtime.Composable
private fun ConceptToggle(label: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(46.dp, 26.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(if (on) Color(0xFF4DE8FF) else Color(0x22FFFFFF))
        ) {
            Box(
                Modifier
                    .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
