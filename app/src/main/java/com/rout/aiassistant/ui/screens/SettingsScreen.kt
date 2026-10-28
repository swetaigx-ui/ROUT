package com.rout.aiassistant.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rout.aiassistant.BuildConfig
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.ai.Personas
import com.rout.aiassistant.commands.MediaControl
import com.rout.aiassistant.service.RoutAccessibilityService
import com.rout.aiassistant.ui.components.GlassBox
import com.rout.aiassistant.ui.components.RefIcons
import com.rout.aiassistant.ui.components.RgbFlowText
import com.rout.aiassistant.ui.components.RoutBackground
import com.rout.aiassistant.ui.components.RoutDock
import com.rout.aiassistant.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val RefInk = Color(0xFFEEECF8)
private val RefMuted = Color(0xFFA3A0BD)
private val RefDim = Color(0xFF7F7B99)
private val RefAccent = Color(0xFF67F0FF)
private val RefOk = Color(0xFF6FF0B6)
private val RefDanger = Color(0xFFFF9DB5)
private val RefCardBg = Color(0xC2161328)
private val RefCardLine = Color(0x17FFFFFF)

/**
 * Settings, ditto of Gyan's reference page (file-ba01d872.html, 2026-10-27):
 * sticky glass topbar + section subbar with a jump dropdown, dark glass cards,
 * pill chips with a violet-cyan selected gradient, green-to-cyan switches,
 * gradient primary buttons, and the flowing-RGB "Powered by Gyan" credit.
 * Every control below is wired to a REAL preference or action - nothing is a demo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController, scrollToEnd: Boolean = false, scrollFraction: Float = 0f) {
    val scrollState = rememberScrollState()
    if (scrollToEnd || scrollFraction > 0f) {
        LaunchedEffect(Unit) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
            repeat(if (scrollToEnd) 20 else 1) {
                scrollState.scrollTo(if (scrollToEnd) scrollState.maxValue else (scrollState.maxValue * scrollFraction).toInt())
                if (scrollToEnd) kotlinx.coroutines.delay(150)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val language by vm.language.collectAsState()
    val speechRate by vm.speechRate.collectAsState()
    val wakeEnabled by vm.wakeEnabled.collectAsState()
    val orbEnabled by vm.orbEnabled.collectAsState()
    val backendUrl by vm.backendUrl.collectAsState()
    val weatherCity by vm.weatherCity.collectAsState()
    val reducedMotion by vm.reducedMotion.collectAsState()
    val devAiEnabled by vm.devAiEnabled.collectAsState()
    val currentPersona by vm.personaMode.collectAsState()

    val sectionOffsets = remember { mutableStateMapOf<String, Float>() }
    val sectionAnchorPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    val currentSection by remember {
        derivedStateOf {
            // Fully at the bottom -> the last section owns the view.
            if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 8) return@derivedStateOf "About ROUT"
            val s = scrollState.value + sectionAnchorPx
            sectionOffsets.entries.filter { it.value <= s }.maxByOrNull { it.value }?.key ?: "Naam"
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var backendInput by remember(backendUrl) { mutableStateOf(backendUrl) }
    var cityInput by remember(weatherCity) { mutableStateOf(weatherCity) }
    var devKeyInput by remember { mutableStateOf("") }
    val userNameSaved by vm.userName.collectAsState()
    var nameInput by remember(userNameSaved) { mutableStateOf(userNameSaved) }

    fun goHome() = nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true }

    RoutBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {

            // ---- Sticky glass header: topbar + section subbar ----
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassBox(Modifier.fillMaxWidth(), radius = 24.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Settings", color = RefInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).sp)
                            Text("ROUT \u00B7 v${BuildConfig.VERSION_NAME}", color = RefDim, fontSize = 12.sp)
                        }
                        RefIconButton(RefIcons.X, "Close settings") { goHome() }
                    }
                }
                GlassBox(Modifier.fillMaxWidth(), radius = 24.dp) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 9.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Section", color = RefDim, fontSize = 11.sp)
                            Text(currentSection, color = RefInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box {
                            RefIconButton(RefIcons.List, "Show all sections", active = menuOpen) { menuOpen = !menuOpen }
                            DropdownMenu(
                                expanded = menuOpen, onDismissRequest = { menuOpen = false },
                                containerColor = Color(0xF7100D1F),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                            ) {
                                listOf(
                                    "Naam" to RefIcons.User, "Assistant ka naam" to RefIcons.Mic, "Connection" to RefIcons.Link,
                                    "ROUT persona" to RefIcons.Spark, "ROUT voice" to RefIcons.Mic,
                                    "Quick controls" to RefIcons.Zap, "Permissions and privacy" to RefIcons.Shield,
                                    "Routines" to RefIcons.Repeat, "Weather" to RefIcons.Cloud,
                                    "Screens" to RefIcons.Layers, "About ROUT" to RefIcons.Info
                                ).forEach { (title, icon) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(icon, null, tint = RefAccent, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Text(title, color = RefInk, fontSize = 14.sp)
                                            }
                                        },
                                        onClick = {
                                            menuOpen = false
                                            val y = sectionOffsets[title] ?: return@DropdownMenuItem
                                            scope.launch { scrollState.animateScrollTo((y - 8f).coerceAtLeast(0f).toInt()) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Scrollable sections ----
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Naam ---
                SettingsCard("Naam", onY = { sectionOffsets["Naam"] = it }) {
                    RefField(nameInput, { nameInput = it.take(30); vm.setUserName(it.take(30)) }, "Aapka naam (optional)")
                }

                SettingsCard("Assistant ka naam", onY = { sectionOffsets["Assistant ka naam"] = it }) {
                    val savedAssistant by vm.assistantName.collectAsState()
                    var assistantInput by remember(savedAssistant) { mutableStateOf(savedAssistant) }
                    var assistantMsg by remember { mutableStateOf("") }
                    val wakeBlockedMsg by vm.wakeBlocked.collectAsState()
                    RefField(assistantInput, { assistantInput = it.take(20); assistantMsg = "" }, "Assistant ka naam (khali = ROUT)")
                    RefPrimaryButton("Naam save karo") {
                        scope.launch {
                            val ok = vm.setAssistantName(assistantInput)
                            assistantMsg = if (ok) "" else "Ye naam nahi chalega (kam se kam 2 akshar, 'hey' jaisa shabd nahi). Pehle wala naam wahi hai."
                        }
                    }
                    if (savedAssistant.isNotBlank()) {
                        RefPrimaryButton("Naam hatao (ROUT par wapas)") { scope.launch { vm.setAssistantName(""); assistantMsg = "" } }
                    }
                    Text(
                        (if (assistantMsg.isNotBlank()) assistantMsg + "\n" else "") +
                            (if (wakeBlockedMsg.isNotBlank()) wakeBlockedMsg + "\n" else "") +
                            (if (savedAssistant.isBlank()) "Abhi: ROUT. " else "Saved: $savedAssistant. ") +
                            "Hey ROUT listening session mein sirf yahi naam bulane par ROUT sunega. LIVE mein phone ke kaam (call/message/app) bhi sirf is naam se chalenge, par LIVE chalu ho to AI saari awaaz sunta hai, naam se nahi rukta. Mic apne aap ON nahi hota. Awaaz pehchaanne mein galti ho sakti hai.",
                        color = RefDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // --- Connection (live status) ---
                val enrolled = vm.isDeviceEnrolled()
                val enrollState by vm.enrollState.collectAsState()
                SettingsCard("Connection", onY = { sectionOffsets["Connection"] = it }) {
                    Text(
                        "Recommended: ROUT backend (source included, folder backend/). Your AI key stays on the server - never inside the app.",
                        color = RefMuted, fontSize = 13.sp, lineHeight = 20.sp
                    )
                    RefField(backendInput, { backendInput = it }, "Backend URL (https://...)")
                    RefPrimaryButton("Save backend URL") { scope.launch { vm.prefs.setBackendUrl(backendInput.trim()) } }
                    if (backendUrl.isNotBlank()) {
                        Text(
                            when {
                                enrolled -> "This device is connected. Its credential is stored encrypted on this phone only."
                                enrollState == "waiting" -> "Approve the link that opened in your browser (valid 10 minutes)..."
                                enrollState == "failed" -> "Enrollment failed or expired. Try again."
                                else -> "This device is not connected yet. Enrollment creates a revocable credential for this phone only."
                            },
                            color = if (enrolled) RefAccent else RefMuted, fontSize = 13.sp, lineHeight = 20.sp
                        )
                        if (!enrolled) {
                            RefPrimaryButton(if (enrollState == "waiting") "Waiting for approval..." else "Connect this device", enabled = enrollState != "waiting") {
                                vm.enrollDevice { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        } else {
                            RefDangerButton("Revoke this device") { vm.revokeDevice() }
                        }
                    }
                    HorizontalDivider(color = Color(0x0FFFFFFF))
                    RefSwitchRow(
                        "Developer key mode (advanced)",
                        "Not the recommended setup. Your own provider key is stored encrypted on this phone only.",
                        devAiEnabled
                    ) { scope.launch { vm.prefs.setDevAiEnabled(it) } }
                    if (devAiEnabled) {
                        RefField(devKeyInput, { devKeyInput = it }, if (vm.prefs.devApiKey.isNotBlank()) "Key saved - enter to replace" else "Paste your own Gemini API key")
                        RefPrimaryButton("Save key on device") {
                            if (devKeyInput.isNotBlank()) { vm.prefs.devApiKey = devKeyInput.trim(); devKeyInput = "" }
                        }
                    }
                }

                // --- ROUT persona ---
                val personaLabel = Personas.byId(currentPersona).label
                SettingsCard("ROUT persona", trailing = personaLabel, onY = { sectionOffsets["ROUT persona"] = it }) {
                    Text(
                        "Mode badalta hai ROUT KAISE bolti hai aur kaunsi voice use karti hai - kya kar sakti hai wo nahi badalta. Pet names (babu/sona) sirf Sweetheart mein; Professional kabhi nahi.",
                        color = RefMuted, fontSize = 13.sp, lineHeight = 20.sp
                    )
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Personas.ALL.forEach { pz ->
                            RefChip(pz.label, selected = currentPersona == pz.id) { vm.setPersona(pz.id) }
                        }
                    }
                }

                // --- ROUT voice ---
                val currentVoice by vm.prefs.voiceName.collectAsState(initial = "Achernar")
                val deviceVoiceOn by vm.deviceVoiceEnabled.collectAsState()
                SettingsCard("ROUT voice", trailing = currentVoice, onY = { sectionOffsets["ROUT voice"] = it }) {
                    Text(
                        "Natural backend voice for spoken replies (needs a connected device). Persona ka apna voice hota hai - yahan se bhi badal sakte ho.",
                        color = RefMuted, fontSize = 13.sp, lineHeight = 20.sp
                    )
                    RefSwitchRow(
                        "Device voice (robotic)",
                        "Off: natural voice na mile to kuch nahi bajta, jawab text mein milta hai. On karne par robotic device voice bhi bol sakti hai.",
                        deviceVoiceOn
                    ) { scope.launch { vm.prefs.setDeviceVoiceEnabled(it) } }
                    Text("Female", color = RefDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Achernar", "Aoede", "Despina", "Sulafat", "Zephyr").forEach { v ->
                            RefChip(v, selected = currentVoice == v) { scope.launch { vm.prefs.setVoiceName(v) } }
                        }
                    }
                    Text("Male", color = RefDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Achird", "Algieba", "Charon", "Umbriel", "Sadaltager").forEach { v ->
                            RefChip(v, selected = currentVoice == v) { scope.launch { vm.prefs.setVoiceName(v) } }
                        }
                    }
                    Text("Language", color = RefDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("hi-IN" to "Hindi", "en-IN" to "English").forEach { (tag, label) ->
                            RefChip(label, selected = language == tag) { scope.launch { vm.prefs.setLanguage(tag) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Speech rate", color = RefInk, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("${"%.1f".format(speechRate)}x", color = RefAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = speechRate, onValueChange = { scope.launch { vm.prefs.setSpeechRate(it) } },
                        valueRange = 0.5f..1.8f, colors = SliderDefaults.colors(thumbColor = RefAccent, activeTrackColor = RefAccent, inactiveTrackColor = Color(0x24FFFFFF))
                    )
                }

                // --- Quick controls ---
                SettingsCard("Quick controls", onY = { sectionOffsets["Quick controls"] = it }) {
                    RefSwitchRow(
                        "Hey ROUT session",
                        "Foreground listening session with a persistent notification. No background wake word in this build. Naam save ho aur sirf naam bolo to LIVE shuru hota hai: tab mic ki awaaz AI ko jaati hai, 20 sec chup rehne par (ya 5 min baad) LIVE band hokar naam-sunna wapas aata hai; STOP/X dabane par sab band. " + (if (vm.onDeviceRecognitionAvailable && android.os.Build.VERSION.SDK_INT >= 31) "Naam pehchaan phone par (on-device) hoga jab tak phone iski language support kare; naam save ho to on-device na chale to session band rahega." else "Is phone par system recognizer use hota hai: awaaz cloud tak ja sakti hai."),
                        wakeEnabled
                    ) { on -> if (on) vm.startWakeSession() else vm.stopWakeSession() }
                    RefSwitchRow(
                        "Floating orb - kahin bhi baat",
                        "Overlay permission chahiye. Orb ko long-press karke hatayo.",
                        orbEnabled
                    ) { on ->
                        if (on) {
                            if (Settings.canDrawOverlays(context)) vm.startOrb()
                            else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else vm.stopOrb()
                    }
                    val batteryAlert by vm.batteryAlert.collectAsState()
                    RefSwitchRow("Battery full/low alert", "Speaks when the battery is full or low while the app is open.", batteryAlert) {
                        scope.launch { vm.prefs.setBatteryAlert(it) }
                    }
                    val flipSilent by vm.flipSilent.collectAsState()
                    RefSwitchRow("Flip phone to silent", "Face-down rakhte hi silent, app khuli hone par.", flipSilent) {
                        scope.launch { vm.prefs.setFlipSilent(it) }
                    }
                    val bedtime by vm.bedtime.collectAsState()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bedtime mode (DND)", color = RefInk, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (bedtime != null) "Nightly DND at ${bedtime!!.first}:${"%02d".format(bedtime!!.second)}. Say 'bedtime off' to stop."
                                else "Say 'bedtime 11 baje' to turn on Do Not Disturb every night.",
                                color = RefDim, fontSize = 12.sp, lineHeight = 17.sp
                            )
                        }
                        if (bedtime != null) {
                            Text("Off", color = RefAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        vm.prefs.clearBedtime()
                                        com.rout.aiassistant.commands.DeviceActions.cancelBedtime(context)
                                    }
                                }.padding(6.dp))
                        }
                    }
                    val translateMode by vm.translateMode.collectAsState()
                    RefSwitchRow("Live translate mode", "Hindi <-> English translation, spoken back. Say 'translate mode off' anytime.", translateMode) {
                        scope.launch { vm.prefs.setTranslateMode(it) }
                    }
                    RefSwitchRow("Reduce motion", "Animations dheemi - kam resources, kam dhyaan.", reducedMotion) {
                        scope.launch { vm.prefs.setReducedMotion(it) }
                    }
                }

                // --- Permissions and privacy (live grant status) ---
                SettingsCard("Permissions and privacy", onY = { sectionOffsets["Permissions and privacy"] = it }) {
                    fun granted(perm: String) = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                    RefPermRow("Mic", granted(android.Manifest.permission.RECORD_AUDIO)) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    RefPermRow("Contacts", granted(android.Manifest.permission.READ_CONTACTS)) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    RefPermRow("Location", granted(android.Manifest.permission.ACCESS_FINE_LOCATION)) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    RefPermRow("Overlay (floating orb)", Settings.canDrawOverlays(context)) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    RefPermRow("Notification access (media control)", MediaControl.notificationAccessEnabled(context)) { MediaControl.openAccessSettings(context) }
                    RefPermRow("Accessibility (screen control)", RoutAccessibilityService.isEnabled) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    Text(
                        "Screen text is read only on this device; password, OTP and payment screens are refused; ROUT asks before anything that could send or post.",
                        color = RefDim, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    RefAccentButton("Export my data (memory)") {
                        scope.launch {
                            val text = vm.exportMemoryText()
                            val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                            val f = java.io.File(dir, "rout-memory.txt").apply { writeText(text) }
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.rout.aiassistant.fileprovider", f)
                            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(share, "Export ROUT memory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    RefDangerButton("Clear conversation history") { vm.clearHistory() }
                    RefDangerButton("Clear all notes") { vm.clearNotes() }
                    RefDangerButton("Clear all tasks") { vm.clearTasks() }
                    RefDangerButton("Clear saved memory") { vm.clearMemory() }
                }

                // --- Routines ---
                SettingsCard("Routines", onY = { sectionOffsets["Routines"] = it }) {
                    val routineList by vm.routines.collectAsState()
                    Text(
                        "One phrase runs many commands. Say the trigger (e.g. \"good morning\") and ROUT runs every step in order.",
                        color = RefMuted, fontSize = 13.sp, lineHeight = 20.sp
                    )
                    routineList.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x09FFFFFF)).border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.trigger, color = RefInk, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(r.steps.replace("\n", "  -  "), color = RefDim, fontSize = 12.sp, maxLines = 2)
                            }
                            RefSwitch(r.enabled) { vm.setRoutineEnabled(r.id, it) }
                            Text(
                                "Delete", color = RefDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp).clickable { vm.deleteRoutine(r.id) }
                            )
                        }
                    }
                    var newTrigger by remember { mutableStateOf("") }
                    var newSteps by remember { mutableStateOf("") }
                    RefField(newTrigger, { newTrigger = it }, "Trigger phrase (e.g. gym time)")
                    RefField(newSteps, { newSteps = it }, "Steps, one per line (e.g. weather)", singleLine = false)
                    RefPrimaryButton("Add routine") {
                        val steps = newSteps.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        if (newTrigger.isNotBlank() && steps.isNotEmpty()) {
                            vm.addRoutine(newTrigger, steps)
                            newTrigger = ""; newSteps = ""
                        }
                    }
                }

                // --- Weather ---
                SettingsCard("Weather", onY = { sectionOffsets["Weather"] = it }) {
                    Text("Uses Open-Meteo (free, no key).", color = RefMuted, fontSize = 13.sp, lineHeight = 20.sp)
                    RefField(cityInput, { cityInput = it }, "City (used when location is off)")
                    RefPrimaryButton("Save city") { scope.launch { vm.prefs.setWeatherCity(cityInput.trim()) } }
                }

                // --- Screens ---
                SettingsCard("Screens", onY = { sectionOffsets["Screens"] = it }) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Notes" to "notes", "Tasks" to "tasks", "Memory" to "memory", "History" to "history").forEach { (label, route) ->
                            RefChip(label, selected = false) { nav.navigate(route) }
                        }
                    }
                }

                // --- About ROUT ---
                SettingsCard("About ROUT", trailing = BuildConfig.VERSION_NAME, onY = { sectionOffsets["About ROUT"] = it }) {
                    RefGhostButton("Source code (GitHub)") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/swetaigx-ui/ROUT")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    RefGhostButton("Help and commands list") {
                        vm.onUserText("tum kya kya kar sakte ho", fromVoice = false)
                        goHome()
                    }
                    Text(
                        "Honest limits: no always-on background wake word; media control needs notification access; AI answers need the connected backend; calls and messages always ask for your confirmation first.",
                        color = RefDim, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }

                // ---- Credit: flowing RGB glow INSIDE the letters ----
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("ROUT AI \u00B7 ", color = RefDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp)
                    RgbFlowText("POWERED BY GYAN", reducedMotion = reducedMotion)
                }
                Spacer(Modifier.height(120.dp))
            }

            // ---- Dock ----
            RoutDock("settings", nav, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp, start = 16.dp, end = 16.dp))
        }
    }
}

// ---------- Reference-styled building blocks ----------

@Composable
fun SettingsCard(title: String, trailing: String = "", onY: (Float) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { onY(it.positionInParent().y) }
            .clip(RoundedCornerShape(22.dp))
            .background(RefCardBg)
            .border(1.dp, RefCardLine, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = RefInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (trailing.isNotBlank() && trailing != "Default") Text(trailing, color = RefAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
fun RefIconButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (active) Color(0x2467F0FF) else Color(0x12FFFFFF))
            .border(1.dp, if (active) Color(0x8067F0FF) else Color(0x29FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = if (active) RefAccent else Color(0xFFC9D2DE), modifier = Modifier.size(18.dp)) }
}

@Composable
fun RefChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.linearGradient(listOf(Color(0x998B5CF6), Color(0x4267F0FF)))
                else Brush.linearGradient(listOf(Color(0x0AFFFFFF), Color(0x0AFFFFFF)))
            )
            .border(1.dp, if (selected) Color(0xA6BEAAFF) else Color(0x1FFFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else Color(0xFFCFCBE6), fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun RefSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 0.dp, tween(250), label = "sw")
    Box(
        Modifier.width(46.dp).height(28.dp).clip(RoundedCornerShape(50))
            .background(
                if (checked) Brush.horizontalGradient(listOf(Color(0xFF4EE8A0), Color(0xFF67F0FF)))
                else Brush.horizontalGradient(listOf(Color(0x24FFFFFF), Color(0x24FFFFFF)))
            )
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.offset(x = thumbOffset).size(22.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun RefSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, color = RefInk, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = RefDim, fontSize = 12.sp, lineHeight = 17.sp)
        }
        RefSwitch(checked, onCheckedChange)
    }
}

@Composable
fun RefPermRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RefInk, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        Text(
            if (granted) "Granted" else "Manage",
            color = if (granted) RefOk else Color(0xFFFFB84D),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RefField(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) }, singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0x9967F0FF), unfocusedBorderColor = Color(0x1FFFFFFF),
            focusedContainerColor = Color(0x0AFFFFFF), unfocusedContainerColor = Color(0x0AFFFFFF),
            focusedTextColor = RefInk, unfocusedTextColor = RefInk,
            focusedLabelColor = RefDim, unfocusedLabelColor = RefDim
        )
    )
}

@Composable
fun RefPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Brush.linearGradient(listOf(Color(0xFF7FF3FF), Color(0xFF8FA6FF))) else Brush.linearGradient(listOf(Color(0x557FF3FF), Color(0x558FA6FF))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color(0xFF061019), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun RefDangerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x61FF82A0), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = RefDanger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun RefAccentButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x6B67F0FF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = RefAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun RefGhostButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = RefInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
