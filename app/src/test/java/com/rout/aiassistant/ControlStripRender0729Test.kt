package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.rout.aiassistant.control.ControlSnapshot
import com.rout.aiassistant.control.ControlState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlStripRender0729Test {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var stops = 0
    private var oks = 0

    private fun shot(name: String) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1080, 2400)
            var last: Throwable? = null
            for (a in 1..3) {
                try {
                    decor.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
                    decor.draw(android.graphics.Canvas(bmp))
                    FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    return@runOnUiThread
                } catch (t: Throwable) { last = t; Thread.sleep(150) }
            }
            throw last ?: IllegalStateException("shot failed")
        }
    }

    private fun show(s: ControlSnapshot) {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(16.dp)) {
                Column(Modifier.padding(top = 120.dp)) {
                    com.rout.aiassistant.ui.screens.ControlStripContent(s, onStop = { stops++ }, onDismiss = { oks++ })
                }
            }
        }
    }

    @Test fun executingShowsStopAndLog() {
        show(ControlSnapshot(ControlState.EXECUTING, "r1", 2, 3, "app.open", "YouTube khol rahi hoon",
            listOf("1. app.open YouTube - ok", "2. web.search Free Fire Shorts - running")))
        composeRule.onNodeWithText("STOP").assertExists().performClick()
        assertEquals(1, stops)
        composeRule.onNodeWithText("Chal raha hai", substring = true).assertExists()
        shot("control_strip_executing_0729")
    }

    @Test fun confirmationAndFailedAndDone() {
        show(ControlSnapshot(ControlState.WAITING_FOR_CONFIRMATION, "r2", 1, 1, "message.send", "Rohan ko 'hi' bhejna hai?", listOf("1. message.send - confirmation chahiye")))
        composeRule.onNodeWithText("Confirmation ka intezaar", substring = true).assertExists()
        shot("control_strip_confirm_0729")
    }

    @Test fun failedShowsOk() {
        show(ControlSnapshot(ControlState.FAILED, "r3", 1, 2, "call.end", "Phone-calls permission nahi mili, call khatam nahi ho paayi", listOf("1. call.end - permission missing")))
        composeRule.onNodeWithText("OK").assertExists().performClick()
        assertEquals(1, oks)
        shot("control_strip_failed_0729")
    }

    @Test fun idleHidden() {
        show(ControlSnapshot())
        composeRule.onAllNodesWithText("STOP").assertCountEquals(0)
    }

    private fun homeWith(prepare: (com.rout.aiassistant.control.ControlEngine) -> Unit, name: String) {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.runOnUiThread { prepare(vm.controlEngineForRender()) }
        composeRule.setContent {
            val nav = androidx.navigation.compose.rememberNavController()
            com.rout.aiassistant.ui.screens.HomeScreen(vm, nav)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("OK").assertExists()
        shot(name)
    }

    @Test fun fullHomeWithStripAndDockFailed() = homeWith({ e -> e.planning("r9"); e.planningFailed("Call end abhi available nahi: permission nahi hai aur ye option abhi on nahi hai. Call khud kaat do, kuch nahi kiya.") }, "home_strip_dock_failed_0729")

    @Test fun fullHomeIdleHasNoStrip() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent { com.rout.aiassistant.ui.screens.HomeScreen(vm, androidx.navigation.compose.rememberNavController()) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("STOP").assertCountEquals(0)
        composeRule.onAllNodesWithText("OK").assertCountEquals(0)
        shot("home_idle_0729")
    }
}
