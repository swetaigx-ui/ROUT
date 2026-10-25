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
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    // Header: brand + small cat orb + friendly greeting
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WarbyOrb(state = AssistantState.IDLE, emotion = WarbyEmotion.HAPPY, rms = 0f, reducedMotion = true, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("ROUT ko apne hisaab se set karo", color = RoutDim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))

                    // Connection status card
                    ConceptCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4DFFA6)))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Connected via ROUT backend", color = RoutIce, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Device enrolled - ready", color = RoutDim, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    // Persona card with chips
                    ConceptCard {
                        Text("ROUT persona", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Kaise baat karti hai + kaunsi voice", color = RoutDim, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            ConceptChip("Sweetheart", true); ConceptChip("Professional", false)
                            ConceptChip("Waifu", false); ConceptChip("Coach", false)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            ConceptChip("Hacker", false); ConceptChip("Therapist", false)
                            ConceptChip("Dost", false); ConceptChip("Shayar", false)
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    // Voice card
                    ConceptCard {
                        Text("ROUT voice", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            ConceptChip("Achernar", false); ConceptChip("Aoede", true); ConceptChip("Despina", false)
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    // Quick toggles card
                    ConceptCard {
                        Text("Quick controls", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        ConceptToggle("Floating orb", true)
                        ConceptToggle("Battery full/low alert", true)
                        ConceptToggle("Flip phone to silent", false)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("ROUT AI - POWERED BY GYAN", color = RoutDim, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val specW = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
            val specH = android.view.View.MeasureSpec.makeMeasureSpec(2200, android.view.View.MeasureSpec.EXACTLY)
            decor.measure(specW, specH)
            decor.layout(0, 0, 1080, 2200)
            val bmp = Bitmap.createBitmap(1080, 2200, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, "settings_concept.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        composeRule.waitForIdle()
    }

}

@androidx.compose.runtime.Composable
private fun ConceptCard(content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))
                )
            )
            .border(1.dp, Color(0x2E4DE8FF), RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content
    )
}

@androidx.compose.runtime.Composable
private fun ConceptChip(label: String, selected: Boolean) {
    Box(
        Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Color(0xFF4DE8FF) else Color(0x1AFFFFFF))
            .border(1.dp, if (selected) Color(0xFF4DE8FF) else Color(0x2EFFFFFF), RoundedCornerShape(50.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color(0xFF050608) else Color.White, fontSize = 13.sp)
    }
}

@androidx.compose.runtime.Composable
private fun ConceptToggle(label: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
