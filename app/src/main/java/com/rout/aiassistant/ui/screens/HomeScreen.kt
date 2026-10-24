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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.data.Message
import com.rout.aiassistant.ui.components.OrbView
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

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(RoutBlack, RoutGraphite, RoutBlack)))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Status row
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("ROUT", style = MaterialTheme.typography.headlineLarge, color = RoutCyan, maxLines = 1)
                    Text("PERSONAL AI  ·  POWERED BY GYAN", style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(if (online) "Online" else "Offline", if (online) RoutCyan else RoutError)
                    Spacer(Modifier.height(4.dp))
                    StatusPill(aiStatus, if (aiStatus.contains("setup", true)) RoutViolet else RoutCyan)
                    Spacer(Modifier.height(4.dp))
                    StatusPill(if (micGranted) "Mic ready" else "Mic off", if (micGranted) RoutCyan else RoutDim)
                }
            }

            // Orb + state
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                OrbView(state = state, rms = rms, reducedMotion = reducedMotion, size = 210.dp)
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
                    listOf(
                        "\"YouTube kholo\"",
                        "\"Torch on karo\"",
                        "\"Weather batao\"",
                        "\"Note banao: kal ka plan\"",
                        "\"Yaad rakho: mera birthday 5 May\"",
                        "\"Alarm set karo 6 baje\""
                    ).forEach { hint ->
                        Surface(color = RoutPanel, shape = RoundedCornerShape(50), modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(hint, color = RoutIce, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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

            // Quick actions
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                QuickAction("YouTube") { vm.onUserText("YouTube kholo", false) }
                QuickAction("Weather") { vm.onUserText("weather batao", false) }
                QuickAction("Notes") { nav.navigate("notes") }
                QuickAction("Tasks") { nav.navigate("tasks") }
                QuickAction("Torch") { vm.onUserText("torch on", false) }
                QuickAction("Memory") { nav.navigate("memory") }
                QuickAction("History") { nav.navigate("history") }
            }

            Spacer(Modifier.height(8.dp))

            // Input row
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type to ROUT... (Hindi/Hinglish/English)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoutCyan, unfocusedBorderColor = RoutDim,
                        focusedTextColor = RoutText, unfocusedTextColor = RoutText
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { if (text.isNotBlank()) { vm.onUserText(text, false); text = "" } },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = RoutCyan)
                ) { Icon(Icons.Default.Send, "Send", tint = RoutBlack) }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (!micGranted) showMicRationale = true else vm.onMicTap()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state == AssistantState.LISTENING) RoutViolet else RoutCyan
                    )
                ) { Text(if (state == AssistantState.LISTENING) "STOP" else "MIC", color = RoutBlack, style = MaterialTheme.typography.labelSmall) }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { nav.navigate("settings") }) { Text("Settings & Privacy", color = RoutDim) }
                if (state != AssistantState.IDLE) {
                    TextButton(onClick = { vm.stopAll() }) { Text("STOP", color = RoutError) }
                }
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
fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun QuickAction(label: String, onClick: () -> Unit) {
    Surface(
        color = RoutPanel, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, color = RoutIce, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

@Composable
fun MessageBubble(m: Message, onReplay: () -> Unit) {
    val isUser = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (isUser) RoutCyan.copy(alpha = 0.15f) else RoutPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(m.text, color = RoutText, style = MaterialTheme.typography.bodyMedium)
                if (!isUser) {
                    Text("replay", color = RoutDim, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(onClick = onReplay).padding(top = 4.dp))
                }
            }
        }
    }
}
