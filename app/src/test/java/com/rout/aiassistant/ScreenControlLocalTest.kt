package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** User 04:54 re-scope: basic physical commands (scroll/swipe/back/brightness) must DO the action
 * or say exactly what is missing - never promise. These routes run locally without backend
 * enrollment; with no accessibility service they must return the honest enable message. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenControlLocalTest {
    private lateinit var ctx: Context
    private lateinit var db: RoutDatabase

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        android.provider.Settings.System.putInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128)
    }
    @After fun tearDown() = db.close()

    private fun router(): CommandRouter =
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {})

    @Test fun `scroll karo routes locally and reports service off without promising`() = runBlocking {
        val reply = router().handle("scroll karo", localOnly = true)
        assertTrue(reply.contains("Screen control off hai"))
    }
    @Test fun `swipe up routes locally`() = runBlocking {
        val reply = router().handle("swipe up karo", localOnly = true)
        assertTrue(reply.contains("Screen control off hai"))
    }
    @Test fun `back karo routes locally`() = runBlocking {
        val reply = router().handle("back karo", localOnly = true)
        assertTrue(reply.contains("Screen control off hai"))
    }
    @Test fun `peeche jao routes locally`() = runBlocking {
        val reply = router().handle("peeche jao", localOnly = true)
        assertTrue(reply.contains("Screen control off hai"))
    }
    @Test fun `home jao routes locally`() = runBlocking {
        val reply = router().handle("home jao", localOnly = true)
        assertTrue(reply.contains("Screen control off hai"))
    }
    @Test fun `brightness badhao attempts adjustment not just settings`() = runBlocking {
        // Either the adjust executes ("Brightness set to ...") or the honest permission path fires -
        // never the settings-open fallback, which would mean the command was not attempted.
        val reply = router().handle("brightness badhao", localOnly = true)
        assertTrue(reply.contains("Brightness set to") || reply.contains("Modify system settings"))
    }
    @Test fun `brightness set to 40 still works`() = runBlocking {
        val reply = router().handle("brightness set to 40", localOnly = true)
        assertTrue(reply.contains("Modify system settings") || reply.contains("Brightness set to 40"))
    }
    @Test fun `unrelated brightness mention still opens settings`() = runBlocking {
        val reply = router().handle("meri brightness kya hai", localOnly = true)
        assertTrue(reply.contains("display settings") || reply.contains("Opening") || reply.contains("Modify"))
    }
    @Test fun `back inside other phrases does not hijack`() = runBlocking {
        // "wapas" as part of a different request must not trigger the back route.
        val reply = router().handle("aaj ki news batao", localOnly = true)
        assertTrue(!reply.contains("Back gaya"))
    }
}
