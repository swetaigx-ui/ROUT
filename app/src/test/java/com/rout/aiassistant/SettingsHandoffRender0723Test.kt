package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** SYNTHETIC render (Robolectric, not a phone): Settings "Hey ROUT session" row with the hand-off text, 1080px-wide viewport. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsHandoffRender0723Test {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun handoffTextRenders() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        val named = System.getenv("RENDER_NAMED") != "0"
        if (named) kotlinx.coroutines.runBlocking { vm.setAssistantName("Maya") }
        composeRule.waitForIdle()
        composeRule.setContent { val nav = rememberNavController(); com.rout.aiassistant.ui.screens.SettingsScreen(vm, nav) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Floating orb - kahin bhi baat").performScrollTo()
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val w = decor.width; val h = decor.height
            System.err.println("VIEWPORT " + w + "x" + h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
            FileOutputStream(File(dir, if (named) "settings_0723_named.png" else "settings_0723_unnamed.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
