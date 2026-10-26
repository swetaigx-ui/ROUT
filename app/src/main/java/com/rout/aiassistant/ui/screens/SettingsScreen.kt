package com.rout.aiassistant.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
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
import com.rout.aiassistant.ui.components.WarbyEmotion
import com.rout.aiassistant.ui.components.WarbyOrb
import com.rout.aiassistant.ui.theme.*
import com.rout.aiassistant.voice.AssistantState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController, scrollToEnd: Boolean = false, scrollFraction: Float = 0f) {
    val scrollState = rememberScrollState()
    if (scrollToEnd || scrollFraction > 0f) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { scrollState.maxValue }.first { it > 0 }
            scrollState.scrollTo(if (scrollToEnd) scrollState.maxValue else (scrollState.maxValue * scrollFraction).toInt())
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
    val warbyEmotions by vm.warbyEmotions.collectAsState()

    var yConn by remember { mutableStateOf(0f) }
    var yPersona by remember { mutableStateOf(0f) }
    var yVoice by remember { mutableStateOf(0f) }
    var yControls by remember { mutableStateOf(0f) }
    var yAppear by remember { mutableStateOf(0f) }
    var yPrivacy by remember { mutableStateOf(0f) }
    var yAbout by remember { mutableStateOf(0f) }
    var backendInput by remember(backendUrl) { mutableStateOf(backendUrl) }
    var cityInput by remember(weatherCity) { mutableStateOf(weatherCity) }
    var devKeyInput by remember { mutableStateOf("") }

    ScreenScaffold(title = "Settings", nav = nav) {
      Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero header: cat-orb identity + friendly greeting (approved concept)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                WarbyOrb(state = AssistantState.IDLE, emotion = WarbyEmotion.HAPPY, rms = 0f, reducedMotion = true, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("ROUT ko apne hisaab se set karo", color = RoutDim, fontSize = 13.sp)
                }
            }

            // --- Connection (live status) ---
            val enrolled = vm.isDeviceEnrolled()
            Section("Connection", accent = Color(0xFF4DFFA6), onY = { yConn = it }) {
                Text(
                    "Recommended: ROUT backend (source included, folder backend/). Your AI key stays on the server - never inside the app.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = backendInput, onValueChange = { backendInput = it },
                    label = { Text("Backend URL (https://...)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
                Button(onClick = { scope.launch { vm.prefs.setBackendUrl(backendInput.trim()) } },
                    colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)) { Text("Save backend URL", color = RoutBlack) }
                if (backendUrl.isNotBlank()) {
                    val enrollState by vm.enrollState.collectAsState()
                    Text(
                        when {
                            enrolled -> "This device is connected. Its credential is stored encrypted on this phone only."
                            enrollState == "waiting" -> "Approve the link that opened in your browser (valid 10 minutes)..."
                            enrollState == "failed" -> "Enrollment failed or expired. Try again."
                            else -> "This device is not connected yet. Enrollment creates a revocable credential for this phone only."
                        },
                        color = if (enrolled) RoutCyan else RoutDim, style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!enrolled) {
                            Button(
                                onClick = {
                                    vm.enrollDevice { url ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                },
                                enabled = enrollState != "waiting",
                                colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)
                            ) { Text(if (enrollState == "waiting") "Waiting for approval..." else "Connect this device", color = RoutBlack) }
                        } else {
                            OutlinedButton(onClick = { vm.revokeDevice() }) { Text("Revoke this device", color = RoutError) }
                        }
                    }
                }
                HorizontalDivider(color = RoutPanel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Developer key mode (advanced)", color = RoutText)
                        Text(
                            "Not the recommended setup. Your own provider key is stored encrypted on this phone only.",
                            color = RoutDim, style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(checked = devAiEnabled, onCheckedChange = { scope.launch { vm.prefs.setDevAiEnabled(it) } }, colors = switchColors())
                }
                if (devAiEnabled) {
                    OutlinedTextField(
                        value = devKeyInput, onValueChange = { devKeyInput = it },
                        label = { Text(if (vm.prefs.devApiKey.isNotBlank()) "Key saved - enter to replace" else "Paste your own Gemini API key") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                    )
                    Button(onClick = { if (devKeyInput.isNotBlank()) { vm.prefs.devApiKey = devKeyInput.trim(); devKeyInput = "" } },
                        colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)) { Text("Save key on device", color = RoutBlack) }
                }
            }

            // --- Persona ---
            val personaLabel = Personas.byId(currentPersona).label
            Section("ROUT persona", accent = Color(0xFFFF5CE1), trailing = if (personaLabel == "Default") "" else personaLabel, onY = { yPersona = it }) {
                Text(
                    "Mode badalta hai ROUT KAISE bolti hai aur kaunsi voice use karti hai - kya kar sakti hai wo nahi badalta. " +
                    "Pet names (babu/sona) sirf Sweetheart mein; Professional kabhi nahi.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
                FlowRow(Modifier.fillMaxWidth()) {
                    Personas.ALL.forEach { pz ->
                        FilterChip(
                            selected = currentPersona == pz.id,
                            onClick = { vm.setPersona(pz.id) },
                            label = { Text(pz.label) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            // --- Voice & language ---
            val currentVoice by vm.prefs.voiceName.collectAsState(initial = "Achernar")
            Section("ROUT voice", accent = Color(0xFFB18CFF), trailing = currentVoice, onY = { yVoice = it }) {
                Text(
                    "Natural backend voice for spoken replies (needs a connected device). Persona ka apna voice hota hai - yahan se bhi badal sakte ho.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
                Text("Female", color = RoutIce, style = MaterialTheme.typography.labelSmall)
                FlowRow(Modifier.fillMaxWidth()) {
                    listOf("Achernar", "Aoede", "Despina", "Sulafat", "Zephyr").forEach { v ->
                        FilterChip(selected = currentVoice == v, onClick = { scope.launch { vm.prefs.setVoiceName(v) } },
                            label = { Text(v) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Text("Male", color = RoutIce, style = MaterialTheme.typography.labelSmall)
                FlowRow(Modifier.fillMaxWidth()) {
                    listOf("Achird", "Algieba", "Charon", "Umbriel", "Sadaltager").forEach { v ->
                        FilterChip(selected = currentVoice == v, onClick = { scope.launch { vm.prefs.setVoiceName(v) } },
                            label = { Text(v) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Language", color = RoutText, modifier = Modifier.weight(1f))
                    Row {
                        listOf("hi-IN" to "Hindi", "en-IN" to "English").forEach { (tag, label) ->
                            FilterChip(selected = language == tag, onClick = { scope.launch { vm.prefs.setLanguage(tag) } },
                                label = { Text(label) }, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                Text("Speech rate: ${"%.1f".format(speechRate)}x", color = RoutText)
                Slider(
                    value = speechRate, onValueChange = { scope.launch { vm.prefs.setSpeechRate(it) } },
                    valueRange = 0.5f..1.8f, colors = SliderDefaults.colors(thumbColor = RoutCyan, activeTrackColor = RoutCyan)
                )
            }

            // --- Quick controls ---
            Section("Quick controls", accent = RoutCyan, onY = { yControls = it }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Hey ROUT session", color = RoutText)
                        Text("Foreground listening session with a persistent notification. No background wake word in this build.",
                            color = RoutDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = wakeEnabled, onCheckedChange = { on ->
                        if (on) vm.startWakeSession() else vm.stopWakeSession()
                    }, colors = switchColors())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Floating orb - kahin bhi baat", color = RoutText)
                        Text("Overlay permission chahiye. Orb ko long-press karke hatayo.", color = RoutDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = orbEnabled, onCheckedChange = { on ->
                        if (on) {
                            if (Settings.canDrawOverlays(context)) vm.startOrb()
                            else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else vm.stopOrb()
                    }, colors = switchColors())
                }
                val batteryAlert by vm.batteryAlert.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Battery full/low alert", color = RoutText)
                        Text("Speaks when the battery is full or low while the app is open.", color = RoutDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = batteryAlert, onCheckedChange = { scope.launch { vm.prefs.setBatteryAlert(it) } }, colors = switchColors())
                }
                val flipSilent by vm.flipSilent.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Flip phone to silent", color = RoutText)
                        Text("Face-down rakhte hi silent, app khuli hone par.", color = RoutDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = flipSilent, onCheckedChange = { scope.launch { vm.prefs.setFlipSilent(it) } }, colors = switchColors())
                }
                val bedtime by vm.bedtime.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bedtime mode (DND)", color = RoutText)
                        Text(
                            if (bedtime != null) "Nightly DND at ${bedtime!!.first}:${"%02d".format(bedtime!!.second)}. Say 'bedtime off' to stop."
                            else "Say 'bedtime 11 baje' to turn on Do Not Disturb every night.",
                            color = RoutDim, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (bedtime != null) {
                        Text("Off", color = RoutCyan, modifier = Modifier.clickable {
                            scope.launch {
                                vm.prefs.clearBedtime()
                                com.rout.aiassistant.commands.DeviceActions.cancelBedtime(context)
                            }
                        })
                    }
                }
                val translateMode by vm.translateMode.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live translate mode", color = RoutText)
                        Text("Hindi <-> English translation, spoken back. Say 'translate mode off' anytime.",
                            color = RoutDim, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = translateMode, onCheckedChange = { scope.launch { vm.prefs.setTranslateMode(it) } }, colors = switchColors())
                }
            }

            // --- Appearance ---
            Section("Appearance", accent = Color(0xFFB18CFF), onY = { yAppear = it }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Cat orb emotions (Warby)", color = RoutText)
                        Text("Warby reacts to your words. Off karne pe sirf state dikhata hai.", color = RoutDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = warbyEmotions, onCheckedChange = { scope.launch { vm.prefs.setWarbyEmotions(it) } }, colors = switchColors())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reduce motion", color = RoutText, modifier = Modifier.weight(1f))
                    Switch(checked = reducedMotion, onCheckedChange = { scope.launch { vm.prefs.setReducedMotion(it) } }, colors = switchColors())
                }
            }

            // --- Permissions & privacy (live grant status) ---
            Section("Permissions and privacy", accent = Color(0xFFFFB84D), onY = { yPrivacy = it }) {
                fun granted(perm: String) = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                val micOk = granted(android.Manifest.permission.RECORD_AUDIO)
                val contactsOk = granted(android.Manifest.permission.READ_CONTACTS)
                val locOk = granted(android.Manifest.permission.ACCESS_FINE_LOCATION)
                PermRow("Mic", micOk) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                PermRow("Contacts", contactsOk) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                PermRow("Location", locOk) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                PermRow("Overlay (floating orb)", Settings.canDrawOverlays(context)) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                PermRow("Notification access (media control)", MediaControl.notificationAccessEnabled(context)) { MediaControl.openAccessSettings(context) }
                PermRow("Accessibility (screen control)", RoutAccessibilityService.isEnabled) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                Text(
                    "Screen text is read only on this device; password, OTP and payment screens are refused; ROUT asks before anything that could send or post.",
                    color = RoutDim, style = MaterialTheme.typography.labelSmall
                )
                HorizontalDivider(color = RoutPanel)
                OutlinedButton(onClick = {
                    scope.launch {
                        val text = vm.exportMemoryText()
                        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                        val f = java.io.File(dir, "rout-memory.txt").apply { writeText(text) }
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.rout.aiassistant.fileprovider", f)
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(share, "Export ROUT memory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Export my data (memory)", color = RoutCyan) }
                DataButton("Clear conversation history") { vm.clearHistory() }
                DataButton("Clear all notes") { vm.clearNotes() }
                DataButton("Clear all tasks") { vm.clearTasks() }
                DataButton("Clear saved memory") { vm.clearMemory() }
            }

            // --- Routines ---
            Section("Routines", accent = Color(0xFF4DFFA6)) {
                val routineList by vm.routines.collectAsState()
                Text(
                    "One phrase runs many commands. Say the trigger (e.g. \"good morning\") and ROUT runs every step in order.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
                routineList.forEach { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.trigger, color = RoutText, style = MaterialTheme.typography.titleMedium)
                            Text(r.steps.replace("\n", "  -  "), color = RoutDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        Switch(checked = r.enabled, onCheckedChange = { vm.setRoutineEnabled(r.id, it) }, colors = switchColors())
                        Text(
                            "Delete", color = RoutDim, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 8.dp).clickable { vm.deleteRoutine(r.id) }
                        )
                    }
                }
                var newTrigger by remember { mutableStateOf("") }
                var newSteps by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = newTrigger, onValueChange = { newTrigger = it },
                    label = { Text("Trigger phrase (e.g. gym time)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newSteps, onValueChange = { newSteps = it },
                    label = { Text("Steps, one per line (e.g. weather)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val steps = newSteps.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    if (newTrigger.isNotBlank() && steps.isNotEmpty()) {
                        vm.addRoutine(newTrigger, steps)
                        newTrigger = ""; newSteps = ""
                    }
                }) { Text("Add routine") }
            }

            // --- Weather ---
            Section("Weather", accent = RoutIce) {
                Text("Uses Open-Meteo (free, no key). With location permission ROUT uses your position; otherwise your city.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = cityInput, onValueChange = { cityInput = it },
                    label = { Text("City (used when location is off)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
                Button(onClick = { scope.launch { vm.prefs.setWeatherCity(cityInput.trim()) } },
                    colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)) { Text("Save city", color = RoutBlack) }
            }

            // --- Screens ---
            Section("Screens", accent = RoutIce) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Notes" to "notes", "Tasks" to "tasks", "Memory" to "memory", "History" to "history").forEach { (label, route) ->
                        OutlinedButton(onClick = { nav.navigate(route) }) { Text(label, color = RoutCyan) }
                    }
                }
            }

            // --- About ---
            Section("About ROUT", accent = RoutIce, trailing = BuildConfig.VERSION_NAME, onY = { yAbout = it }) {
                PermRow("Version", true, valueText = BuildConfig.VERSION_NAME) {}
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/swetaigx-ui/ROUT")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, modifier = Modifier.fillMaxWidth()) { Text("Source code (GitHub)", color = RoutCyan) }
                OutlinedButton(onClick = {
                    vm.onUserText("tum kya kya kar sakte ho", fromVoice = false)
                    nav.navigate("home")
                }, modifier = Modifier.fillMaxWidth()) { Text("Help and commands list", color = RoutCyan) }
                Text(
                    "Honest limits: no always-on background wake word; media control needs notification access; " +
                    "AI answers need the connected backend; calls/messages always ask for your confirmation first.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
            }

            Text("ROUT AI - POWERED BY GYAN", color = RoutViolet, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
        }
        // Floating section navigation (icons jump to the section; replaces the old status header).
        val navItems: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Float>> = listOf(
            Triple(Icons.Default.Share, "Connection", yConn),
            Triple(Icons.Default.Face, "Persona", yPersona),
            Triple(Icons.Default.Phone, "Voice", yVoice),
            Triple(Icons.Default.Build, "Controls", yControls),
            Triple(Icons.Default.Star, "Appearance", yAppear),
            Triple(Icons.Default.Lock, "Privacy", yPrivacy),
            Triple(Icons.Default.Info, "About", yAbout)
        )
        com.rout.aiassistant.ui.components.GlassBox(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
            radius = 28.dp,
            fill = Color(0xF2101A2B)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                navItems.forEach { (icon, label, y) ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .clickable { scope.launch { scrollState.animateScrollTo(y.toInt()) } }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, label, tint = RoutCyan, modifier = Modifier.size(22.dp))
                        Text(label, color = RoutIce, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
      }
    }
}

@Composable
fun PermRow(label: String, granted: Boolean, valueText: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = RoutText, modifier = Modifier.weight(1f))
        Text(
            valueText ?: if (granted) "Granted" else "Manage",
            color = if (valueText != null || granted) RoutCyan else Color(0xFFFFB84D),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun Section(title: String, accent: Color = RoutIce, trailing: String = "", onY: (Float) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    com.rout.aiassistant.ui.components.GlassBox(radius = 18.dp, modifier = Modifier.fillMaxWidth().onGloballyPositioned { onY(it.positionInParent().y) }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp, 20.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = RoutIce, modifier = Modifier.weight(1f))
                if (trailing.isNotBlank()) Text(trailing, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
fun DataButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, color = RoutError) }
}

@Composable
fun switchColors() = SwitchDefaults.colors(checkedTrackColor = RoutCyan, checkedThumbColor = RoutBlack)
