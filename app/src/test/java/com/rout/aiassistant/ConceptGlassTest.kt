package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import com.rout.aiassistant.ui.components.OrbView
import com.rout.aiassistant.ui.theme.*
import com.rout.aiassistant.voice.AssistantState

/**
 * DESIGN CONCEPT PREVIEW - "minimal liquid glass" proposal for user approval.
 * This is NOT shipped UI. It renders the proposed look with the real Compose
 * engine so the user approves actual pixels before any redesign is built.
 * Glass here = translucent fill + hairline gradient border over a subtle
 * depth-gradient background. No blur effects (no GPU cost, no lag).
 */

private val GlassFill = Color(0x0FFFFFFF)          // 6% white
private val GlassFillStrong = Color(0x14FFFFFF)    // 8% white
private val GlassEdge = Brush.verticalGradient(
    listOf(Color(0x38FFFFFF), Color(0x14FFFFFF))   // bright top, fading bottom
)

@Composable
private fun GlassBox(modifier: Modifier = Modifier, radius: Int = 22, fill: Color = GlassFill, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(radius.dp)).background(fill).border(1.dp, GlassEdge, RoundedCornerShape(radius.dp)),
        content = content
    )
}

@Composable
private fun ConceptBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            // subtle depth: faint cyan glow high-center, graphite falloff. Static, zero GPU cost.
            drawRect(Color(0xFF050608))
            drawCircle(Color(0x0D4DE8FF), radius = size.minDimension * 0.75f, center = Offset(size.width / 2f, size.height * 0.30f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0A0E14)), startY = size.height * 0.55f))
        },
        content = content
    )
}

@Composable
private fun ConceptWordmark() {
    Column {
        Text("ROUT", color = RoutCyan, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        Text("PERSONAL AI · POWERED BY GYAN", color = RoutDim, fontSize = 10.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun StatusPill(text: String, dot: Color) {
    GlassBox(radius = 50, fill = GlassFill) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(text, color = RoutDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ConceptHome(reply: String?) {
    ConceptBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ConceptWordmark()
                Spacer(Modifier.weight(1f))
                StatusPill(if (reply == null) "Offline" else "Ready", if (reply == null) RoutError else RoutCyan)
                Spacer(Modifier.width(10.dp))
                GlassBox(radius = 50) { Icon(Icons.Default.Settings, "Settings", tint = RoutDim, modifier = Modifier.padding(10.dp).size(18.dp)) }
            }
            Spacer(Modifier.height(56.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // soft glow seat behind the orb - sells the glass without blur
                Box(Modifier.size(300.dp).drawBehind { drawCircle(Color(0x124DE8FF)) })
                OrbView(state = AssistantState.IDLE, rms = 0f, reducedMotion = true, size = 220.dp)
            }
            Spacer(Modifier.height(40.dp))
            if (reply == null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bolo ya type karo - kya karoon?", color = RoutText.copy(alpha = 0.85f), fontSize = 17.sp)
                    Spacer(Modifier.height(24.dp))
                    listOf("Weather batao", "YouTube kholo", "Alarm set karo 6 baje").forEach {
                        GlassBox(radius = 50) { Text("\u201C$it\u201D", color = RoutIce, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Row {
                        Spacer(Modifier.weight(1f))
                        GlassBox(radius = 20, fill = Color(0x1A4DE8FF)) {
                            Text("Weather batao", color = RoutText, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassBox(Modifier.fillMaxWidth(), radius = 20) {
                        Column(Modifier.padding(16.dp)) {
                            Text("ROUT", color = RoutCyan, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(reply, color = RoutText, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GlassBox(Modifier.fillMaxWidth(), radius = 28, fill = GlassFillStrong) {
                Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Type to ROUT\u2026 (Hindi/Hinglish/English)", color = RoutDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    GlassBox(radius = 50) { Text("MIC", color = RoutIce, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(RoutCyan), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Send, "Send", tint = RoutBlack, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class ConceptGlassTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun savePng(name: String) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val specW = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
            val specH = android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY)
            decor.measure(specW, specH)
            decor.layout(0, 0, 1080, 2340)
            val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun conceptIdle() {
        composeRule.setContent { RoutTheme { ConceptHome(reply = null) } }
        savePng("concept_glass_idle.png")
    }

    @Test
    fun conceptReply() {
        composeRule.setContent { RoutTheme { ConceptHome(reply = "Aaj Mumbai mein halki baarish hai, 28\u00B0C. Chhata le jao.") } }
        savePng("concept_glass_reply.png")
    }
}
