package com.rout.aiassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ui.components.OrbView
import com.rout.aiassistant.ui.theme.RoutBlack
import com.rout.aiassistant.ui.theme.RoutTheme
import com.rout.aiassistant.voice.AssistantState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun savePng(name: String, heightPx: Int = 2340) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val specW = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
            val specH = android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
            decor.measure(specW, specH)
            decor.layout(0, 0, 1080, heightPx)
            val bmp = Bitmap.createBitmap(1080, heightPx, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun orbStates() {
        composeRule.setContent {
            RoutTheme {
                Column(
                    Modifier.fillMaxSize().background(RoutBlack).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrbView(state = AssistantState.IDLE, rms = 0f, reducedMotion = true, size = 150.dp)
                        OrbView(state = AssistantState.LISTENING, rms = 6f, reducedMotion = true, size = 150.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrbView(state = AssistantState.SPEAKING, rms = 0f, reducedMotion = true, size = 150.dp)
                        OrbView(state = AssistantState.ERROR, rms = 0f, reducedMotion = true, size = 150.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("ROUT orb states: idle / listening / speaking / error",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        savePng("orb_states.png")
    }

    @Test
    fun settingsScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)
        composeRule.setContent {
            RoutTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                com.rout.aiassistant.ui.screens.SettingsScreen(vm, nav)
            }
        }
        composeRule.waitForIdle()
        savePng("settings_screen.png")
    }

    @Test
    fun homeScreenFontScale13() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        try {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val vm = MainViewModel(app)
            composeRule.setContent {
                RoutTheme {
                    val nav = androidx.navigation.compose.rememberNavController()
                    com.rout.aiassistant.ui.screens.HomeScreen(vm, nav)
                }
            }
            composeRule.waitForIdle()
            savePng("home_screen_font13.png")
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(1.0f)
        }
    }

    @Test
    fun homeScreenSmallHeight() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)
        composeRule.setContent {
            RoutTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                com.rout.aiassistant.ui.screens.HomeScreen(vm, nav)
            }
        }
        composeRule.waitForIdle()
        savePng("home_screen_small.png", heightPx = 1500)
    }

    @Test
    fun settingsScreenBottom() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)
        composeRule.setContent {
            RoutTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                com.rout.aiassistant.ui.screens.SettingsScreen(vm, nav, scrollToEnd = true)
            }
        }
        composeRule.waitForIdle()
        savePng("settings_screen_bottom.png")
    }

    @Test
    fun historyScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)
        composeRule.setContent {
            RoutTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                com.rout.aiassistant.ui.screens.HistoryScreen(vm, nav)
            }
        }
        composeRule.waitForIdle()
        savePng("history_screen.png")
    }

    @Test
    fun homeScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)
        composeRule.setContent {
            RoutTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                com.rout.aiassistant.ui.screens.HomeScreen(vm, nav)
            }
        }
        composeRule.waitForIdle()
        savePng("home_screen.png")
    }
}
