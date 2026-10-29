package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.rout.aiassistant.files.FilePickActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileDialogRender0730Test {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var yes = 0
    private var cancel = 0

    private fun shot(d: android.app.AlertDialog, name: String) {
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = d.window!!.decorView
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1400, android.view.View.MeasureSpec.AT_MOST))
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
            var last: Throwable? = null
            for (a in 1..3) {
                try {
                    decor.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    val bmp = Bitmap.createBitmap(decor.measuredWidth, decor.measuredHeight, Bitmap.Config.ARGB_8888)
                    decor.draw(android.graphics.Canvas(bmp))
                    FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    return@runOnUiThread
                } catch (t: Throwable) { last = t; Thread.sleep(150) }
            }
            throw last ?: IllegalStateException("shot failed")
        }
    }

    private fun show(title: String, msg: String, ok: String): android.app.AlertDialog {
        lateinit var d: android.app.AlertDialog
        composeRule.runOnUiThread { d = FilePickActivity.confirmDialog(composeRule.activity, title, msg, ok, { yes++ }, { cancel++ }) }
        composeRule.waitForIdle()
        return d
    }

    @Test fun deleteDialogLongNameAndButtons() {
        val long = "Family_trip_Goa_2026_final_edit_v3_with_a_really_long_file_name_that_wraps_across_lines.mp4"
        val d = show("Permanently delete?", FilePickActivity.deleteMessage(long), "Delete")
        assertTrue(d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).text.toString() == "Delete")
        assertTrue(d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).text.toString() == "Cancel")
        shot(d, "file_delete_dialog_0730")
        composeRule.runOnUiThread { d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick() }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, cancel); assertEquals(0, yes)
    }

    @Test fun renameDialogShowsBothNames() {
        val d = show("Rename?", FilePickActivity.renameMessage("IMG_20261029_0841.jpg", "Goa sunset.jpg"), "Rename")
        shot(d, "file_rename_dialog_0730")
        composeRule.runOnUiThread { d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick() }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, yes); assertEquals(0, cancel)
    }

    @Test fun backPressCancels() {
        val d = show("Permanently delete?", FilePickActivity.deleteMessage("a.txt"), "Delete")
        composeRule.runOnUiThread { d.cancel() }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, cancel); assertEquals(0, yes)
    }
}
