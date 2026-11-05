package com.rout.aiassistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.voice.AssistantState
import kotlinx.coroutines.launch

/**
 * Home: MAX-style design (Gyan's 2026-11-05 reference: "pura same-to-same"),
 * with ROUT's own identity. Dark glass, glowing blue orb with eyes,
 * "Hi <name>, I'm ROUT", stats strip, quick chips, bottom Ask bar + pink mic FAB,
 * left drawer with all sections, "Powered by Gyan" credit.
 */
private val MaxBg = Color(0xFF07070F)
private val MaxCard = Color(0xC2141424)
private val MaxCardLine = Color(0x1AFFFFFF)
private val MaxInk = Color(0xFFEEEEF6)
private val MaxDim = Color(0xFF8F8CA8)
private val MaxCyan = Color(0xFF5FC8FF)
private val MaxPink = Color(0xFFFF3E8A)

@Composable
private fun MaxOrb(state: AssistantState, rms: Float, sizeDp: Float, onTap: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "orb")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val glowPulse = when (state) {
        AssistantState.LISTENING -> 1f + (rms.coerceIn(0f, 10f) / 10f) * 0.25f
        AssistantState.SPEAKING -> 1.12f + breathe * 0.06f
        AssistantState.PROCESSING -> 1.05f + breathe * 0.04f
        else -> 1f + breathe * 0.03f
    }
    val blink by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart), label = "blink")
    val eyeH = if (blink > 0.94f) 0.15f else 1f
    Canvas(Modifier.size(sizeDp.dp).clickable(onClick = onTap)) {
        val c = center; val R = size.minDimension / 2f
        // outer glow
        drawCircle(
            Brush.radialGradient(listOf(Color(0x662F7BFF), Color(0x22124BB8), Color.Transparent), c, R * glowPulse),
            R * glowPulse, c)
        // core sphere
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF3D5A80), Color(0xFF16233F), Color(0xFF0A1020)),
                Offset(c.x - R * 0.25f, c.y - R * 0.3f), R * 1.15f),
            R * 0.62f, c)
        // eyes
        val eyeR = R * 0.075f
        val eyeY = c.y - R * 0.02f
        for (dx in listOf(-R * 0.22f, R * 0.22f)) {
            drawCircle(Color(0xFF9FE8FF), eyeR, Offset(c.x + dx, eyeY))
            if (eyeH < 1f) drawRect(Color(0xFF16233F),
                Offset(c.x + dx - eyeR, eyeY - eyeR), androidx.compose.ui.geometry.Size(eyeR * 2, eyeR * 2 * (1 - eyeH)))
        }
    }
}

@Composable
private fun MaxStat(label: String, value: String) {
    Column(
        Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).background(MaxCard)
            .border(1.dp, MaxCardLine, RoundedCornerShape(16.dp)).padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Text(label.uppercase(), color = MaxDim, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = MaxInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MaxChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaxCard)
            .border(1.dp, MaxCardLine, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = MaxInk, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = MaxInk, fontSize = 13.sp)
    }
}

