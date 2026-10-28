package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.service.ScreenSafety
import com.rout.aiassistant.ui.screens.SCREEN_CLOUD_SCOPE_TEXT
import com.rout.aiassistant.ui.screens.ScreenCloudScopeText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenCloudApproval0724Test {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun offByDefaultAndOnlyAnExplicitSetEnablesIt() = runBlocking {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        assertFalse(prefs.screenCloudAllowed())
        prefs.setScreenCloudAllowed(true); assertTrue(prefs.screenCloudAllowed())
        prefs.setScreenCloudAllowed(false); assertFalse(prefs.screenCloudAllowed())
    }

    @Test fun scopeTextNamesWhatGoesWhereAndWhatDoesNot() {
        val t = SCREEN_CLOUD_SCOPE_TEXT
        for (k in listOf("Kya jaayega", "Kahan jaayega", "Kya nahi jaayega", "screenshot", "chat ke message", "100% pakka nahi", "Confirm ke baad", "notifications ke liye nahi"))
            assertTrue(k, t.contains(k))
        assertFalse(t.contains("sab kuch") )
    }

    @Test fun redactionStillHidesCodesNumbersEmailPhone() {
        val r = ScreenSafety.redact("OTP 482913, card 4111 1111 1111 1111, a@b.com, 9876543210")
        assertFalse(r.contains("482913") || r.contains("4111") || r.contains("a@b.com") || r.contains("9876543210"))
    }

    @Test fun renderScopeTextSynthetic() {
        composeRule.setContent {
            Surface(color = Color(0xFF1B1B2B), contentColor = Color(0xFFE6E3F5)) { Column(Modifier.padding(24.dp)) { androidx.compose.material3.Text("Screen text AI ko bhejne ki ijazat?", color = Color.White); androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp)); androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) { ScreenCloudScopeText() } } }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val d = composeRule.activity.window.decorView
            val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
            d.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(File("/home/sandbox/rout/renders").apply { mkdirs() }, "screen_cloud_scope_0724.png")).use { Bitmap.createBitmap(bmp, 0, 0, d.width, minOf(d.height, 900)).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
