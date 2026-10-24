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
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 42)
        setContent {
            RoutTheme {
                val nav = rememberNavController()
                val onboarded by vm.onboarded.collectAsState()
                LaunchedEffect(onboarded) {
                    if (!onboarded) nav.navigate("onboarding") { launchSingleTop = true }
                }
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(vm, nav) }
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
