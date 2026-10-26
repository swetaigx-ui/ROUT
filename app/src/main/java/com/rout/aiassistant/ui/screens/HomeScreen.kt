package com.rout.aiassistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.data.Message
import com.rout.aiassistant.ui.components.GlassBox
import com.rout.aiassistant.ui.components.GlassFill
import com.rout.aiassistant.ui.components.GlassFillStrong
import com.rout.aiassistant.ui.components.WarbyOrb
import com.rout.aiassistant.ui.components.Warby
import com.rout.aiassistant.ui.components.WarbyMode
import com.rout.aiassistant.ui.components.RoutBackground
import com.rout.aiassistant.ui.theme.*
import com.rout.aiassistant.voice.AssistantState

/**
 * Home: the user's approved reference design - ROUT AI header with status
 * pill, glowing R orb (tap to talk), live wave bars, QUICK ACTIONS tile grid,
 * pinned talk bar. Layout is fixed: header, tiles and talk bar never scroll;
 * only the conversation area scrolls. Chat keeps the real backend/commands.
 */
@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val state by vm.assistantState.collectAsState()
    val rawEmotion by vm.emotion.collectAsState()
    val warbyEmotionsOn by vm.warbyEmotions.collectAsState()
    val emotion = if (warbyEmotionsOn) rawEmotion else null
    val rms by vm.rms.collectAsState()
    val partial by vm.partial.collectAsState()
    val messages by vm.messages.collectAsState()
    val online by vm.online.collectAsState()
    val reducedMotion by vm.reducedMotion.collectAsState()
    val voiceMode by vm.voiceMode.collectAsState()
    val liveState by vm.liveState.collectAsState()
    val liveError by vm.liveError.collectAsState()
    val confirm by vm.confirm.collectAsState()
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var showMicRationale by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) vm.onMicTap() else vm.setBanner("Microphone denied. Text input still works.")
    }
    val micTap = { if (!micGranted) showMicRationale = true else vm.onMicTap() }

    // Auto-scroll only when already near the bottom - never yank the user upward.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex >= messages.size - 3) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    RoutBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
        ) {
            // ---- Header (pinned) ----
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ROUT AI",
                        style = TextStyle(
                            brush = RoutBrandGradient, fontSize = 27.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 3.sp
                        )
                    )
                    Text("YOUR PERSONAL AI", color = RoutDim, fontSize = 10.sp, letterSpacing = 3.sp,
                        modifier = Modifier.padding(top = 3.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pillText = when {
                        !online -> "Offline"
                        state == AssistantState.LISTENING -> "Listening"
                        state == AssistantState.PROCESSING -> "Thinking"
                        state == AssistantState.SPEAKING -> "Speaking"
                        state == AssistantState.EXECUTING -> "Working"
                        state == AssistantState.WAITING_CONFIRMATION -> "Confirm"
                        state == AssistantState.ERROR -> "Error"
                        else -> "Ready"
                    }
                    val persona by vm.personaMode.collectAsState()
                    val personaLabel = com.rout.aiassistant.ai.Personas.byId(persona).label
                    val pillFull = if (personaLabel.isNotBlank() && personaLabel != "Default") "$pillText - $personaLabel" else pillText
                    val dotColor = when {
                        !online -> RoutError
                        state == AssistantState.ERROR -> RoutError
                        state == AssistantState.IDLE -> RoutGreen
                        else -> RoutCyan
                    }
                    GlassBox(radius = 50.dp) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).background(dotColor, RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            Text(pillFull, color = RoutIce, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassBox(radius = 50.dp) {
                        Icon(Icons.Default.Settings, "Settings", tint = RoutDim,
                            modifier = Modifier.size(40.dp).clickable { nav.navigate("settings") }.padding(10.dp))
                    }
                }
            }

            // ---- Middle: orb + state, or conversation ----
            if (messages.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.clickable { micTap() }, contentAlignment = Alignment.Center) {
                        WarbyOrb(state = state, emotion = emotion, rms = rms, reducedMotion = reducedMotion, size = 200.dp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        when (state) {
                            AssistantState.LISTENING -> "I'm listening..."
                            AssistantState.ERROR -> "Kuch gadbad ho gayi"
                            else -> "Your AI. Your World."
                        },
                        color = RoutText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (state) {
                            AssistantState.LISTENING -> if (partial.isBlank()) "Boliyega, main sun raha hoon..." else partial
                            AssistantState.PROCESSING -> "Soch raha hoon..."
                            AssistantState.SPEAKING -> "Bol raha hoon..."
                            AssistantState.EXECUTING -> "Kar raha hoon..."
                            AssistantState.ERROR -> "Mic ya network check karo, ya type karo"
                            else -> "Tap the R orb, or type below."
                        },
                        color = if (state == AssistantState.ERROR) RoutError else RoutDim,
                        fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
                        modifier = Modifier.widthIn(max = 310.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    WaveBars(active = state == AssistantState.LISTENING, rms = rms, reducedMotion = reducedMotion)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        warbyStatusLabel(state, emotion),
                        color = RoutDim, fontSize = 11.sp, letterSpacing = 2.sp
                    )
                }
            } else {
                // Compact orb row + conversation (only this area scrolls)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.clickable { micTap() }, contentAlignment = Alignment.Center) {
                        WarbyOrb(state = state, emotion = emotion, rms = rms, reducedMotion = reducedMotion, size = 84.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when (state) {
                                AssistantState.LISTENING -> if (partial.isBlank()) "Listening..." else partial
                                AssistantState.PROCESSING -> "Thinking..."
                                AssistantState.SPEAKING -> "Speaking..."
                                AssistantState.EXECUTING -> "Working..."
                                AssistantState.WAITING_CONFIRMATION -> "Confirm on screen, or say haan"
                                AssistantState.ERROR -> "Error - try again or type"
                                AssistantState.IDLE -> "Ready"
                            },
                            color = if (state == AssistantState.ERROR) RoutError else RoutDim,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2
                        )
                        if (state == AssistantState.LISTENING) {
                            Spacer(Modifier.height(6.dp))
                            WaveBars(active = true, rms = rms, reducedMotion = reducedMotion, barCount = 10, maxH = 18)
                        }
                    }
                }
                // During a LIVE conversation the chat list is hidden (his request); it returns when LIVE ends.
                if (liveState == "live" || liveState == "connecting") {
                    Spacer(Modifier.weight(1f).fillMaxWidth())
                } else LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { m -> MessageBubble(m, onReplay = { vm.replay(m.text) }) }
                }
            }

            // ---- Quick actions (pinned) ----
            Text("QUICK ACTIONS", color = RoutDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            val tiles = listOf(
                Triple("▶️", "YouTube", "YouTube kholo"),
                Triple("📞", "Call", "Phone kholo"),
                Triple("🌤️", "Weather", "Weather batao"),
                Triple("⌕", "Search", "Google kholo"),
                Triple("◷", "Time", "Time batao"),
                Triple("✧", "More", "Help")
            )
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (ico, label, cmd) ->
                        GlassBox(radius = 18.dp, modifier = Modifier.weight(1f)) {
                            Column(
                                Modifier.fillMaxWidth().clickable { vm.onUserText(cmd, false) }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(ico, fontSize = 22.sp)
                                Spacer(Modifier.height(5.dp))
                                Text(label, color = RoutIce, fontSize = 12.sp)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ---- Talk bar (pinned) ----
            GlassBox(Modifier.fillMaxWidth().imePadding(), radius = 28.dp, fill = GlassFillStrong) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                    TextField(
                        value = text, onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask ROUT anything...", color = RoutDim, fontSize = 14.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = RoutText, unfocusedTextColor = RoutText
                        )
                    )
                    Box(
                        Modifier.size(42.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF1268A8), Color(0xFF2631A0))), RoundedCornerShape(50)
                        ).clickable { if (text.isNotBlank()) { vm.onUserText(text, false); text = "" } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Send, "Send", tint = RoutIce, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(52.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF41EAFF), Color(0xFF427AFF))), RoundedCornerShape(50)
                        ).clickable { micTap() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (state == AssistantState.LISTENING) "■" else "🎙",
                            fontSize = 20.sp, color = Color(0xFF061324)
                        )
                    }
                }
            }
            // Live voice + stop controls (compact, only when relevant)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        liveState == "live" -> "LIVE on - tap to end"
                        liveState == "connecting" -> "Live connecting..."
                        liveState == "failed" -> (liveError ?: "Live unavailable")
                        voiceMode == "natural" -> "Natural voice on"
                        else -> ""
                    },
                    color = if (liveState == "failed") RoutError else RoutDim,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    modifier = Modifier.clickable { vm.toggleLive() }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (liveState != "live" && liveState != "connecting") {
                        Text("LIVE", color = RoutCyan, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { if (!micGranted) showMicRationale = true else vm.toggleLive() }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (state != AssistantState.IDLE) {
                        Text("STOP", color = RoutError, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { vm.stopAll() }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            // Bottom navigation (replaces the red-marked "POWERED BY GYAN" footer, per his video).
            GlassBox(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), radius = 24.dp, fill = Color(0x2E101A2B)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    Triple(Icons.Default.Home, "Home", ""),
                    Triple(Icons.Default.Edit, "Notes", "notes"),
                    Triple(Icons.Default.Check, "Tasks", "tasks"),
                    Triple(Icons.Default.Favorite, "Memory", "memory"),
                    Triple(Icons.AutoMirrored.Filled.List, "History", "history"),
                    Triple(Icons.Default.Settings, "Settings", "settings")
                ).forEach { (icon, label, route) ->
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .clickable { if (route.isNotEmpty()) nav.navigate(route) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, label, tint = if (route.isEmpty()) RoutCyan else RoutDim, modifier = Modifier.size(22.dp))
                        Text(label, color = if (route.isEmpty()) RoutIce else RoutDim, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
            }
        }

        // Mic rationale dialog
        if (showMicRationale) {
            AlertDialog(
                onDismissRequest = { showMicRationale = false },
                confirmButton = {
                    TextButton(onClick = {
                        showMicRationale = false
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("Allow microphone", color = RoutCyan) }
                },
                dismissButton = { TextButton(onClick = { showMicRationale = false }) { Text("Not now", color = RoutDim) } },
                title = { Text("Microphone access", color = RoutText) },
                text = {
                    Text(
                        "ROUT uses the microphone only when you tap the mic or enable the Hey ROUT session. " +
                        "Speech is processed by Android's speech service. ROUT does not store raw recordings. " +
                        "You can revoke this anytime in Android Settings.",
                        color = RoutDim
                    )
                },
                containerColor = RoutGraphite
            )
        }

        // Confirmation dialog for consequential actions (call / sms)
        confirm?.let { req ->
            AlertDialog(
                onDismissRequest = { vm.cancelPending() },
                confirmButton = { TextButton(onClick = { vm.confirmPending() }) { Text("Confirm", color = RoutCyan) } },
                dismissButton = { TextButton(onClick = { vm.cancelPending() }) { Text("Cancel", color = RoutError) } },
                title = { Text("Confirm action", color = RoutText) },
                text = { Text(req.description, color = RoutDim) },
                containerColor = RoutGraphite
            )
        }
    }
}

@Composable
fun WaveBars(active: Boolean, rms: Float, reducedMotion: Boolean, barCount: Int = 12, maxH: Int = 26) {
    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (reducedMotion) 1600 else 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )
    val level = ((rms + 2f) / 12f).coerceIn(0f, 1f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(maxH.dp)
    ) {
        for (i in 0 until barCount) {
            val wave = if (active) {
                7f + (maxH - 7f) * (0.25f + 0.75f * level) *
                    kotlin.math.abs(kotlin.math.sin((phase * 2f + i * 0.45f) * Math.PI)).toFloat()
            } else 7f
            Box(
                Modifier.width(3.dp).height(wave.dp)
                    .background(RoutCyan.copy(alpha = if (active) 0.9f else 0.55f), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
fun MessageBubble(m: Message, onReplay: () -> Unit) {
    val isUser = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        GlassBox(
            radius = 20.dp,
            fill = if (isUser) Color(0x1A4DE8FF) else GlassFill,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Text("ROUT", color = RoutCyan, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp))
                    Spacer(Modifier.height(4.dp))
                }
                Text(m.text, color = RoutText, style = MaterialTheme.typography.bodyMedium)
                if (!isUser) {
                    Text("replay", color = RoutDim, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(onClick = onReplay).padding(top = 4.dp))
                }
            }
        }
    }
}

/** His warby-orb.js status labels, live-state first, then emotion. */
private fun warbyStatusLabel(state: AssistantState, emotion: com.rout.aiassistant.ui.components.WarbyEmotion?): String {
    val mode = when (state) {
        AssistantState.LISTENING -> "listening"
        AssistantState.SPEAKING -> "talking"
        AssistantState.PROCESSING, AssistantState.EXECUTING -> "thinking"
        AssistantState.WAITING_CONFIRMATION -> "curious"
        AssistantState.ERROR -> "sad"
        AssistantState.IDLE -> emotion?.name?.lowercase() ?: "idle"
    }
    return Warby.label(mode)
}
