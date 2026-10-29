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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.control.ControlState as CS
import com.rout.aiassistant.data.Message
import com.rout.aiassistant.ui.components.GlassBox
import com.rout.aiassistant.ui.components.GlassFill
import com.rout.aiassistant.ui.components.RefIcons
import com.rout.aiassistant.ui.components.RoutDock
import com.rout.aiassistant.ui.components.SiriOrb
import com.rout.aiassistant.ui.components.WarmGlassBox
import com.rout.aiassistant.ui.components.Warby
import com.rout.aiassistant.ui.theme.*
import com.rout.aiassistant.voice.AssistantState

/**
 * Home: Gyan's approved 2026-10-29 reference design (he picked the cream-glass
 * preview himself: "sab paanch se yah accha laga, isko add karo"). Compact
 * header (avatar + ROUT + persona selector), one large luminous glass hero
 * panel holding the real SiriOrb (tap = mic) with the status line + LIVE
 * toggle in its footer, six real skill commands as a 2-column glass pill grid,
 * and the existing RoutDock. Warm near-black backdrop with localized
 * cream/amber light pools that show through the translucent glass. Every
 * control is wired to the same real actions as before - only the visual
 * composition changed. The frosted glass samples a pre-rendered software-blurred
 * copy of this painted backdrop (see components/FrostedGlass.kt).
 */
private val CreamText = Color(0xFFFDF8F1)
private val CreamDim = Color(0x99FDF8F1)
private val LiveAmber = Color(0xFFFF8A2A)
private val IconAmber = Color(0xFFFFB04A)
private val IconChip = Color(0x38FF963C)

/** Approved backdrop: near-black base, localized cream pool upper-middle,
 *  warm amber pool lower-middle, faint olive top-right, dark falloff bottom. */
@Composable
private fun RoutHomeBackground(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var backdrop by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }.toInt()
        val hPx = with(density) { maxHeight.toPx() }.toInt()
        LaunchedEffect(wPx, hPx) {
            if (wPx > 0 && hPx > 0) {
                backdrop = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.rout.aiassistant.ui.components.buildBlurredHomeBackdrop(wPx, hPx)
                }
            }
        }
        var bgOrigin by remember { mutableStateOf(Offset.Zero) }
        val bgBmp = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.rout.aiassistant.R.drawable.home_bg)
        androidx.compose.runtime.CompositionLocalProvider(
            com.rout.aiassistant.ui.components.LocalFrostedBackdrop provides
                backdrop?.let { com.rout.aiassistant.ui.components.FrostedBackdrop(it, bgOrigin) }
        ) {
            Box(
                Modifier.fillMaxSize()
                    .onGloballyPositioned { bgOrigin = it.boundsInRoot().topLeft }
                    .drawBehind {
                    // Approved home11 background texture (bg7), fill-crop centered.
                    val bw = bgBmp.width.toFloat(); val bh = bgBmp.height.toFloat()
                    val sc = maxOf(size.width / bw, size.height / bh)
                    val dw = bw * sc; val dh = bh * sc
                    val ox = (size.width - dw) / 2f; val oy = (size.height - dh) / 2f
                    drawImage(bgBmp,
                        srcOffset = IntOffset.Zero, srcSize = IntSize(bgBmp.width, bgBmp.height),
                        dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                        dstSize = IntSize(dw.toInt(), dh.toInt()),
                        filterQuality = FilterQuality.High)
                    },
                content = content
            )
        }
    }
}

