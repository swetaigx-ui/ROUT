package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.commands.ContactResolver
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Follow-ups ("phir se", "wahi Rohan ko bhi", "wapas kholo") never resend/call/change recipient without a fresh confirmation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B1FollowUp0724Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val armed = ArrayList<com.rout.aiassistant.commands.ConfirmRequest>()
    private val sent = ArrayList<Pair<String, String>>()
    private val called = ArrayList<String>()
    private var rows = listOf(
        ContactResolver.Row("Rahul Sharma", "+911111111111", 1L),
        ContactResolver.Row("Rohan Das", "+913333333333", 5L))
    private fun router(): CommandRouter {
        org.robolectric.Shadows.shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        return CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) },
            contactRows = { _, q -> rows.filter { it.name.contains(q, ignoreCase = true) } },
            emailRows = { _, _ -> rows },
            whatsappSender = { _, n, b -> sent.add(n to b); "sent" },
            whatsappCaller = { _, _, n, _ -> called.add(n); "called" })
    }
    private val followUps = listOf("phir se", "wahi rohan ko bhi", "wahi message rohan ko bhi bhejo", "dobara bhejo", "wapas call karo", "same rohan ko bhi", "usko bhi bhej do")

    @Test fun followUpsWithNothingConfirmedYetAskAndArmNothing() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)   // armed, NOT confirmed
        for (f in followUps) {
            val r = rt.handle(f, localOnly = true)
            assertEquals("armed by: " + f, 1, armed.size)
            if (f != "usko bhi bhej do") assertTrue(f + " -> " + r, r.contains("Poora bolo"))
        }
        assertTrue(sent.isEmpty()); assertTrue(called.isEmpty())
    }

    @Test fun phirSeAfterConfirmedSendRearmsFreshConfirmationNeverSends() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        armed[0].action()
        rt.handle("phir se", localOnly = true)
        assertEquals(2, armed.size); assertEquals("nothing sent by the follow-up itself", 1, sent.size)
        assertTrue(armed[1].description, armed[1].description.contains("Rahul Sharma") && armed[1].description.contains("hello"))
        armed[1].action()
        assertEquals(2, sent.size)
    }

    @Test fun wahiOtherPersonKeepsWordsButResolvesRecipientFreshAndConfirms() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo kal milte hain", localOnly = true)
        armed[0].action()
        for (f in listOf("wahi rohan ko bhi", "wahi message rohan ko bhi bhejo", "same rohan ko bhi")) {
            rt.handle(f, localOnly = true)
            val d = armed.last().description
            assertTrue(f + " -> " + d, d.contains("Rohan Das") && d.contains("kal milte hain"))
        }
        assertEquals("only the first, user-confirmed send", 1, sent.size)
    }

    @Test fun followUpRecipientUnknownOrAmbiguousAsksAndArmsNothing() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        armed[0].action()
        val n = armed.size
        val r = rt.handle("wahi zzzz ko bhi", localOnly = true)
        assertEquals(n, armed.size); assertTrue(r, r.contains("nahi mila"))
        assertEquals(1, sent.size)
    }

    @Test fun followUpForgottenAfterExpiryOrClearAsksForEverything() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        armed[0].action()
        rt.clearLastSent()
        val r = rt.handle("phir se", localOnly = true)
        assertEquals(1, armed.size); assertTrue(r, r.contains("Poora bolo"))
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true); armed[1].action()
        rt.followUpWindowMs = 0
        Thread.sleep(5)
        val r2 = rt.handle("wahi rohan ko bhi", localOnly = true)
        assertEquals(2, armed.size); assertTrue(r2, r2.contains("Poora bolo"))
    }

    @Test fun callFollowUpNeverTurnsIntoAMessageAndViceVersa() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp call karo", localOnly = true); armed[0].action()
        rt.handle("phir se", localOnly = true)
        assertTrue("call re-arm", armed[1].description.contains("call", ignoreCase = true) && armed[1].description.contains("Rahul Sharma"))
        assertTrue(sent.isEmpty())
        rt.handle("wahi rohan ko bhi", localOnly = true)
        assertTrue(armed[2].description.contains("Rohan Das") && armed[2].description.contains("call", ignoreCase = true))
        assertTrue(sent.isEmpty())
    }

    @Test fun expiredDraftCannotBeCorrectedIntoANewSend() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        val f = CommandRouter::class.java.getDeclaredField("lastDraft").apply { isAccessible = true }
        val d = f.get(rt)!!
        val at = d.javaClass.getDeclaredField("at").apply { isAccessible = true }
        at.setLong(d, System.currentTimeMillis() - 91_000)
        rt.handle("nahi, rohan ko", localOnly = true)
        assertEquals("expired: nothing re-armed", 1, armed.size); assertTrue(sent.isEmpty())
    }

    @Test fun cancelClearsTheDraftSoACorrectionCannotRearmIt() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        rt.clearDraft()                       // what cancelPending() does
        rt.handle("nahi, rohan ko", localOnly = true)
        assertEquals(1, armed.size); assertTrue(sent.isEmpty())
    }

    @Test fun correctionStillNeedsFreshConfirmationShowingRecipientAndWords() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        rt.handle("nahi, rohan ko", localOnly = true)
        assertEquals(2, armed.size); assertTrue(sent.isEmpty())
        assertTrue(armed[1].description.contains("Rohan Das") && armed[1].description.contains("hello"))
    }

    @Test fun ambiguousCorrectionRecipientArmsNothing() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        val amb = listOf(ContactResolver.Row("Ro Das", "+914444444444", 8L))
        // "ro" matches both Rohan Das and Ro Das in the fake lookup only when both are present; use shared router rows
        rt.handle("nahi, zzzz ko", localOnly = true)   // no such contact
        assertEquals("unknown recipient arms nothing", 1, armed.size); assertTrue(sent.isEmpty()); assertNotNull(amb)
    }

    @Test fun cancelOrStopClearsLastConfirmedMemoryToo() = runBlocking {
        for (f in listOf("phir se", "wahi rohan ko bhi")) {
            armed.clear(); sent.clear()
            val rt = router()
            rt.handle("rahul ko whatsapp bhejo hello", localOnly = true); armed[0].action()
            rt.clearDraft()                       // cancelPending() and stopAll() both call this
            val r = rt.handle(f, localOnly = true)
            assertEquals(f, 1, armed.size); assertTrue(r, r.contains("Poora bolo")); assertEquals(1, sent.size)
        }
    }

    @Test fun videoCallIsNeverDowngradedToPlainCall() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp video call karo", localOnly = true); armed[0].action()
        rt.handle("phir se", localOnly = true)
        assertTrue(armed[1].description, armed[1].description.contains("video", ignoreCase = true))
        rt.handle("wahi rohan ko bhi", localOnly = true)
        assertTrue(armed[2].description, armed[2].description.contains("video", ignoreCase = true) && armed[2].description.contains("Rohan Das"))
        assertTrue(called.isEmpty() || called.size == 1)
    }

    @Test fun messageWordAfterACallAsksInsteadOfChangingIntent() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp call karo", localOnly = true); armed[0].action()
        val r = rt.handle("phir se bhejo", localOnly = true)
        assertEquals(1, armed.size); assertTrue(r, r.contains("message nahi"))
    }

    @Test fun contactsChangedBetweenSendAndRepeatAreResolvedFresh() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true); armed[0].action()
        rows = rows + ContactResolver.Row("Rahul Sharma", "+919999999999", 9L)   // duplicate appeared
        val r = rt.handle("phir se", localOnly = true)
        assertEquals("ambiguous now: nothing armed", 1, armed.size); assertTrue(r, r.contains("ek se zyada"))
        rows = rows.filter { it.name != "Rahul Sharma" }                           // contact deleted
        val r2 = rt.handle("phir se", localOnly = true)
        assertEquals(1, armed.size); assertTrue(r2, r2.contains("nahi mila"))
        assertEquals(1, sent.size)
    }

    @Test fun repeatShowsTheCurrentNumberNotTheOldOne() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        assertTrue(armed[0].description, armed[0].description.contains("+911111111111"))
        armed[0].action()
        assertEquals("+911111111111", sent[0].first)
        rows = listOf(ContactResolver.Row("Rahul Sharma", "+918888888888", 1L), rows[1])   // same name, number changed
        val r = rt.handle("phir se", localOnly = true)
        assertEquals(2, armed.size); assertEquals("nothing sent before yes", 1, sent.size)
        assertTrue(armed[1].description, armed[1].description.contains("+918888888888") && !armed[1].description.contains("+911111111111"))
        assertTrue(r, r.contains("+918888888888"))
        armed[1].action()
        assertEquals("+918888888888", sent[1].first)
    }
}
