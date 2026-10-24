package com.rout.aiassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(vm: MainViewModel, nav: NavController) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        Triple("Namaste, main ROUT hoon",
            "Your personal AI assistant - Hindi, Hinglish aur English mein.\n\n" +
            "Open apps, search the web, control torch and volume, save notes and tasks, " +
            "set reminders, check real weather, and more - many features work fully offline on your phone.", null),
        Triple("Permissions, clearly explained",
            "Microphone: only when you tap the mic or enable a Hey ROUT session.\n" +
            "Notifications: for reminders and the listening indicator.\n" +
            "Contacts: only when you ask ROUT to call or message someone.\n" +
            "Overlay: only if you enable the floating orb.\n\n" +
            "ROUT asks before every call or message, and never records audio secretly.", null),
        Triple("Your data stays yours",
            "Notes, tasks and memory live on your phone. You can export or delete everything in Settings. " +
            "AI answers require a backend you configure - until then, AI features show an honest setup-required state " +
            "instead of fake answers.", "Start using ROUT")
    )
    Column(
        Modifier.fillMaxSize().background(RoutBlack).padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("ROUT", style = MaterialTheme.typography.headlineLarge, color = RoutCyan)
        Column {
            Text(pages[page].first, style = MaterialTheme.typography.titleLarge, color = RoutText)
            Spacer(Modifier.height(16.dp))
            Text(pages[page].second, style = MaterialTheme.typography.bodyLarge, color = RoutDim)
        }
        Column {
            if (page < pages.size - 1) {
                Button(
                    onClick = { page++ }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)
                ) { Text("Next", color = RoutBlack) }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            vm.prefs.setOnboarded(true)
                            nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)
                ) { Text(pages[page].third ?: "Start", color = RoutBlack) }
            }
            Spacer(Modifier.height(8.dp))
            Text("${page + 1} / ${pages.size}", color = RoutDim, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
