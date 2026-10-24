package com.rout.aiassistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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
import com.rout.aiassistant.ui.components.OrbView
import com.rout.aiassistant.ui.components.RoutBackground
import com.rout.aiassistant.ui.theme.*
import com.rout.aiassistant.voice.AssistantState

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val state by vm.assistantState.collectAsState()
    val rms by vm.rms.collectAsState()
    val partial by vm.partial.collectAsState()
    val messages by vm.messages.collectAsState()
    val online by vm.online.collectAsState()
    val aiStatus by vm.aiStatus.collectAsState()
    val confirm by vm.confirm.collectAsState()
    val reducedMotion by vm.reducedMotion.collectAsState()
    val wakeEnabled by vm.wakeEnabled.collectAsState()
    val voiceMode by vm.voiceMode.collectAsState()
    val liveState by vm.liveState.collectAsState()
    val liveTranscript by vm.liveTranscript.collectAsState()
    val liveError by vm.liveError.collectAsState()
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

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    RoutBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Status row
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("ROUT", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp, letterSpacing = 8.sp), color = RoutCyan, maxLines = 1)
                    Text("PERSONAL AI  ·  POWERED BY GYAN", style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pillText = when {
                        !online -> "Offline"
                        aiStatus.isNotBlank() -> aiStatus
                        else -> "Ready"
                    }
                    val pillColor = when {
                        !online -> RoutError
                        aiStatus.contains("setup", true) || aiStatus.contains("connect", true) -> RoutViolet
                        else -> RoutCyan
                    }
                    GlassBox(radius = 50.dp) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clickable { nav.navigate("settings") },
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(pillColor, RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            Text(pillText, color = RoutDim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassBox(radius = 50.dp) {
                        Icon(Icons.Default.Settings, "Settings", tint = RoutDim,
                            modifier = Modifier.size(44.dp).clickable { nav.navigate("settings") }.padding(12.dp))
                    }
                }
            }

            // Orb + state
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(300.dp).drawBehind { drawCircle(androidx.compose.ui.graphics.Color(0x124DE8FF)) })
                    OrbView(state = state, rms = rms, reducedMotion = reducedMotion, size = 210.dp)
                }
                Text(
                    when (state) {
                        AssistantState.IDLE -> if (wakeEnabled) "Hey ROUT session active" else "Idle - tap the mic or type below"
                        AssistantState.LISTENING -> if (partial.isBlank()) "Listening..." else partial
                        AssistantState.PROCESSING -> "Processing..."
                        AssistantState.SPEAKING -> "Speaking..."
                        AssistantState.EXECUTING -> "Executing..."
                        AssistantState.WAITING_CONFIRMATION -> "Waiting for your confirmation"
                        AssistantState.ERROR -> "Error - check mic and network, or type instead"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state == AssistantState.ERROR) RoutError else RoutDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Conversation
            if (messages.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Bolo ya type karo - kya karoon?", style = MaterialTheme.typography.titleMedium, color = RoutDim)
                    Spacer(Modifier.height(14.dp))
                    listOf("Weather batao", "YouTube kholo", "Alarm set karo 6 baje").forEach { hint ->
                        GlassBox(radius = 50.dp, modifier = Modifier.padding(vertical = 5.dp)) {
                            Text("\"$hint\"", color = RoutIce, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable { vm.onUserText(hint, false) }.padding(horizontal = 18.dp, vertical = 10.dp))
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { m -> MessageBubble(m, onReplay = { vm.replay(m.text) }) }
                }
            }

            val voiceLabel = when {
                liveState == "live" -> "Live voice on" + if (liveTranscript.isNotBlank()) ": $liveTranscript" else ""
                liveState == "connecting" -> "Connecting live voice..."
                liveState == "failed" -> (liveError ?: "Live unavailable") + " Using normal voice."
                voiceMode == "natural" -> "Voice: natural (backend)"
                voiceMode == "unavailable" -> "Voice: unavailable on this device. Text replies only."
                else -> "Voice: device fallback (natural voice needs enrolment)"
            }
            Text(voiceLabel, color = RoutDim, style = MaterialTheme.typography.labelSmall,
                maxLines = 2, modifier = Modifier.padding(bottom = 4.dp, start = 6.dp))
            // Input bar (glass)
            GlassBox(Modifier.fillMaxWidth().padding(bottom = 8.dp), radius = 28.dp, fill = GlassFillStrong) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 8.dp)) {
                    TextField(
                        value = text, onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type to ROUT... (Hindi/Hinglish/English)", color = RoutDim) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedTextColor = RoutText, unfocusedTextColor = RoutText
                        )
                    )
                    GlassBox(radius = 50.dp) {
                        Text(if (liveState == "live" || liveState == "connecting") "END" else "LIVE",
                            color = if (liveState == "live") RoutCyan else RoutIce,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { if (!micGranted) showMicRationale = true else vm.toggleLive() }
                                .padding(horizontal = 12.dp, vertical = 12.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    GlassBox(radius = 50.dp) {
                        Text(if (state == AssistantState.LISTENING) "STOP" else "MIC",
                            color = if (state == AssistantState.LISTENING) RoutViolet else RoutIce,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { if (!micGranted) showMicRationale = true else vm.onMicTap() }
                                .padding(horizontal = 14.dp, vertical = 12.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(44.dp).background(RoutCyan, RoundedCornerShape(50))
                        .clickable { if (text.isNotBlank()) { vm.onUserText(text, false); text = "" } },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Send, "Send", tint = RoutBlack, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (state != AssistantState.IDLE) {
                TextButton(onClick = { vm.stopAll() }, modifier = Modifier.fillMaxWidth()) { Text("STOP", color = RoutError) }
            }
        }

        // Mic rationale dialog (explain before requesting)
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
fun MessageBubble(m: Message, onReplay: () -> Unit) {
    val isUser = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        GlassBox(
            radius = if (isUser) 20.dp else 20.dp,
            fill = if (isUser) androidx.compose.ui.graphics.Color(0x1A4DE8FF) else GlassFill,
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