@Composable
fun MaxHomeScreen(vm: MainViewModel, nav: NavController) {
    val state by vm.assistantState.collectAsState()
    val rms by vm.rms.collectAsState()
    val userName by vm.userName.collectAsState()
    val persona by vm.personaMode.collectAsState()
    val online by vm.online.collectAsState()
    val weatherCity by vm.weatherCity.collectAsState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var pendingMicAction by remember { mutableStateOf<() -> Unit>({ vm.onMicTap() }) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) pendingMicAction() else vm.setBanner("Microphone denied. Text input still works.")
    }
    val micTap = { if (!micGranted) { pendingMicAction = { vm.onMicTap() }; micLauncher.launch(Manifest.permission.RECORD_AUDIO) } else vm.onMicTap() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xF20B0B16), modifier = Modifier.width(280.dp)) {
                Column(Modifier.fillMaxHeight().padding(18.dp)) {
                    Text("ROUT", color = MaxInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    val items = listOf(
                        Triple("New chat", Icons.Default.Add, "chat"),
                        Triple("Chat", Icons.Default.Email, "chat"),
                        Triple("Tasks", Icons.Default.CheckCircle, "tasks"),
                        Triple("Notes", Icons.Default.Edit, "notes"),
                        Triple("History", Icons.Default.Refresh, "history"),
                        Triple("Memory", Icons.Default.Star, "memory"),
                        Triple("Translator", Icons.Default.PlayArrow, "translator"),
                        Triple("Scanner", Icons.Default.Search, "scanner"),
                        Triple("Code Studio", Icons.Default.Build, "codestudio"),
                        Triple("Mirror", Icons.Default.Face, "mirror"),
                        Triple("Video Editor", Icons.Default.PlayArrow, "videoeditor"),
                        Triple("Hand Magic", Icons.Default.ThumbUp, "handmagic"),
                        Triple("3D Avatar", Icons.Default.AccountCircle, "avatar"),
                        Triple("Settings", Icons.Default.Settings, "settings")
                    )
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        items.forEach { (label, icon, route) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { scope.launch { drawerState.close() }; nav.navigate(route) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, label, tint = MaxInk, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = MaxInk, fontSize = 15.sp)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(
                            Brush.sweepGradient(listOf(Color(0xFF5FC8FF), Color(0xFF8B5CF6), Color(0xFF5FC8FF)))),
                            contentAlignment = Alignment.Center
                        ) { Text((userName.ifBlank { "G" }).first().uppercase(), color = Color(0xFF0A1020), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text(userName.ifBlank { "Gyan" }, color = MaxInk, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Powered by Gyan", color = MaxDim, fontSize = 11.sp)
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(MaxBg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, "Menu", tint = MaxInk, modifier = Modifier.size(26.dp).clickable { scope.launch { drawerState.open() } })
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ROUT", color = MaxInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(com.rout.aiassistant.ai.Personas.byId(persona).label + " · Fast", color = MaxDim, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Settings, "Settings", tint = MaxInk, modifier = Modifier.size(24.dp).clickable { nav.navigate("settings") })
            }
            Spacer(Modifier.height(26.dp))
            // Orb
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MaxOrb(state, rms, 190f, micTap)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                when (state) {
                    AssistantState.LISTENING -> "Sun raha hoon ${userName.ifBlank { "Gyan" }}…"
                    AssistantState.SPEAKING -> "Theek hai ${userName.ifBlank { "Gyan" }}, baad mein baat karte hain!"
                    AssistantState.PROCESSING -> "Soch raha hoon…"
                    else -> "Hi ${userName.ifBlank { "Gyan" }}, I'm ROUT"
                },
                color = MaxInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text("Ask anything, or tap for tools", color = MaxDim, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            // Stats strip
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                MaxStat("Weather", weatherCity.ifBlank { "Set city" })
                MaxStat("Today", if (online) "Online" else "Offline")
                MaxStat("Mode", com.rout.aiassistant.ai.Personas.byId(persona).label.split(" ").first())
            }
            Spacer(Modifier.height(18.dp))
            // Quick chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                MaxChip(Icons.Default.PlayArrow, "Video") { nav.navigate("videoeditor") }
                MaxChip(Icons.Default.Search, "Scanner") { nav.navigate("scanner") }
                MaxChip(Icons.Default.Build, "Code") { nav.navigate("codestudio") }
            }
            Spacer(Modifier.weight(1f))
            // Bottom ask bar + mic FAB
            var text by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(MaxCard)
                        .border(1.dp, MaxCardLine, RoundedCornerShape(26.dp)).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, "plus", tint = MaxDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (text.isBlank()) Text("Ask ROUT", color = MaxDim, fontSize = 15.sp)
                        BasicTextField(text, { text = it }, textStyle = TextStyle(color = MaxInk, fontSize = 15.sp),
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaxPink).clickable {
                        if (text.isNotBlank()) { vm.onUserText(text, false); text = "" } else micTap()
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Call, "mic", tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
            // Powered by Gyan shimmer credit
            val shimmerShift by rememberInfiniteTransition(label = "credit").animateFloat(
                0f, 500f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sh"
            )
            Text(
                "Powered by Gyan",
                fontSize = 11.sp,
                style = TextStyle(brush = Brush.linearGradient(
                    listOf(MaxDim, MaxInk, MaxDim),
                    start = Offset(shimmerShift - 180f, 0f), end = Offset(shimmerShift, 0f)
                )),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
