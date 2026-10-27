package com.rout.aiassistant

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
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
        // 0.7.4: the "Sun raha:" diagnostic line - what the model is hearing, on screen.
        val h = vm.live.javaClass.getDeclaredField("_heard").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (h.get(vm.live) as MutableStateFlow<String>).value = "camera kholo"
        shot("home_live_heard_074")
        // 0.7.4: surfaced upstream error instead of silent failure.
        val e = vm.live.javaClass.getDeclaredField("_error").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (e.get(vm.live) as MutableStateFlow<String?>).value = "Live error: model overloaded"
        @Suppress("UNCHECKED_CAST") (f.get(vm.live) as MutableStateFlow<String>).value = "failed"
        shot("home_live_error_074")
    }

    /** 0.7.5: LIVE error state in isolation - fresh VM, no other state bleeding in. */
    @Test fun liveErrorIsolated() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent {
            val nav = rememberNavController()
            com.rout.aiassistant.ui.screens.HomeScreen(vm, nav)
        }
        val f = vm.live.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        val e = vm.live.javaClass.getDeclaredField("_error").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (e.get(vm.live) as MutableStateFlow<String?>).value = "Live error: model overloaded"
        @Suppress("UNCHECKED_CAST") (f.get(vm.live) as MutableStateFlow<String>).value = "failed"
        shot("home_live_error_isolated_075")
    }

    @Test fun chatView() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent {
            val nav = rememberNavController()
            com.rout.aiassistant.ui.screens.ChatScreen(vm, nav)
        }
        composeRule.runOnUiThread { vm.onUserText("time batao", false) }
        repeat(60) {
            if (vm.messages.value.size >= 2) return@repeat
            Thread.sleep(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        shot("chat_071")
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
        run {
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 30000) {
                composeRule.mainClock.advanceTimeBy(200); Thread.sleep(50); org.robolectric.shadows.ShadowLooper.idleMainLooper(); composeRule.waitForIdle()
                val nodes = composeRule.onAllNodesWithText("POWERED BY GYAN", substring = true, ignoreCase = true).fetchSemanticsNodes()
                if (nodes.isNotEmpty() && nodes[0].boundsInRoot.bottom < 2300f) break
            }
        }
        shot("settings_end_071")
        val tree = composeRule.onRoot().fetchSemanticsNode()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode, out: MutableList<Pair<String, androidx.compose.ui.geometry.Rect>>) {
            val t = (if (n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text } else null)
            if (t != null) out.add(t to n.boundsInRoot)
            n.children.forEach { walk(it, out) }
        }
        val all = mutableListOf<Pair<String, androidx.compose.ui.geometry.Rect>>(); walk(tree, all)
        val about = all.filter { it.first.contains("POWERED BY GYAN", true) || it.first.contains("About", true) }
        val navLabel = all.last { it.first == "Settings" }
        about.forEach { println("BOUNDS " + it.first.take(40) + " " + it.second) }
        val credit = all.first { it.first.contains("POWERED BY GYAN") }
        org.junit.Assert.assertTrue("credit line must clear the nav bar", credit.second.bottom < navLabel.second.top - 40f)
        println("BOUNDS navLabel " + navLabel.second + " rootH=" + tree.boundsInRoot.height)
    }
}
