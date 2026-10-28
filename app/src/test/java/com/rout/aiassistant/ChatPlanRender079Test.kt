package com.rout.aiassistant

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.data.Message
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** SYNTHETIC fixture: conversation text is injected to show how plan/correction output renders. Not a phone or backend result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatPlanRender079Test {
    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun chatShowsPlanAndCorrection() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        lateinit var vm: MainViewModel
        composeRule.runOnUiThread { vm = MainViewModel(app) }
        composeRule.setContent { com.rout.aiassistant.ui.screens.ChatScreen(vm, rememberNavController()) }
        val f = vm.javaClass.getDeclaredField("conversationId").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val cid = (f.get(vm) as MutableStateFlow<Long>)
        repeat(30) { if (cid.value != 0L) return@repeat; Thread.sleep(100); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        val dao = RoutDatabase.get(app).conversations()
        runBlocking {
            dao.insertMessage(Message(conversationId = cid.value, role = "user", text = "youtube kholo aur neeche scroll karo"))
            dao.insertMessage(Message(conversationId = cid.value, role = "rout", text = "AI ne ye plan diya: open youtube -> scroll down. Routine: ai chain. Opening youtube Scroll kar diya."))
            dao.insertMessage(Message(conversationId = cid.value, role = "user", text = "rahul ko whatsapp bhejo kal milte hain"))
            dao.insertMessage(Message(conversationId = cid.value, role = "rout", text = "Rahul Sharma ko WhatsApp: \"kal milte hain\" - screen par confirm karo ya 'haan' bolo."))
            dao.insertMessage(Message(conversationId = cid.value, role = "user", text = "nahi, rohan ko"))
            dao.insertMessage(Message(conversationId = cid.value, role = "rout", text = "Rohan Das ko WhatsApp: \"kal milte hain\" - screen par confirm karo ya 'haan' bolo."))
        }
        repeat(40) { if (vm.messages.value.size >= 6) return@repeat; Thread.sleep(100); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        composeRule.waitForIdle()
        val dir = File("/home/sandbox/rout/renders").apply { mkdirs() }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1080, 2400)
            val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            FileOutputStream(File(dir, "chat_plan_synthetic_0715.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        println("MSGS=" + vm.messages.value.size)
    }
}
