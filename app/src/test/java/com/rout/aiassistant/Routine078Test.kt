package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Route proof for routines: seeded + user-created triggers run their steps; deleting stops them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Routine078Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun router(): CommandRouter {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        return CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {})
    }

    @Test fun seededGoodMorningRunsItsSteps() = runBlocking {
        val r = router().handle("good morning", localOnly = true)
        assertTrue(r, r.startsWith("Routine: good morning."))
    }

    @Test fun userRoutineCreateRunDelete() = runBlocking {
        val rt = router()
        val made = rt.handle("routine banao coding mode: time, battery", localOnly = true)
        assertTrue(made, made.contains("ban gaya"))
        val ran = rt.handle("coding mode", localOnly = true)
        assertTrue(ran, ran.startsWith("Routine: coding mode."))
        rt.handle("routine hatao coding mode", localOnly = true)
        val after = rt.handle("coding mode", localOnly = true)
        assertFalse(after, after.startsWith("Routine: coding mode."))
    }
}
