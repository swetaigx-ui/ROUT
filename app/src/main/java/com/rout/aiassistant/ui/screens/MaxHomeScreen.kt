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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * ROUT home - Gemini-style layout in Gyan's liquid-glass language:
 * hamburger + "ROUT v" persona picker + new-chat + profile avatar; center RGB
 * sparkle + clean greeting; suggestion rows; Chat/Task pill + glass input bar
 * with plus / mic / waveform. Translucent glass cards, moving RGB glow,
 * everything below the camera cutout, "Powered by Gyan" shimmer credit.
 */
private val MaxBg = Color(0xFF07070F)
private val MaxGlass = Color(0x14FFFFFF)
private val MaxGlassLine = Color(0x2EFFFFFF)
private val MaxInk = Color(0xFFEEEEF6)
private val MaxDim = Color(0xFF8F8CA8)
private val MaxCyan = Color(0xFF5FC8FF)
private val MaxPink = Color(0xFFFF3E8A)

private fun Modifier.glass(shape: RoundedCornerShape) = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0x17FFFFFF), Color(0x09FFFFFF))))
    .border(1.dp, MaxGlassLine, shape)

@Composable
private fun rgbBrush(): Brush {
    val t = rememberInfiniteTransition(label = "rgb")
        .animateFloat(0f, 600f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "x")
    return Brush.linearGradient(
        listOf(Color(0xFF8B5CF6), MaxCyan, Color(0xFF34D399), MaxPink, Color(0xFF8B5CF6)),
        start = Offset(t.value - 400f, 0f), end = Offset(t.value, 120f)
    )
}

@Composable
private fun Sparkle(sizeDp: Float) {
    val t = rememberInfiniteTransition(label = "sp").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
    Canvas(Modifier.size(sizeDp.dp)) {
        val c = center; val r = size.minDimension / 2f
        val brush = Brush.sweepGradient(
            listOf(Color(0xFFFF4D6D), Color(0xFF5FC8FF), Color(0xFF34D399), Color(0xFF8B5CF6), Color(0xFFFF4D6D)),
            Offset(c.x + r * 0.5f * kotlin.math.cos(Math.toRadians(t.value.toDouble())).toFloat(),
                   c.y + r * 0.5f * kotlin.math.sin(Math.toRadians(t.value.toDouble())).toFloat()))
        val path = Path().apply {
            moveTo(c.x, c.y - r)
            quadraticTo(c.x + r * 0.18f, c.y - r * 0.18f, c.x + r, c.y)
            quadraticTo(c.x + r * 0.18f, c.y + r * 0.18f, c.x, c.y + r)
            quadraticTo(c.x - r * 0.18f, c.y + r * 0.18f, c.x - r, c.y)
            quadraticTo(c.x - r * 0.18f, c.y - r * 0.18f, c.x, c.y - r)
            close()
        }
        drawPath(path, brush)
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (highlight) Modifier.glass(RoundedCornerShape(50)) else Modifier.clip(RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = if (highlight) MaxCyan else MaxInk, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaxInk, fontSize = 14.sp)
    }
}

