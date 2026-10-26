package com.rout.aiassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rout.aiassistant.ui.screens.*
import com.rout.aiassistant.ui.theme.RoutTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // --- Battery full/low voice alert: works while the ROUT app is open (honest scope). ---
    private var lastBatteryBucket = -1
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: android.content.Intent) {
            if (!vm.batteryAlert.value) return
            val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            val status = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val bucket = when { pct >= 99 && charging -> 2; pct <= 20 && !charging -> 1; else -> 0 }
            if (bucket != 0 && bucket != lastBatteryBucket) {
                vm.engine.speak(if (bucket == 2) "Battery full ho gayi, charger hata sakte ho."
                    else "Battery low hai, $pct percent bacha hai. Charger laga lo.")
            }
            lastBatteryBucket = bucket
        }
    }

    // --- Flip-to-silent: accelerometer, while the ROUT app is open (honest scope). ---
    private var faceDownSince = 0L
    private var silencedByFlip = false
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager }
    private val accelListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            if (!vm.flipSilent.value) { faceDownSince = 0; return }
            val z = e.values[2]
            if (z < -8.5f) {
                if (faceDownSince == 0L) faceDownSince = System.currentTimeMillis()
                if (!silencedByFlip && System.currentTimeMillis() - faceDownSince > 900) {
                    silencedByFlip = true
                    com.rout.aiassistant.commands.DeviceActions.setRingerMode(this@MainActivity, "silent")
                    vm.engine.speak("Phone ulti hai - silent kar diya.")
                }
            } else if (z > 0) {
                faceDownSince = 0
                silencedByFlip = false
            }
        }
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
    }

    override fun onResume() {
        super.onResume()
        RoutApplication.foreground = true
        registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(accelListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        RoutApplication.foreground = false
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        sensorManager.unregisterListener(accelListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_CALENDAR)
            needed.add(Manifest.permission.READ_CALENDAR)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_CALL_LOG)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
        intent?.getStringExtra("run_command")?.takeIf { it.isNotBlank() }?.let { cmd ->
            // Tapped a scheduled briefing notification - run its command once the UI is up.
            window.decorView.post { vm.onUserText(cmd, fromVoice = false) }
        }
        if (intent?.getBooleanExtra("tile_voice", false) == true) {
            // Quick Settings tile: straight into listening.
            window.decorView.post { vm.onMicTap() }
        }
        handleShareIntent(intent)
        setContent {
            RoutTheme {
                val nav = rememberNavController()
                val onboarded by vm.onboarded.collectAsState()
                LaunchedEffect(onboarded) {
                    if (!onboarded) nav.navigate("onboarding") { launchSingleTop = true }
                }
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(vm, nav) }
                    composable("chat") { com.rout.aiassistant.ui.screens.ChatScreen(vm, nav) }
                    composable("onboarding") { OnboardingScreen(vm, nav) }
                    composable("notes") { NotesScreen(vm, nav) }
                    composable("tasks") { TasksScreen(vm, nav) }
                    composable("memory") { MemoryScreen(vm, nav) }
                    composable("history") { HistoryScreen(vm, nav) }
                    composable("settings") { SettingsScreen(vm, nav) }
                }
            }
        }
    }

    // Bug 3: a command paused on a runtime permission resumes by itself after the grant.
    private val permLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.any { it.value }) window.decorView.post { vm.onPermissionsResult() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("tile_voice", false)) {
            window.decorView.post { vm.onMicTap() }
        }
        handleShareIntent(intent)
    }

    /** Explicit share of a photo or PDF into ROUT (share sheet -> "ROUT"). */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: android.net.Uri? = if (android.os.Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) window.decorView.post { vm.handleShared(uri, intent.type) }
    }

    override fun onStop() {
        super.onStop()
        // Honest lifecycle: stop foreground listening when the app leaves the screen,
        // unless the user explicitly enabled the Hey ROUT foreground session.
        if (vm.assistantState.value == com.rout.aiassistant.voice.AssistantState.LISTENING &&
            !vm.wakeEnabled.value
        ) vm.engine.stopListening()
        vm.engine.stopSpeaking()
    }
}
