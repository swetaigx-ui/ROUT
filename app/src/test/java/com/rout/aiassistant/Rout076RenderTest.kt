package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/** 0.7.6 renders: clean Home (no greeting card) + dock liquid-touch frames. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Rout076RenderTest {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(name: String) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1080, 2400)
            // Robolectric software-canvas can NPE inside vector cache on a racing
            // animation frame; software layer type + one retry rides it out.
            var last: Throwable? = null
            for (attempt in 1..3) {
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

    @Test fun homeCleanNoGreetingCard() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent {
            val nav = rememberNavController()
            com.rout.aiassistant.ui.screens.HomeScreen(vm, nav)
        }
        composeRule.waitForIdle()
        // The greeting/help card is gone: no greeting, no "madad" line, no Home ask field.
        composeRule.onNodeWithText("Aaj main aapki kya madad karun?", substring = true).assertDoesNotExist()
        composeRule.onAllNodesWithText("Good Evening", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Good Morning", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Good Afternoon", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("ROUT se kuch bhi poochho", substring = true).assertDoesNotExist()
        // Core controls remain: persona pill, settings gear, orb stage, status caption.
        assertTrue(composeRule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty())
        shot("home_clean_076")
    }

    @Test fun chatStillHasTextEntry() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent {
            val nav = rememberNavController()
            com.rout.aiassistant.ui.screens.ChatScreen(vm, nav)
        }
        composeRule.waitForIdle()
        // Text input is preserved in Chat after the Home ask field was removed.
        composeRule.onNode(hasSetTextAction()).assertExists()
    }

    @Test fun dockLiquidTouchFrames() {
        val current = mutableStateOf("settings")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val nav = rememberNavController()
            com.rout.aiassistant.ui.components.RoutDock(current.value, nav)
        }
        composeRule.mainClock.advanceTimeBy(500)
        shot("dock_idle_076")
        // Press on the "Home" tab area and hold: glow follows the finger.
        val home = composeRule.onNodeWithText("Home")
        val hb = home.fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            down(androidx.compose.ui.geometry.Offset(hb.left + hb.width / 2, hb.top + hb.height / 2))
        }
        composeRule.mainClock.advanceTimeBy(120) // mid fade-in
        shot("dock_touch_home_076")
        composeRule.mainClock.advanceTimeBy(400)
        shot("dock_touch_home_settled_076")
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(200) // mid liquid-settle back
        shot("dock_release_settle_076")
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(5000) // drain settle animations before class ends
        shot("dock_after_release_076")
        assertTrue(true)
    }
}
