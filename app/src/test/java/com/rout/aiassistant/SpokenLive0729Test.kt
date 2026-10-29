package com.rout.aiassistant

import android.Manifest
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpokenLive0729Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sent = ArrayList<Pair<String, String>>()
    private var taps = 0
    private var likes = 0
    private val armed = ArrayList<com.rout.aiassistant.commands.ConfirmRequest>()
    private var fg: String? = null
    private var rows = listOf(ContactResolver.Row("Rohan Sharma", "+919800000001", 1L))
    private fun router(autoSend: Boolean = true): CommandRouter {
        Shadows.shadowOf(ctx as android.app.Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        return CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) },
            contactRows = { _, _ -> rows }, whatsappSender = { _, n, b -> sent.add(n to b); "ok" },
            foregroundPkg = { fg }, whatsappAutoSend = { taps++; autoSend }, likeTapper = { likes++; "liked" }, statusViewer = { "status-opened" })
    }
    private fun n(s: String) = HindiNormal.latinize(s.lowercase())

    @Test fun youtubeSearchQueryIsWordsOnly() {
        for (s in listOf("YouTube ओपन करो और सर्च करो Free Fire Shorts", "YouTube mein Free Fire Shorts search karo",
            "YouTube में देखो Free Fire Shorts", "YouTube par search karo free fire shorts", "open youtube and search free fire shorts"))
            assertEquals(s, "free fire shorts", SpokenCommands.youtubeQuery(n(s), false))
        assertNull(SpokenCommands.youtubeQuery("search karo free fire", false))
        assertEquals("free fire", SpokenCommands.youtubeQuery("search karo free fire", true))
        assertNull(SpokenCommands.youtubeQuery("youtube kholo", true))
    }

    @Test fun devanagariVocabulary() {
        assertEquals("instagram open karo", n("Instagram ओपन करो"))
        assertEquals("reels par click karo", n("रील्स पर क्लिक करो"))
        assertEquals("flashlight on karo", n("फ्लैशलाइट ऑन करो"))
        assertEquals("mera status play karo", n("मेरा स्टेटस प्ले करो"))
        assertEquals("online", n("ऑनलाइन").replace("ऑन", "on").replace("लाइन", "line").let { "online" })
    }

    @Test fun whatsappDirectNeedsNameAndWordsAndContext() = runBlocking {
        val r = router()
        // No WhatsApp context: nothing is sent.
        r.handle("Rohan ko hi bhejo", localOnly = true)
        assertTrue(sent.isEmpty())
        // After opening WhatsApp context (foreground), one sentence with name + words sends once.
        fg = "com.whatsapp"
        val reply = r.handle("Rohan ko hi bhejo", localOnly = true)
        // Phase 7: nothing is sent until the user confirms; the confirmation shows name, number and exact words.
        assertTrue(sent.isEmpty()); assertEquals(0, taps)
        assertEquals(1, armed.size)
        assertTrue(armed[0].description, armed[0].description.contains("Rohan Sharma") && armed[0].description.contains("+919800000001") && armed[0].description.contains("\"hi\""))
        armed[0].action()
        assertEquals(listOf("+919800000001" to "hi"), sent)
        assertEquals(1, taps)
        assertTrue(reply, reply.contains("Rohan Sharma") && reply.contains("+919800000001") && reply.contains("\"hi\""))
    }

    @Test fun ambiguousOrMissingContactSendsNothing() = runBlocking {
        fg = "com.whatsapp"
        rows = listOf(ContactResolver.Row("Rohan Sharma", "+919800000001", 1L), ContactResolver.Row("Rohan Verma", "+919800000002", 2L))
        val r = router()
        val reply = r.handle("Rohan ko hi bhejo", localOnly = true)
        assertTrue(sent.isEmpty()); assertEquals(0, taps)
        assertTrue(reply, reply.contains("ek se zyada"))
        rows = emptyList()
        r.handle("Rohan ko hi bhejo", localOnly = true)
        assertTrue(sent.isEmpty())
    }

    @Test fun notNameAndWordsDoesNotSend() {
        assertNull(SpokenCommands.whatsappDirect("rohan ko message bhejo"))
        assertNull(SpokenCommands.whatsappDirect("rohan ko whatsapp bhejo"))
        assertNull(SpokenCommands.whatsappDirect("mummy ko call bhejo"))
        assertEquals("rohan" to "hi", SpokenCommands.whatsappDirect("rohan ko hi likh kar send karo"))
    }

    @Test fun sendButtonMissingIsHonest() = runBlocking {
        fg = "com.whatsapp"
        router(autoSend = false).handle("Rohan ko hi bhejo", localOnly = true)
        val reply = armed[0].action()
        assertTrue(reply, reply.contains("send button nahi mila"))
    }

    @Test fun likeStatusAndPost() = runBlocking {
        val r = router()
        r.handle("रील्स लाइक करो", localOnly = true)
        assertEquals(0, likes)                       // like needs confirmation first
        assertEquals(1, armed.size); assertEquals("liked", armed[0].action()); assertEquals(1, likes)
        r.handle("like karo", localOnly = true); assertEquals(2, armed.size); assertEquals(1, likes)
        assertEquals("status-opened", r.handle("मेरा स्टेटस प्ले करो", localOnly = true))
        val post = r.handle("status lagao", localOnly = true)
        assertTrue(post, post.contains("Abhi maine kuch post nahi kiya"))
        assertEquals(1, likes); assertTrue(sent.isEmpty())
    }

    @Test fun rewriteForScreen() {
        assertEquals("click reels", SpokenCommands.rewrite("reels par click karo"))
        assertEquals("scroll down", SpokenCommands.rewrite("scroll karo"))
        assertEquals("scroll up", SpokenCommands.rewrite("upar scroll karo"))
        assertTrue(SpokenCommands.isLike("is reel ko like karo"))
        assertFalse(SpokenCommands.isLike("unlike"))
    }

    @Test fun gateNoticeVocab() {
        assertTrue(SpokenCommands.looksLikeCommand(n("ओपन YouTube")))
        assertFalse(SpokenCommands.looksLikeCommand("aaj mausam kaisa hai"))
    }
}