@Composable
fun MaxHomeScreen(vm: MainViewModel, nav: NavController) {
    val state by vm.assistantState.collectAsState()
    val userName by vm.userName.collectAsState()
    val persona by vm.personaMode.collectAsState()
    val speechRate by vm.prefs.speechRate.collectAsState(initial = 1.0f)
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

    var personaPicker by remember { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }
    var toolsSheet by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xF50B0B16), modifier = Modifier.width(290.dp)) {
                Column(Modifier.fillMaxHeight().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ROUT", color = MaxInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Search, "Search chats", tint = MaxInk, modifier = Modifier.size(22.dp)
                            .clickable { scope.launch { drawerState.close() }; nav.navigate("history") })
                        Spacer(Modifier.width(16.dp))
                        Icon(Icons.Default.Close, "Close", tint = MaxInk, modifier = Modifier.size(22.dp)
                            .clickable { scope.launch { drawerState.close() } })
                    }
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        DrawerRow(Icons.Default.Edit, "New chat", highlight = true) { scope.launch { drawerState.close() }; nav.navigate("chat") }
                        DrawerRow(Icons.Default.Menu, "Library") { scope.launch { drawerState.close() }; nav.navigate("history") }
                        DrawerRow(Icons.Default.Notifications, "Scheduled") { scope.launch { drawerState.close() }; nav.navigate("tasks") }
                        DrawerRow(Icons.Default.Settings, "Customize") { scope.launch { drawerState.close() }; nav.navigate("settings") }
                        DrawerRow(Icons.Default.PlayArrow, "Videos") { scope.launch { drawerState.close() }; nav.navigate("videoeditor") }
                        Spacer(Modifier.height(18.dp))
                        Text("Notes", color = MaxDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(Modifier.height(6.dp))
                        DrawerRow(Icons.Default.Add, "New note") { scope.launch { drawerState.close() }; nav.navigate("notes") }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("All", color = MaxDim, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.List, "Filter", tint = MaxDim, modifier = Modifier.size(18.dp)
                                .clickable { scope.launch { drawerState.close() }; nav.navigate("history") })
                        }
                        Spacer(Modifier.height(6.dp))
                        DrawerRow(Icons.Default.Refresh, "History") { scope.launch { drawerState.close() }; nav.navigate("history") }
                        DrawerRow(Icons.Default.Star, "Memory") { scope.launch { drawerState.close() }; nav.navigate("memory") }
                    }
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(10.dp)
                            .clickable { scope.launch { drawerState.close() }; nav.navigate("settings") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(
                            Brush.sweepGradient(listOf(MaxCyan, Color(0xFF8B5CF6), MaxCyan))),
                            contentAlignment = Alignment.Center
                        ) { Text((userName.ifBlank { "G" }).first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(userName.ifBlank { "Gyan" }, color = MaxInk, fontSize = 14.sp)
                            Text("Powered by Gyan", color = MaxDim, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.Settings, "Settings", tint = MaxDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(MaxBg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
            // Top bar: hamburger + persona dropdown + new chat + avatar
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, "Menu", tint = MaxInk, modifier = Modifier.size(24.dp).clickable { scope.launch { drawerState.open() } })
                Spacer(Modifier.width(16.dp))
                Row(Modifier.clip(RoundedCornerShape(50)).clickable { personaPicker = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("ROUT", color = MaxInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("  " + com.rout.aiassistant.ai.Personas.byId(persona).label, color = MaxDim, fontSize = 13.sp)
                    Icon(Icons.Default.ArrowDropDown, "pick", tint = MaxDim, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Edit, "New chat", tint = MaxInk, modifier = Modifier.size(21.dp).clickable { nav.navigate("chat") })
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(30.dp).clip(CircleShape).background(
                    Brush.sweepGradient(listOf(MaxCyan, Color(0xFF8B5CF6), MaxCyan)))
                    .clickable { accountSheet = true },
                    contentAlignment = Alignment.Center
                ) { Text((userName.ifBlank { "G" }).first().uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.weight(1f))
            // Center: sparkle + greeting
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Sparkle(30f)
                Spacer(Modifier.height(20.dp))
                Text(
                    when (state) {
                        AssistantState.LISTENING -> "Sun raha hoon ${userName.ifBlank { "Gyan" }}..."
                        AssistantState.SPEAKING -> "Ye raha jawab!"
                        AssistantState.PROCESSING -> "Soch raha hoon..."
                        else -> "Kya explore karein aaj,\n${userName.ifBlank { "Gyan" }}?"
                    },
                    fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                    style = TextStyle(brush = rgbBrush()),
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 34.sp
                )
            }
            Spacer(Modifier.weight(1f))

            // Suggestion rows
            val suggestions = listOf(
                Triple(Icons.Default.PlayArrow, "Video edit karo", "videoeditor"),
                Triple(Icons.Default.Search, "Document scan karo", "scanner"),
                Triple(Icons.Default.Build, "Code likho aur chalao", "codestudio")
            )
            suggestions.forEach { (icon, label, route) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable { nav.navigate(route) }.padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(30.dp).glass(RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Icon(icon, label, tint = MaxCyan, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = MaxInk, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            // Chat / Task pill
            var taskMode by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                listOf("Chat" to false, "Task" to true).forEach { (label, isTask) ->
                    val sel = taskMode == isTask
                    Text(label, color = if (sel) MaxInk else MaxDim, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(if (sel) Modifier.glass(RoundedCornerShape(50)) else Modifier)
                            .clickable { taskMode = isTask }
                            .padding(horizontal = 18.dp, vertical = 7.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            // Input bar: plus + field + mic + waveform
            var text by remember { mutableStateOf("") }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).glass(RoundedCornerShape(28.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, "Tools", tint = MaxDim, modifier = Modifier.size(22.dp).clickable { toolsSheet = true })
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isBlank()) Text(if (taskMode) "Naya task likho..." else "Try @ for more", color = MaxDim, fontSize = 15.sp)
                    BasicTextField(text, { text = it }, textStyle = TextStyle(color = MaxInk, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                            if (text.isNotBlank()) {
                                if (taskMode) { vm.addTask(text); vm.setBanner("Task added: $text") } else vm.onUserText(text, false)
                                text = ""
                            }
                        }))
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(24.dp).clickable { micTap() }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(15.dp)) {
                        val w = size.width; val h = size.height; val c = MaxDim
                        drawRoundRect(c, Offset(w * 0.32f, 0f), Size(w * 0.36f, h * 0.55f), CornerRadius(w * 0.18f))
                        drawArc(c, 0f, 180f, false, Offset(w * 0.14f, h * 0.28f), Size(w * 0.72f, h * 0.5f), style = Stroke(w * 0.1f))
                        drawLine(c, Offset(w * 0.5f, h * 0.78f), Offset(w * 0.5f, h * 0.95f), strokeWidth = w * 0.1f)
                        drawLine(c, Offset(w * 0.3f, h * 0.97f), Offset(w * 0.7f, h * 0.97f), strokeWidth = w * 0.1f)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaxCyan).clickable {
                    if (text.isNotBlank()) {
                        if (taskMode) { vm.addTask(text); vm.setBanner("Task added: $text") } else vm.onUserText(text, false)
                        text = ""
                    } else micTap()
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Send, "Go", tint = Color(0xFF0B0B16), modifier = Modifier.size(17.dp))
                }
            }
            // Powered by Gyan shimmer credit
            Text("Powered by Gyan", fontSize = 10.sp, style = TextStyle(brush = rgbBrush()),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    // Persona picker
    if (personaPicker) {
        AlertDialog(
            onDismissRequest = { personaPicker = false },
            containerColor = Color(0xF20B0B16),
            title = null,
            text = {
                Column {
                    com.rout.aiassistant.ai.Personas.ALL.forEach { p ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { scope.launch { vm.prefs.setPersonaMode(p.id) }; personaPicker = false }
                            .padding(vertical = 9.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (persona == p.id) Icon(Icons.Default.Check, "sel", tint = MaxCyan, modifier = Modifier.size(16.dp))
                            else Spacer(Modifier.width(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(p.label, color = MaxInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                if (p.style.isNotBlank()) Text(p.style.take(52) + if (p.style.length > 52) "..." else "", color = MaxDim, fontSize = 11.sp)
                                else Text("Sab kuch, seedhi baat", color = MaxDim, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider(color = MaxGlassLine)
                    Spacer(Modifier.height(8.dp))
                    Text("Speech speed", color = MaxInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
                    listOf(Triple("Slow", "Aaram se", 0.85f), Triple("Normal", "Balanced", 1.0f), Triple("Fast", "Jaldi jawab", 1.2f)).forEach { (label, sub, rate) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { scope.launch { vm.prefs.setSpeechRate(rate) } }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (kotlin.math.abs(speechRate - rate) < 0.05f) Icon(Icons.Default.Check, "sel", tint = MaxCyan, modifier = Modifier.size(16.dp))
                            else Spacer(Modifier.width(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(label, color = MaxInk, fontSize = 14.sp)
                                Text(sub, color = MaxDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Account sheet
    if (accountSheet) {
        AlertDialog(
            onDismissRequest = { accountSheet = false },
            containerColor = Color(0xF20B0B16),
            title = null,
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(
                            Brush.sweepGradient(listOf(MaxCyan, Color(0xFF8B5CF6), MaxCyan))),
                            contentAlignment = Alignment.Center
                        ) { Text((userName.ifBlank { "G" }).first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(userName.ifBlank { "Gyan" }, color = MaxInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Powered by Gyan", color = MaxCyan, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    DrawerRow(Icons.Default.Settings, "Settings") { accountSheet = false; nav.navigate("settings") }
                    DrawerRow(Icons.Default.Star, "Memory") { accountSheet = false; nav.navigate("memory") }
                    DrawerRow(Icons.Default.Refresh, "History") { accountSheet = false; nav.navigate("history") }
                    Spacer(Modifier.height(6.dp))
                    Text("ROUT 0.9.1", color = MaxDim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = {}
        )
    }

    // Tools sheet (plus button)
    if (toolsSheet) {
        AlertDialog(
            onDismissRequest = { toolsSheet = false },
            containerColor = Color(0xF20B0B16),
            title = null,
            text = {
                Column {
                    Text("Tools", color = MaxInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    listOf(
                        Triple("Translator", Icons.Default.PlayArrow, "translator"),
                        Triple("Scanner", Icons.Default.Search, "scanner"),
                        Triple("Code Studio", Icons.Default.Build, "codestudio"),
                        Triple("Video Editor", Icons.Default.PlayArrow, "videoeditor"),
                        Triple("Mirror", Icons.Default.Face, "mirror"),
                        Triple("Hand Magic", Icons.Default.ThumbUp, "handmagic"),
                        Triple("3D Avatar", Icons.Default.AccountCircle, "avatar"),
                        Triple("Tasks", Icons.Default.CheckCircle, "tasks"),
                        Triple("Notes", Icons.Default.Edit, "notes")
                    ).forEach { (label, icon, route) ->
                        DrawerRow(icon, label) { toolsSheet = false; nav.navigate(route) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
