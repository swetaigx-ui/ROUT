package com.rout.aiassistant.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rout.aiassistant.BuildConfig
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.commands.MediaControl
import com.rout.aiassistant.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController, scrollToEnd: Boolean = false) {
    val scrollState = rememberScrollState()
    if (scrollToEnd) {
        androidx.compose.runtime.LaunchedEffect(Unit) { scrollState.scrollTo(scrollState.maxValue) }
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

    var backendInput by remember(backendUrl) { mutableStateOf(backendUrl) }
    var cityInput by remember(weatherCity) { mutableStateOf(weatherCity) }
    var devKeyInput by remember { mutableStateOf("") }

    ScreenScaffold(title = "Settings & Privacy", nav = nav) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("Voice & Language") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Language", color = RoutText, modifier = Modifier.weight(1f))
                    Row {
                        listOf("hi-IN" to "Hindi", "en-IN" to "English").forEach { (tag, label) ->
                            FilterChip(
                                selected = language == tag,
                                onClick = { scope.launch { vm.prefs.setLanguage(tag) } },
                                label = { Text(label) },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
                Text("Speech rate: ${"%.1f".format(speechRate)}x", color = RoutText)
                Slider(
                    value = speechRate, onValueChange = { scope.launch { vm.prefs.setSpeechRate(it) } },
                    valueRange = 0.5f..1.8f, colors = SliderDefaults.colors(thumbColor = RoutCyan, activeTrackColor = RoutCyan)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Hey ROUT session", color = RoutText)
                        Text(
                            "A foreground listening session while this screen stays on. A persistent notification " +
                            "shows ROUT is listening, and you can stop it anytime. This build has no " +
                            "background always-on wake word.",
                            color = RoutDim, style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(checked = wakeEnabled, onCheckedChange = { on ->
                        if (on) vm.startWakeSession() else vm.stopWakeSession()
                    }, colors = switchColors())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Reduce motion", color = RoutText)
                    }
                    Switch(checked = reducedMotion, onCheckedChange = { scope.launch { vm.prefs.setReducedMotion(it) } }, colors = switchColors())
                }
            }

            Section("ROUT voice") {
                val currentVoice by vm.prefs.voiceName.collectAsState(initial = "Achernar")
                Text(
                    "Natural backend voice for ROUT's spoken replies (needs a connected device). " +
                    "Device fallback voice is unchanged.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
                Text("Female", color = RoutIce, style = MaterialTheme.typography.labelSmall)
                FlowRow(Modifier.fillMaxWidth()) {
                    listOf("Achernar", "Aoede", "Despina", "Sulafat", "Zephyr").forEach { v ->
                        FilterChip(
                            selected = currentVoice == v,
                            onClick = { scope.launch { vm.prefs.setVoiceName(v) } },
                            label = { Text(v) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                Text("Male", color = RoutIce, style = MaterialTheme.typography.labelSmall)
                FlowRow(Modifier.fillMaxWidth()) {
                    listOf("Achird", "Algieba", "Charon", "Umbriel", "Sadaltager").forEach { v ->
                        FilterChip(
                            selected = currentVoice == v,
                            onClick = { scope.launch { vm.prefs.setVoiceName(v) } },
                            label = { Text(v) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            Section("ROUT AI connection") {
                Text(
                    "Recommended: deploy the ROUT backend (source included, folder backend/) and paste its URL here. " +
                    "Your AI key stays on the server - never inside the app.",
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
                    val enrolled = vm.isDeviceEnrolled()
                    Text(
                        when {
                            enrolled -> "This device is connected. Its credential is stored encrypted on this phone only."
                            enrollState == "waiting" -> "Approve the link that opened in your browser (valid 10 minutes)..."
                            enrollState == "failed" -> "Enrollment failed or expired. Try again."
                            else -> "This device is not connected yet. Enrollment creates a revocable credential for this phone only - nothing is shared or printed."
                        },
                        color = if (enrolled) RoutCyan else RoutDim, style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!enrolled) {
                            Button(
                                onClick = {
                                    vm.enrollDevice { url ->
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
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
                Divider(color = RoutPanel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Developer key mode (advanced)", color = RoutText)
                        Text(
                            "Not the recommended setup. Your own provider key is stored encrypted on this phone only. " +
                            "It is never included in the app or the source code.",
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

            Section("Weather") {
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

            Section("Floating orb") {
                Text("A movable ROUT orb over other apps. Requires the Android overlay permission. Long-press the orb to remove it.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Floating orb", color = RoutText, modifier = Modifier.weight(1f))
                    Switch(checked = orbEnabled, onCheckedChange = { on ->
                        if (on) {
                            if (Settings.canDrawOverlays(context)) vm.startOrb()
                            else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:com.rout.aiassistant")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else vm.stopOrb()
                    }, colors = switchColors())
                }
            }

            Section("Screen control") {
                Text("Lets ROUT tap a button you name, scroll, and do Back/Home/Recents when you ask. " +
                    "Screen text is read only on this device; password, OTP and payment screens are refused; " +
                    "and ROUT asks before anything that could send or post. " +
                    "To use it, enable ROUT under system Accessibility settings. Off by default." +
                    if (com.rout.aiassistant.service.RoutAccessibilityService.isEnabled) " (Currently: enabled)" else " (Currently: off)",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open accessibility settings", color = RoutCyan) }
            }

            Section("Media control") {
                Text(
                    "Play/pause and track skip use Android media sessions. This needs notification access, " +
                    "enabled by you in system settings. ROUT does not read notification contents." +
                    if (MediaControl.notificationAccessEnabled(context)) " (Currently: enabled)" else " (Currently: off)",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { MediaControl.openAccessSettings(context) }) { Text("Open notification access settings", color = RoutCyan) }
            }

            Section("Screens") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Notes" to "notes", "Tasks" to "tasks", "Memory" to "memory", "History" to "history").forEach { (label, route) ->
                        OutlinedButton(onClick = { nav.navigate(route) }) { Text(label, color = RoutCyan) }
                    }
                }
            }

            Section("Your data") {
                DataButton("Clear conversation history") { vm.clearHistory() }
                DataButton("Clear all notes") { vm.clearNotes() }
                DataButton("Clear all tasks") { vm.clearTasks() }
                DataButton("Clear saved memory") { vm.clearMemory() }
            }

            Section("About") {
                Text("ROUT ${BuildConfig.VERSION_NAME} - Personal AI Assistant", color = RoutText)
                Text("Powered by Gyan", color = RoutViolet, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Honest limits: this build has no always-on background wake word, media control needs notification access, " +
                    "AI answers need the backend you deploy, and calls/messages always ask for your confirmation first.",
                    color = RoutDim, style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    com.rout.aiassistant.ui.components.GlassBox(radius = 18.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RoutIce)
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
