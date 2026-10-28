package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.PMode
import com.rout.aiassistant.ai.PersonalitySettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** SYNTHETIC render (Robolectric, 393dp phone width, not a phone): the real SettingsScreen Personality card with settings expanded. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonalityRender0729Test {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(name: String) {
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val d = composeRule.activity.window.decorView
            val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
            d.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(File("/home/sandbox/rout/renders").apply { mkdirs() }, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun personalityCardRenders() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        kotlinx.coroutines.runBlocking {
            vm.prefs.setPersonality(PersonalitySettings(mode = PMode.WAIFU, romanticOn = true, affection = 3, jealousy = 2, playfulAnger = true,
                intensity = 2, language = "hinglish", addressStyle = "custom", preferredAddress = "Gyan ji ka bahut lamba pyaar wala naam", stopPhrase = "OK OK, RUKO",
                resumePhrase = "phir se shuru karo meri jaan"))
        }
        composeRule.waitForIdle()
        composeRule.setContent { com.rout.aiassistant.ui.screens.SettingsScreen(vm, rememberNavController()) }
        composeRule.waitForIdle()
        Thread.sleep(500); composeRule.waitForIdle()
        composeRule.onNodeWithText("Sirf bolne ka andaaz", substring = true).performScrollTo(); shot("personality_a_top_0729.png")
        composeRule.onNodeWithText("Playful jealousy").performScrollTo(); shot("personality_b_toggles_0729.png")
        composeRule.onNodeWithText("Preferred address", substring = true).performScrollTo(); shot("personality_c_fields_0729.png")
        composeRule.onNodeWithText("Voice style", substring = true).performScrollTo()
        composeRule.onNodeWithText("Energetic and playful").performScrollTo(); shot("personality_d_voice_0729.png")
        composeRule.onNodeWithText("Speech speed", substring = true).performScrollTo(); shot("personality_e_after_voice_0729.png")
        composeRule.onNodeWithText("Mode badalta hai ROUT KAISE", substring = true).performScrollTo(); shot("personality_f_persona_default_0729.png")
    }
}
