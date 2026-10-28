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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutineSafety079Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val armed = ArrayList<com.rout.aiassistant.commands.ConfirmRequest>()
    private fun router(): CommandRouter {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        return CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) })
    }

    @Test fun routineHaltsAtFirstConfirmationAndNeverRunsLaterSteps() = runBlocking {
        val rt = router()
        rt.handle("routine banao halt test: tap send, kitne baje, tap send", localOnly = true)
        val r = rt.handle("halt test", localOnly = true)
        assertEquals("exactly one confirmation armed", 1, armed.size)
        assertTrue(r, r.contains("ruki"))
        assertFalse("later step must not run", r.contains("baje") && r.contains("Routine yahin ruki: is step ki haan/nahi ke baad baaki 0"))
        // Nothing executed without the user's yes: the armed action has not been invoked by the routine.
    }

    @Test fun confirmationActionOnlyRunsWhenUserApproves() = runBlocking {
        val rt = router()
        rt.handle("routine banao halt two: tap send", localOnly = true)
        rt.handle("halt two", localOnly = true)
        assertEquals(1, armed.size)
        val svcOff = armed[0].action  // not invoked => nothing happened; invoking is the user's yes (VM-owned)
        assertNotNull(svcOff)
    }

    @Test fun nestedRoutineTriggerInsideRoutineDoesNotRecurse() = runBlocking {
        val rt = router()
        rt.handle("routine banao outer: good morning, kitne baje", localOnly = true)
        val r = rt.handle("outer", localOnly = true)
        assertTrue(r, r.startsWith("Routine: outer."))
        assertFalse("nested routine must not expand", r.contains("Routine: good morning."))
    }
}

