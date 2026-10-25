package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rout.aiassistant.ui.components.WarbyEmotion
import com.rout.aiassistant.ui.components.WarbyOrb
import com.rout.aiassistant.voice.AssistantState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders Warby in its key modes so the design is verified as actual pixels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WarbyRenderTest {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun savePng(name: String) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val specW = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
            val specH = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
            decor.measure(specW, specH)
            decor.layout(0, 0, 1080, 1080)
            val bmp = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        composeRule.waitForIdle()
    }

    private fun shot(name: String, state: AssistantState, emotion: WarbyEmotion?) {
        composeRule.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF050608)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                WarbyOrb(state = state, emotion = emotion, rms = 3f, reducedMotion = false, size = 320.dp)
            }
        }
        savePng(name)
    }

    @Test fun warbyIdle() = shot("warby_idle.png", AssistantState.IDLE, null)
    @Test fun warbyListening() = shot("warby_listening.png", AssistantState.LISTENING, null)
    @Test fun warbyTalking() = shot("warby_talking.png", AssistantState.SPEAKING, null)
    @Test fun warbyHappy() = shot("warby_happy.png", AssistantState.IDLE, WarbyEmotion.HAPPY)
    @Test fun warbyLove() = shot("warby_love.png", AssistantState.IDLE, WarbyEmotion.LOVE)
    @Test fun warbySleepy() = shot("warby_sleepy.png", AssistantState.IDLE, WarbyEmotion.SLEEPY)
    @Test fun warbyAngry() = shot("warby_angry.png", AssistantState.IDLE, WarbyEmotion.ANGRY)
    @Test fun warbyExcited() = shot("warby_excited.png", AssistantState.IDLE, WarbyEmotion.EXCITED)
}
