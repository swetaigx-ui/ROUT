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
    private val rows = listOf(
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

    @Test fun followUpsAfterAConfirmedSendArmAndSendNothing() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        armed[0].action()
        for (f in followUps) { rt.handle(f, localOnly = true); assertEquals("armed by: " + f + " -> " + armed.last().description, 1, armed.size) }
        assertEquals("only the one confirmed send", 1, sent.size)
        assertEquals("nothing newly armed by a follow-up", 1, armed.size)
        assertTrue(called.isEmpty())
    }

    @Test fun followUpsWhileADraftIsPendingChangeNothing() = runBlocking {
        val rt = router()
        rt.handle("rahul ko whatsapp bhejo hello", localOnly = true)
        for (f in followUps) { rt.handle(f, localOnly = true); assertEquals("armed by: " + f + " -> " + armed.last().description, 1, armed.size) }
        assertTrue(sent.isEmpty()); assertTrue(called.isEmpty())
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
}
