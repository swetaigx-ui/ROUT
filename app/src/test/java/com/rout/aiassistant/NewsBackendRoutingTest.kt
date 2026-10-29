package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.commands.NewsClient
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewsBackendRoutingTest {
    private lateinit var ctx: Context
    private lateinit var db: RoutDatabase

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun router(news: (suspend (String) -> NewsClient.Result)?): CommandRouter =
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {}, newsBackend = news)

    @Test fun `news query uses backend answer with mode tag`() = runBlocking {
        val r = router { q ->
            assertTrue(q.contains("news", ignoreCase = true) || q.isNotBlank())
            NewsClient.Result.Answer("NDTV ke mutabik aaj yeh hua.", listOf("NDTV"), "rss-headlines")
        }
        val reply = r.handle("aaj ki news batao", localOnly = true)
        assertTrue(reply.contains("NDTV ke mutabik aaj yeh hua."))
        assertTrue(reply.contains("(headlines se)"))
    }

    @Test fun `grounded backend answer has no rss tag`() = runBlocking {
        val r = router { NewsClient.Result.Answer("Grounded jawab.", listOf("X"), "grounded") }
        val reply = r.handle("latest news batao", localOnly = true)
        assertTrue(reply.contains("Grounded jawab."))
        assertTrue(!reply.contains("(headlines se)"))
    }

    @Test fun `no backend means honest unavailable or feed reply`() = runBlocking {
        // Robolectric has no network: on-device RSS fails, so the honest line must come back.
        val r = router(null)
        val reply = r.handle("aaj ki news batao", localOnly = true)
        assertTrue(reply.contains("News feed abhi nahi mil raha") || reply.contains("Aaj ki top khabrein"))
    }
}