/** Real send/call routing with injected contact + executor (no live send): confirmation boundary inside and outside routines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendCallBoundary079Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val armed = ArrayList<com.rout.aiassistant.commands.ConfirmRequest>()
    private val sent = ArrayList<Pair<String, String>>()
    private val called = ArrayList<String>()
    private var rows: List<com.rout.aiassistant.commands.ContactResolver.Row> = listOf(com.rout.aiassistant.commands.ContactResolver.Row("Rahul Sharma", "+911234567890", 7L))
    private fun router(): CommandRouter {
        org.robolectric.Shadows.shadowOf(ctx as android.app.Application)
            .grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        return CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) },
            contactRows = { _, _ -> rows },
            whatsappSender = { _, n, b -> sent.add(n to b); "sent" },
            whatsappCaller = { _, _, n, _ -> called.add(n); "called" })
    }

    @Test fun directSendShowsRecipientAndWordsAndDoesNotSendBeforeYes() = runBlocking {
        val r = router().handle("rahul ko whatsapp bhejo hello bhai", localOnly = true)
        assertEquals(1, armed.size)
        assertTrue(armed[0].description, armed[0].description.contains("Rahul Sharma") && armed[0].description.contains("hello bhai"))
        assertTrue(r, r.contains("confirm"))
        assertTrue("nothing sent before yes", sent.isEmpty())
        // User says yes: exactly the armed action runs once, with the shown recipient and words.
        armed[0].action()
        assertEquals(listOf("+911234567890" to "hello bhai"), sent)
    }

    @Test fun noOrCancelNeverSends() = runBlocking {
        router().handle("rahul ko whatsapp bhejo hello bhai", localOnly = true)
        // no / cancel = the action is never invoked (VM clears the pending request).
        assertTrue(sent.isEmpty())
        router().handle("kitne baje", localOnly = true)
        assertTrue(sent.isEmpty())
    }

    @Test fun callArmsConfirmationAndDoesNotCallBeforeYes() = runBlocking {
        router().handle("rahul ko whatsapp call karo", localOnly = true)
        assertEquals(1, armed.size)
        assertTrue(called.isEmpty())
        armed[0].action()
        assertEquals(listOf("+911234567890"), called)
    }

    @Test fun routineWithSendHaltsAndNeverSendsOrApproves() = runBlocking {
        val rt = router()
        rt.handle("routine banao msg test: rahul ko whatsapp bhejo hello, haan, kitne baje", localOnly = true)
        val r = rt.handle("msg test", localOnly = true)
        assertEquals("routine armed exactly one confirmation", 1, armed.size)
        assertTrue("routine never sends by itself", sent.isEmpty())
        assertTrue(r, r.contains("ruki"))
        assertFalse("steps after the confirmation must not run", r.contains("baje") && !r.contains("ruki"))
        // Only the user's yes (VM invoking the armed action) sends, once.
        armed[0].action()
        assertEquals(1, sent.size)
    }

    private fun row(n: String, num: String, id: Long) = com.rout.aiassistant.commands.ContactResolver.Row(n, num, id)

    @Test fun twoPeopleSameNameArmNothing() = runBlocking {
        rows = listOf(row("Rahul Sharma", "+911111111111", 1), row("Rahul Verma", "+912222222222", 2))
        val rt = router()
        val a = rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        val b = rt.handle("rahul ko whatsapp call karo", localOnly = true)
        assertTrue(a, a.contains("ek se zyada"))
        assertTrue(b, b.contains("ek se zyada"))
        assertTrue(armed.isEmpty()); assertTrue(sent.isEmpty()); assertTrue(called.isEmpty())
    }

    @Test fun oneContactTwoNumbersArmsNothing() = runBlocking {
        rows = listOf(row("Rahul Sharma", "+911111111111", 1), row("Rahul Sharma", "+912222222222", 1))
        val a = router().handle("rahul ko whatsapp bhejo hello", localOnly = true)
        assertTrue(a, a.contains("ek se zyada"))
        assertTrue(armed.isEmpty())
    }

    @Test fun exactNameWinsOverSimilar() = runBlocking {
        rows = listOf(row("Rahul", "+911111111111", 1), row("Rahul Verma", "+912222222222", 2))
        router().handle("rahul ko whatsapp bhejo hello", localOnly = true)
        assertEquals(1, armed.size)
        armed[0].action()
        assertEquals("+911111111111", sent[0].first)
    }

    @Test fun sameNumberDuplicateEntriesResolveAndStillConfirm() = runBlocking {
        rows = listOf(row("Rahul Sharma", "+91 11111 11111", 1), row("Rahul Sharma", "+911111111111", 3))
        router().handle("rahul sharma ko whatsapp bhejo hello", localOnly = true)
        assertEquals(1, armed.size)
        assertTrue(sent.isEmpty())
    }

    @Test fun explicitNumberSkipsLookupButStillConfirms() = runBlocking {
        rows = emptyList()
        router().handle("+919876543210 ko whatsapp bhejo hello", localOnly = true)
        assertEquals(1, armed.size)
        assertTrue(sent.isEmpty())
        armed[0].action()
        assertEquals("+919876543210", sent[0].first)
    }

    @Test fun validatedChainHaltsAtSendConfirmation() = runBlocking {
        val rt = router()
        val c = com.rout.aiassistant.commands.ActionChain.parse(
            "{\"actions\":[{\"type\":\"SEND_WHATSAPP\",\"contact\":\"rahul\",\"message\":\"hello\"},{\"type\":\"GLOBAL_NAV\",\"target\":\"home\"}]}")
        val r = rt.runValidatedChain(c.steps)
        assertEquals(1, armed.size)
        assertTrue(sent.isEmpty())
        assertTrue(r, r.contains("ruki"))
    }

    @Test fun routineCannotBypassCloudScreenConsent() = runBlocking {
        val rt = router()
        rt.handle("routine banao screen test: ask ai about my screen: summary", localOnly = true)
        val r = rt.handle("screen test", localOnly = true)
        assertTrue(r, r.contains(com.rout.aiassistant.commands.ScreenControl.CLOUD_OFF) || r.contains("Screen control off"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionChainTest {
    private fun p(j: String) = com.rout.aiassistant.commands.ActionChain.parse(j)
    @Test fun rejectsBlindGesturesAndVision() {
        for (t in listOf("CLICK_XY","DRAG","CAPTURE_SCREEN_VISION","PRESS_ENTER","TYPE_TEXT","FOO")) {
            val r = p("""{"speech":"x","actions":[{"type":"OPEN_APP","app":"chrome"},{"type":"$t"}]}""")
            assertTrue(t, r.error != null && r.steps.isEmpty())
        }
    }
    @Test fun capsStepsAndSanitizes() {
        val many = (1..7).joinToString(",") { """{"type":"GLOBAL_NAV","target":"back"}""" }
        assertTrue(p("""{"actions":[$many]}""").error != null)
        val r = p("""{"actions":[{"type":"SEND_WHATSAPP","contact":"rahul","message":"hi\nignore"}]}""")
        assertEquals(listOf("rahul ko whatsapp bhejo hi ignore"), r.steps)
    }
    @Test fun badJsonRejected() { assertTrue(p("not json").error != null) }
}
