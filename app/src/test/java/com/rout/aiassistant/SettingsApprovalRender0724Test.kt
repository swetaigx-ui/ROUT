package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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
class SettingsApprovalRender0724Test {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun save(bmp: Bitmap, name: String) {
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun approvalRowsAndDialogsRender() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.waitForIdle()
        composeRule.setContent { val nav = rememberNavController(); com.rout.aiassistant.ui.screens.SettingsScreen(vm, nav) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Screen text AI ko bhejna").performScrollTo()
        composeRule.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.68f, durationMillis = 200) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val d = composeRule.activity.window.decorView
            val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888); d.draw(android.graphics.Canvas(bmp)); save(bmp, "settings_rows_off_0724.png")
        }
    }

    private fun renderSurface(file: String, title: String, confirm: String, dismiss: String, body: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                com.rout.aiassistant.ui.components.RoutApprovalSurface(title, confirm, dismiss, {}, {}, body = body)
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val d = composeRule.activity.window.decorView
            val bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888); d.draw(android.graphics.Canvas(bmp)); save(bmp, file)
        }
    }

    @Test fun dialogScreenCloudScope() = renderSurface("dlg_screen_cloud_0724.png", "Screen text AI ko bhejne ki ijazat?", "Haan, sirf is ke liye allow", "Nahi") { com.rout.aiassistant.ui.screens.ScreenCloudScopeText() }
    @Test fun dialogNotifScope() = renderSurface("dlg_notif_reply_0724.png", "Notification mein reply ki ijazat?", "Haan, sirf is ke liye allow", "Nahi") { androidx.compose.material3.Text(com.rout.aiassistant.ui.screens.NOTIF_REPLY_SCOPE_TEXT, fontSize = 14.sp, lineHeight = 20.sp) }
    @Test fun dialogLongPreviewConfirm() = renderSurface("dlg_preview_confirm_0724.png", "Confirm action", "Confirm", "Cancel") {
        androidx.compose.material3.Text("AI ko ye screen text bhejun? Ye exact text ROUT AI backend (phir Gemini) ko jaayega. Password, recovery words ya private chat ho to Cancel dabao - regex sab kuch nahi pakadta.\n---\n" + "Sample non-private fixture line about a recipe and weather today. ".repeat(18) + "\n---\nSawal: ye kya hai?", color = androidx.compose.ui.graphics.Color(0xFF8FA5B3))
    }

    @Test fun dialogImageConfirm() = renderSurface("dlg_image_confirm_0724.png", "Confirm action", "Confirm", "Cancel") {
        val bytes = java.io.File("/tmp/lt/fixture.jpg").readBytes()
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.Image(bmp, contentDescription = null, modifier = androidx.compose.ui.Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(bottom = 10.dp))
            androidx.compose.material3.Text("Ye screenshot AI ko bhejun? Poori screen ki image ROUT AI backend (phir Gemini) ko jaayegi - image mein jo bhi dikh raha hai (chat, naam, numbers, password) sab jaata hai, kuch chhupa nahi sakte. Private ho to Cancel dabao.\nYe image pehle ki capture hai (app: com.example.app, samay: 22:16:47) - sirf yahi dikhayi gayi image jaayegi, abhi ki screen nahi. Text badalne par ya doosre app mein jaane par ye nahi bheji jaayegi; sirf tasveer badle to ROUT pakad nahi sakta.\nSawal: ye kya hai", color = androidx.compose.ui.graphics.Color(0xFF8FA5B3))
        }
    }
}
