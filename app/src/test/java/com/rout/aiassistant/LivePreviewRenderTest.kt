package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ui.LiveFloatView
import com.rout.aiassistant.ui.components.SiriOrb
import com.rout.aiassistant.ui.theme.RoutBlack
import com.rout.aiassistant.voice.AssistantState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class LivePreviewRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun saveDecor(name: String, wPx: Int, hPx: Int) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            decor.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(wPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(hPx, android.view.View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, wPx, hPx)
            val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        composeRule.waitForIdle()
    }

    /** Animation frames of the Siri swirl (LISTENING) as one horizontal strip (180px/frame). */
    @Test
    fun orbAnimFrames() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(RoutBlack)) {
                for (f in 0 until 8) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        SiriOrb(state = AssistantState.LISTENING, rms = 5f, reducedMotion = false,
                            modifier = Modifier.size(56.dp), phaseOverride = f * 1.6f)
                    }
                }
            }
        }
        saveDecor("orb_anim/strip.png", 8 * 180, 180)
    }

    /** The floating LIVE orb as it appears over other apps while speaking. */
    @Test
    fun floatingLiveOrb() {
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = LiveFloatView(ctx)
        view.setSpeaking(true)
        val spec = android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, 300, 300)
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        FileOutputStream(File(dir, "floating_live_orb.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
