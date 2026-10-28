package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.commands.ConfirmRequest
import com.rout.aiassistant.commands.ReplyTarget
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotifReplyOff0724Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val armed = ArrayList<ConfirmRequest>()
    private var targets: List<ReplyTarget> = emptyList()
    private val sent = ArrayList<Pair<ReplyTarget, String>>()
    private var read = 0
    private val router by lazy {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) },
            notifTargets = { read++; targets }, notifReplier = { t, w -> sent.add(t to w); true })
    }
    @org.junit.After fun reset() { runBlocking { Prefs(ctx).setNotifReplyAllowed(false) } }
    private val rohan = ReplyTarget("k1", "com.whatsapp", "WhatsApp", "Rohan Sharma", false)
    private val grp = ReplyTarget("k2", "com.whatsapp", "WhatsApp", "Rohan", true)

    @Test fun notificationReplyIsOffAndArmsNothing() = runBlocking {
        for (c in listOf("notification ko reply karo hello", "notification par reply bhejo ok", "reply to this notification: yes", "notification se reply karo"))
            assertEquals(c, CommandRouter.NOTIF_REPLY_OFF, router.handle(c, localOnly = true))
        assertTrue(armed.isEmpty())
    }

    @Test fun offReadsNoNotificationAndSendsNothing() = runBlocking {
        targets = listOf(rohan)
        router.handle("notification reply to Rohan: aa raha hu", localOnly = true)
        assertEquals(0, read); assertTrue(armed.isEmpty()); assertTrue(sent.isEmpty())
    }

    @Test fun onArmsFreshConfirmationWithAppSenderWordsAndSendsOnlyOnConfirm() = runBlocking {
        Prefs(ctx).setNotifReplyAllowed(true); targets = listOf(rohan)
        val r = router.handle("notification reply to Rohan: aa raha hu", localOnly = true)
        assertTrue(r, r.contains("Confirm on screen"))
        assertEquals(1, armed.size); assertTrue(sent.isEmpty())
        val d = armed[0].description
        assertTrue(d, d.contains("WhatsApp") && d.contains("Rohan Sharma") && d.contains("aa raha hu") && d.contains("com.whatsapp") && d.contains("Thread: "))
        val out = armed[0].action()
        assertEquals(1, sent.size); assertEquals("aa raha hu", sent[0].second); assertTrue(out, out.contains("Reply bhej diya"))
    }

    @Test fun ambiguousGroupAndMissingThreadsArmNothing() = runBlocking {
        Prefs(ctx).setNotifReplyAllowed(true)
        targets = listOf(rohan, ReplyTarget("k3", "com.instagram", "Instagram", "Rohan Verma", false))
        assertTrue(router.handle("notification reply to Rohan: hi", localOnly = true).contains("ek se zyada"))
        targets = listOf(grp)
        assertTrue(router.handle("notification reply to Rohan: hi", localOnly = true).contains("group"))
        targets = listOf(rohan)
        assertTrue(router.handle("notification reply to Amit: hi", localOnly = true).contains("nahi mili"))
        assertTrue(router.handle("notification reply to Rohan:", localOnly = true).contains("Poora bolo"))
        assertTrue(armed.isEmpty()); assertTrue(sent.isEmpty())
    }

    @Test fun threadChangedBetweenConfirmAndSendIsRefused() = runBlocking {
        Prefs(ctx).setNotifReplyAllowed(true); targets = listOf(rohan)
        router.handle("notification reply to Rohan: hi", localOnly = true)
        targets = listOf(rohan.copy(sender = "Rohit"))           // same key, different person
        assertTrue(armed[0].action().contains("badal gayi"))
        targets = listOf(rohan.copy(isGroup = true))
        assertTrue(armed[0].action().contains("badal gayi"))
        targets = emptyList()
        assertTrue(armed[0].action().contains("badal gayi"))
        assertTrue(sent.isEmpty())
    }

    @Test fun turningItOffBeforeConfirmRefuses() = runBlocking {
        Prefs(ctx).setNotifReplyAllowed(true); targets = listOf(rohan)
        router.handle("notification reply to Rohan: hi", localOnly = true)
        Prefs(ctx).setNotifReplyAllowed(false)
        assertEquals(CommandRouter.NOTIF_REPLY_OFF, armed[0].action()); assertTrue(sent.isEmpty())
    }
}
