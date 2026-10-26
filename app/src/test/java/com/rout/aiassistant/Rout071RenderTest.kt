package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** 0.7.1 pixel checks: Home with bottom nav, Home while LIVE (chat hidden), Settings scrolled to the end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rout071RenderTest {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(name: String) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1080, 2400)
            val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun homeAndLiveAndSettingsEnd() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent {
            val nav = rememberNavController()
            com.rout.aiassistant.ui.screens.HomeScreen(vm, nav)
        }
        shot("home_normal_071")
        composeRule.runOnUiThread { vm.onUserText("time batao", false) }
        repeat(60) {
            if (vm.messages.value.size >= 2) return@repeat
            Thread.sleep(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        println("MSGS=" + vm.messages.value.size)
        shot("home_chat_071")
        val f = vm.live.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (f.get(vm.live) as MutableStateFlow<String>).value = "live"
        shot("home_live_chat_071")
    }

    @Test fun settingsEnd() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent {
            val nav = rememberNavController()
            com.rout.aiassistant.ui.screens.SettingsScreen(vm, nav, scrollToEnd = true)
        }
        composeRule.waitForIdle()
        shot("settings_end_071")
    }
}
