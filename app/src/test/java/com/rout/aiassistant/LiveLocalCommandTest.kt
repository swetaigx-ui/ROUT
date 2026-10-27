package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LIVE voice turns: localOnly routing executes real device commands but never
 * falls through to the AI fallback (general conversation belongs to the LIVE
 * model's own voice reply).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveLocalCommandTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val router by lazy {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {})
    }

    @Test fun deviceCommandHandledLocally() = runBlocking {
        val reply = router.handle("kitne baje", localOnly = true)
        assertTrue(reply.isNotBlank())
        assertTrue(!reply.contains("To enable AI answers"))
    }

    @Test fun generalChatReturnsEmptyNoAiFallback() = runBlocking {
        val reply = router.handle("tumhara din kaisa ja raha hai", localOnly = true)
        assertEquals("", reply)
    }

    @Test fun normalHandleStillUsesAiFallback() = runBlocking {
        val reply = router.handle("tumhara din kaisa ja raha hai")
        assertTrue(reply.isNotBlank())
    }
}