private data class Skill(val icon: ImageVector, val title: String, val sub: String, val cmd: String)

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val state by vm.assistantState.collectAsState()
    val rms by vm.rms.collectAsState()
    val partial by vm.partial.collectAsState()
    val messages by vm.messages.collectAsState()
    val online by vm.online.collectAsState()
    val reducedMotion by vm.reducedMotion.collectAsState()
    val voiceDetail by vm.voiceDetail.collectAsState()
    val liveState by vm.liveState.collectAsState()
    val liveError by vm.liveError.collectAsState()
    val liveHeard by vm.liveHeard.collectAsState()
    val confirm by vm.confirm.collectAsState()
    val context = LocalContext.current

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
    val liveTap = { if (!micGranted) { pendingMicAction = { vm.toggleLive() }; showMicRationale = true } else vm.toggleLive() }

    RoutHomeBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Header: avatar + ROUT + persona selector, settings gear ----
            val persona by vm.personaMode.collectAsState()
            val personaLabel = com.rout.aiassistant.ai.Personas.byId(persona).label
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clickable {
                        val all = com.rout.aiassistant.ai.Personas.ALL
                        val i = all.indexOfFirst { it.id == persona }.coerceAtLeast(0)
                        vm.setPersona(all[(i + 1) % all.size].id)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(
                            Brush.sweepGradient(listOf(
                                Color(0xFFFF7A1A), Color(0xFFFFB04A), Color(0xFFB44CF5),
                                Color(0xFF5D7BFF), Color(0xFFFF7A1A)
                            ))
                        ),
                        contentAlignment = Alignment.Center
                    ) { Text("R", color = Color(0xFF160903), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Mode: $personaLabel \u25BE", color = CreamDim, fontSize = 10.sp, maxLines = 1)
                        Text("ROUT", color = CreamText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                WarmGlassBox(Modifier, radius = 17.dp) {
                    Icon(Icons.Default.Settings, "Settings", tint = Color(0xFFF5E2CC),
                        modifier = Modifier.size(34.dp).clickable { nav.navigate("settings") }.padding(8.dp))
                }
            }

            ControlStrip(vm)

            // ---- Hero: big luminous glass panel, orb + status + LIVE ----
            WarmGlassBox(Modifier.fillMaxWidth().fillMaxHeight(0.52f), radius = 26.dp, hero = true, tex = com.rout.aiassistant.R.drawable.glass_hero) {
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.clickable { micTap() }, contentAlignment = Alignment.Center) {
                            SiriOrb(state = state, rms = rms, reducedMotion = reducedMotion, size = 236.dp)
                        }
                    }
                    val lastReply = messages.lastOrNull { it.role != "user" }?.text
                    val caption = when {
                        state == AssistantState.LISTENING && partial.isNotBlank() -> partial
                        liveState == "live" && liveHeard.isNotBlank() -> "Sun raha: $liveHeard"
                        else -> ""
                    }
                    if (caption.isNotBlank()) {
                        Text(caption, color = CreamText, fontSize = 13.sp, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            color = if (state == AssistantState.ERROR) RoutError else CreamDim,
                            fontSize = 10.sp, letterSpacing = 3.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (liveState == "live") "END" else "LIVE",
                            color = LiveAmber, fontSize = 10.5.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { liveTap() }.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // ---- Diagnostics: live/voice status + STOP (only when there is something to say) ----
            val statusText = when {
                liveState == "connecting" -> "Live connecting..."
                liveState == "failed" -> (liveError ?: "Live unavailable")
                liveState == "live" && liveHeard.isBlank() -> "LIVE on - bolo, ROUT sun raha hai"
                voiceDetail.isNotBlank() -> "Last voice: " + voiceDetail
                else -> ""
            }
            if (statusText.isNotBlank() || state != AssistantState.IDLE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(statusText, color = if (liveState == "failed") RoutError else CreamDim,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                    if (state != AssistantState.IDLE) {
                        Text("STOP", color = RoutError, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { vm.stopAll() }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            // ---- Six real skills, 2-column glass pills ----
            val skills = listOf(
                Skill(RefIcons.Clock, "Time & Date", "Abhi kitne baje?", "time batao"),
                Skill(RefIcons.Timer, "Timer", "2 minute ka timer", "2 minute ka timer"),
                Skill(RefIcons.NotePen, "Notes", "Kya yaad hai?", "__notes"),
                Skill(RefIcons.Cloud, "Weather", "Aaj ka mausam", "weather batao"),
                Skill(RefIcons.YouTube, "YouTube", "YouTube kholo", "YouTube kholo"),
                Skill(RefIcons.Phone, "Call", "Kisi ko call karo", "phone kholo")
            )
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                skills.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        pair.forEach { s ->
                            val texIdx = skills.indexOf(s) + 1
                            val texRes = when (texIdx) {
                                1 -> com.rout.aiassistant.R.drawable.glass_sk1
                                2 -> com.rout.aiassistant.R.drawable.glass_sk2
                                3 -> com.rout.aiassistant.R.drawable.glass_sk3
                                4 -> com.rout.aiassistant.R.drawable.glass_sk4
                                5 -> com.rout.aiassistant.R.drawable.glass_sk5
                                else -> com.rout.aiassistant.R.drawable.glass_sk6
                            }
                            SkillPill(s, Modifier.weight(1f), texRes) {
                            if (s.cmd == "__notes") nav.navigate("notes") else vm.onUserText(s.cmd, false)
                        } }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

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
private fun SkillPill(s: Skill, modifier: Modifier, texRes: Int, onClick: () -> Unit) {
    WarmGlassBox(modifier.height(52.dp), radius = 16.dp, tex = texRes) {
        Row(
            Modifier.fillMaxSize().clickable(onClick = onClick).padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(IconChip),
                contentAlignment = Alignment.Center
            ) { Icon(s.icon, contentDescription = null, tint = IconAmber, modifier = Modifier.size(14.dp)) }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(s.title, color = CreamText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(s.sub, color = CreamDim, fontSize = 9.5.sp, maxLines = 1)
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


/** Execution progress for the device-control engine: current state, short log and a prominent Stop. Hidden when idle. */
@Composable
private fun ControlStrip(vm: MainViewModel) {
    val snap by vm.controlSnapshot.collectAsState()
    ControlStripContent(snap, onStop = { vm.stopAutomation() }, onDismiss = { vm.dismissControl() })
}

@Composable
internal fun ControlStripContent(snap: com.rout.aiassistant.control.ControlSnapshot, onStop: () -> Unit, onDismiss: () -> Unit) {
    if (snap.state == com.rout.aiassistant.control.ControlState.IDLE) return
    val active = snap.state in setOf(CS.PLANNING, CS.WAITING_FOR_CONFIRMATION, CS.EXECUTING, CS.VERIFYING)
    val label = when (snap.state) {
        CS.PLANNING -> "Command samajh rahi hoon"
        CS.WAITING_FOR_CONFIRMATION -> "Confirmation ka intezaar"
        CS.EXECUTING -> "Chal raha hai"
        CS.VERIFYING -> "Check kar rahi hoon"
        CS.COMPLETED -> "Ho gaya"
        CS.FAILED -> "Nahi hua"
        CS.CANCELLED -> "Roka gaya"
        else -> ""
    }
    GlassBox(radius = 18.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label + if (snap.total > 0 && active) "  ${snap.step}/${snap.total}" else "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (snap.message.isNotBlank()) Text(snap.message, color = Color(0xFFC9D2DE), fontSize = 13.sp, maxLines = 3)
                }
                Button(onClick = { if (active) onStop() else onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (active) Color(0xFFB3261E) else Color(0xFF2A2F3A), contentColor = Color.White)) {
                    Text(if (active) "STOP" else "OK")
                }
            }
            snap.log.takeLast(3).forEach { Text(it, color = Color(0xFF8D97A6), fontSize = 11.sp, maxLines = 1) }
        }
    }
}
