package com.rout.aiassistant
import android.Manifest
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.ContactResolver
import com.rout.aiassistant.commands.HindiNormal
import com.rout.aiassistant.commands.SpokenCommands
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import com.rout.aiassistant.data.Routine
import com.rout.aiassistant.commands.CommandRouter
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutineHalt0730Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sent = ArrayList<Pair<String, String>>()
    private val armed = ArrayList<com.rout.aiassistant.commands.ConfirmRequest>()
    private lateinit var db: RoutDatabase
    private fun router(): CommandRouter {
        Shadows.shadowOf(ctx as android.app.Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        return CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) },
            contactRows = { _, _ -> listOf(ContactResolver.Row("Rohan Sharma", "+919800000001", 1L)) }, whatsappSender = { _, n, b -> sent.add(n to b); "ok" },
            foregroundPkg = { null }, whatsappAutoSend = { true }, likeTapper = { "liked" }, statusViewer = { "status-opened" })
    }

    @Test fun routineHaltsAtConfirmationAndIsNotComplete() = runBlocking {
        val r = router()
        db.routines().insert(Routine(trigger = "morning msg", steps = "call Rohan Sharma\ntime"))
        val (ok, msg) = r.runSavedRoutine("morning msg")
        assertFalse("ok=$ok msg=$msg armed=${armed.size}", ok)
        assertEquals(1, armed.size)            // exactly one pending confirmation
        assertTrue(msg.contains("poora nahi"))
        assertTrue(sent.isEmpty())              // nothing sent without the user's yes
        assertTrue(msg.contains("nahi chalenge") || msg.contains("baaki"))
    }

    @Test fun routineWithoutConfirmationCompletes() = runBlocking {
        val r = router()
        db.routines().insert(Routine(trigger = "quick", steps = "time"))
        assertTrue(r.runSavedRoutine("quick").first)
        assertTrue(armed.isEmpty())
    }

    @Test fun unknownRoutineFailsHonestly() = runBlocking {
        val r = router()
        db.routines().insert(Routine(trigger = "good morning", steps = "time"))
        val (ok, msg) = r.runSavedRoutine("evening")
        assertFalse(ok); assertTrue(msg.contains("nahi mila"))
    }

    @Test fun routineNameMatchingIsExactNotFuzzy() {
        val known = listOf("good morning", "good night", "office")
        assertEquals("good morning", CommandRouter.matchRoutine("good morning routine", known))
        assertEquals("good morning", CommandRouter.matchRoutine("Good Morning routine chalao", known))
        assertEquals("good night", CommandRouter.matchRoutine("good night", known))
        assertNull(CommandRouter.matchRoutine("good", known))
        assertNull(CommandRouter.matchRoutine("morning", known))
        assertNull(CommandRouter.matchRoutine("routine chalao", known))
        assertNull(CommandRouter.matchRoutine("goodmorning", known))
        assertNull(CommandRouter.matchRoutine("office party", known))
    }
}
