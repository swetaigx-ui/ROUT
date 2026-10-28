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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.rout.aiassistant.ui.components.SiriOrb
import com.rout.aiassistant.ui.components.RoutDock
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val voiceDetail by vm.voiceDetail.collectAsState()
    val liveState by vm.liveState.collectAsState()
    val liveError by vm.liveError.collectAsState()
    val liveHeard by vm.liveHeard.collectAsState()
    val confirm by vm.confirm.collectAsState()
    val context = LocalContext.current

    val listState = rememberLazyListState()

    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var showMicRationale by remember { mutableStateOf(false) }
    var pendingMicAction by remember { mutableStateOf<() -> Unit>({ vm.onMicTap() }) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) pendingMicAction() else vm.setBanner("Microphone denied. Text input still works.")
    }
    val micTap = { if (!micGranted) { pendingMicAction = { vm.onMicTap() }; showMicRationale = true } else vm.onMicTap() }

    // Auto-scroll only when already near the bottom - never yank the user upward.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex >= messages.size - 3) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    RoutBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Top: persona pill + settings ----
            val persona by vm.personaMode.collectAsState()
            val personaLabel = com.rout.aiassistant.ai.Personas.byId(persona).label
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassBox(radius = 50.dp) {
                    Row(
                        Modifier.clickable {
                            val all = com.rout.aiassistant.ai.Personas.ALL
                            val i = all.indexOfFirst { it.id == persona }.coerceAtLeast(0)
                            vm.setPersona(all[(i + 1) % all.size].id)
                        }.padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(9.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF5EEAD4), Color(0xFFA855F7))), RoundedCornerShape(50)))
                        Spacer(Modifier.width(10.dp))
                        Text("ROUT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("  \u00B7 $personaLabel \u25BE", color = Color(0xFF8D97A6), fontSize = 15.sp, maxLines = 1)
                    }
                }
                GlassBox(radius = 50.dp) {
                    Icon(Icons.Default.Settings, "Settings", tint = Color(0xFFC9D2DE),
                        modifier = Modifier.size(46.dp).clickable { nav.navigate("settings") }.padding(12.dp))
                }
            }

            // ---- Stage: orb, status, caption ----
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.clickable { micTap() }, contentAlignment = Alignment.Center) {
                    SiriOrb(state = state, rms = rms, reducedMotion = reducedMotion, size = 220.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        liveState == "live" -> "LIVE"
                        liveState == "connecting" -> "CONNECTING\u2026"
                        !online && state == AssistantState.IDLE -> "OFFLINE \u00B7 TAP TO TALK"
                        state == AssistantState.LISTENING -> "LISTENING\u2026"
                        state == AssistantState.PROCESSING -> "THINKING\u2026"
                        state == AssistantState.SPEAKING -> "SPEAKING"
                        state == AssistantState.EXECUTING -> "WORKING\u2026"
                        state == AssistantState.WAITING_CONFIRMATION -> "CONFIRM ON SCREEN"
                        state == AssistantState.ERROR -> "ERROR"
                        else -> "TAP TO TALK"
                    },
                    color = if (state == AssistantState.ERROR) RoutError else Color(0xFF8D97A6),
                    fontSize = 12.sp, letterSpacing = 3.sp
                )
                val lastReply = messages.lastOrNull { it.role != "user" }?.text
                val caption = when {
                    state == AssistantState.LISTENING && partial.isNotBlank() -> partial
                    liveState == "live" -> "LIVE on - tap to end"
                    lastReply != null -> lastReply
                    else -> ""
                }
                Box(Modifier.heightIn(min = 44.dp).padding(top = 8.dp).clickable(enabled = liveState == "live") { vm.toggleLive() }, contentAlignment = Alignment.Center) {
                    Text(caption, color = Color(0xFFF5F7FA), fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 320.dp))
                }
            }

            // ---- Status line: live / voice diagnostics ----
            val statusText = when {
                liveState == "connecting" -> "Live connecting..."
                liveState == "failed" -> (liveError ?: "Live unavailable")
                liveState == "live" && liveHeard.isNotBlank() -> "Sun raha: " + liveHeard
                liveState == "live" -> "LIVE on - bolo, ROUT sun raha hai"
                voiceDetail.isNotBlank() -> "Last voice: " + voiceDetail
                else -> ""
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(statusText, color = if (liveState == "failed") RoutError else Color(0xFF8D97A6),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                if (liveState != "live" && liveState != "connecting") {
                    Text("LIVE", color = RoutCyan, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { if (!micGranted) { pendingMicAction = { vm.toggleLive() }; showMicRationale = true } else vm.toggleLive() }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                if (state != AssistantState.IDLE) {
                    Text("STOP", color = RoutError, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { vm.stopAll() }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            // ---- Skill cards (real ROUT commands) ----
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple("\uD83D\uDD52", "Time & Date" to "Abhi kitne baje?", "time batao"),
                    Triple("\u23F1\uFE0F", "Timer" to "2 minute ka timer", "2 minute ka timer"),
                    Triple("\uD83D\uDCDD", "Notes" to "Kya yaad hai?", "__notes"),
                    Triple("\uD83C\uDF24\uFE0F", "Weather" to "Aaj ka mausam", "weather batao"),
                    Triple("\u25B6\uFE0F", "YouTube" to "YouTube kholo", "YouTube kholo"),
                    Triple("\uD83D\uDCDE", "Call" to "Kisi ko call karo", "phone kholo")
                ).forEach { (ico, titleSub, cmd) ->
                    GlassBox(Modifier.width(150.dp).height(120.dp), radius = 22.dp) {
                        Column(
                            Modifier.fillMaxSize().clickable { if (cmd == "__notes") nav.navigate("notes") else vm.onUserText(cmd, false) }.padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(ico, fontSize = 26.sp)
                            Column {
                                Text(titleSub.first, color = Color(0xFFF5F7FA), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(titleSub.second, color = Color(0xFF8D97A6), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // ---- Dock ----
            RoutDock("home", nav, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
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

        // Floating LIVE overlay permission: asked explicitly, never drawn silently.
        val overlayPrompt by vm.overlayPrompt.collectAsState()
        if (overlayPrompt) {
            AlertDialog(
                onDismissRequest = { vm.dismissOverlayPrompt() },
                confirmButton = {
                    TextButton(onClick = {
                        vm.dismissOverlayPrompt()
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + context.packageName)
                            )
                        )
                    }) { Text("Open settings", color = RoutCyan) }
                },
                dismissButton = { TextButton(onClick = { vm.dismissOverlayPrompt() }) { Text("Later", color = RoutDim) } },
                title = { Text("Floating LIVE", color = RoutText) },
                text = {
                    Text(
                        "Back dabaane ke baad screen par tairta orb dikhane ke liye 'Display over other apps' permission chahiye. " +
                        "Bina iske LIVE phir bhi chalega - notification mein STOP button rahega.",
                        color = RoutDim
                    )
                },
                containerColor = RoutGraphite
            )
        }

        // Confirmation dialog for consequential actions (call / sms)
        confirm?.let { req ->
            com.rout.aiassistant.ui.components.RoutApprovalDialog(
                title = "Confirm action", confirmLabel = "Confirm", dismissLabel = "Cancel",
                onConfirm = { vm.confirmPending() }, onDismiss = { vm.cancelPending() }, container = RoutGraphite
            ) {
                Column {
                    req.imageJpeg?.let { bytes ->
                        val bmp = remember(bytes) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                        if (bmp != null) Image(bmp, contentDescription = "Screenshot jo AI ko jaayega", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(bottom = 10.dp))
                    }
                    Text(req.description, color = RoutDim)
                }
            }
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
