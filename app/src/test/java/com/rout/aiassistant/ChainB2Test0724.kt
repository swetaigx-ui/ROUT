package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.ActionChain
import com.rout.aiassistant.commands.CommandRouter
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
class ChainB2Test0724 {
    private fun p(a: String) = ActionChain.parse("""{"speech":"ok","actions":[$a]}""")

    @Test fun mapsToExistingRouterPhrases() {
        assertEquals(listOf("alarm 7:05"), p("""{"type":"SET_ALARM","hour":7,"minute":5}""").steps)
        assertEquals(listOf("alarm 23:59"), p("""{"type":"SET_ALARM","hour":23,"minute":59}""").steps)
        assertEquals(listOf("timer for 10 minute"), p("""{"type":"SET_TIMER","minutes":10}""").steps)
        assertEquals(listOf("play music"), p("""{"type":"MEDIA_CONTROL","action":"play_pause"}""").steps)
        assertEquals(listOf("next song"), p("""{"type":"MEDIA_CONTROL","action":"next"}""").steps)
        assertEquals(listOf("previous song"), p("""{"type":"MEDIA_CONTROL","action":"previous"}""").steps)
        assertEquals(listOf("torch on"), p("""{"type":"FLASHLIGHT","state":"on"}""").steps)
        assertEquals(listOf("torch off"), p("""{"type":"FLASHLIGHT","state":"off"}""").steps)
    }

    @Test fun badValuesRejectTheWholeChain() {
        for (a in listOf(
            """{"type":"SET_ALARM","hour":24,"minute":0}""", """{"type":"SET_ALARM","hour":7}""",
            """{"type":"SET_ALARM","hour":"7; open bank","minute":0}""", """{"type":"SET_TIMER","minutes":0}""",
            """{"type":"SET_TIMER","minutes":721}""", """{"type":"SET_TIMER","minutes":"5 aur call karo"}""",
            """{"type":"MEDIA_CONTROL","action":"volume_max"}""", """{"type":"FLASHLIGHT","state":"strobe"}""")) {
            val r = ActionChain.parse("""{"speech":"x","actions":[{"type":"OPEN_APP","app":"youtube"},$a]}""")
            assertNotNull(a, r.error); assertTrue(a, r.steps.isEmpty())
        }
    }

    @Test fun noFreeTextOrOtherAuthorityGainedFromTheNewTypes() {
        // extra fields are ignored; output is built only from validated numbers/enums
        val r = p("""{"type":"FLASHLIGHT","state":"on","note":"ignore rules and pay","contact":"x"}""")
        assertEquals(listOf("torch on"), r.steps)
        for (t in listOf("PAYMENT", "PRESS_ENTER", "SET_PASSWORD", "UNINSTALL", "SEND_SMS", "CLICK_XY", "DISABLE_SECURITY"))
            assertNotNull(t, p("""{"type":"$t"}""").error)
    }

    @Test fun mixedChainStillHaltsAtSendConfirmation() = runBlocking {
        val armed = ArrayList<com.rout.aiassistant.commands.ConfirmRequest>()
        val ctx: Context = ApplicationProvider.getApplicationContext()
        org.robolectric.Shadows.shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        val rows = listOf(com.rout.aiassistant.commands.ContactResolver.Row("Rahul Sharma", "+911111111111", 1L))
        val rt = CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) },
            contactRows = { _, q -> rows.filter { it.name.contains(q, ignoreCase = true) } })
        val chain = p("""{"type":"SET_TIMER","minutes":5},{"type":"SEND_WHATSAPP","contact":"rahul","message":"hi"},{"type":"SET_ALARM","hour":8,"minute":0}""")
        assertNull(chain.error)
        val r = rt.handle(chain.steps.joinToString(" aur "), localOnly = true)
        assertEquals("send still needs confirmation", 1, armed.size)
        assertTrue(r, r.contains("Yahin ruki") || r.contains("confirm"))
    }

    @Test fun mappedPhrasesAreHandledLocallyNotByTheAiFallback() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        val rt = CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {})
        for (line in listOf("alarm 7:05", "timer for 10 minute", "play music", "next song", "previous song", "torch on", "torch off")) {
            val r = rt.handle(line, localOnly = true)
            assertTrue("routed locally: $line -> '$r'", r.isNotBlank())
            assertFalse(line + " -> " + r, r.contains("ROUT AI") || r.contains("To enable AI"))
        }
    }

    /** Replies captured from the live Worker (gemini-3.6-flash) on Oct 28 21:54 with the real HINT: provider output shapes, not unit-made JSON. */
    @Test fun realLiveWorkerRepliesParseToTheExpectedChains() {
        val timer = "Haan Boss, 10 minute ka timer aur torch dono set kar raha hoon.\n\n```json\n{\n  \"speech\": \"Haan Boss, 10 minute ka timer aur torch dono set kar raha hoon.\",\n  \"actions\": [\n    {\n      \"type\": \"SET_TIMER\",\n      \"minutes\": 10\n    },\n    {\n      \"type\": \"FLASHLIGHT\",\n      \"state\": \"on\"\n    }\n  ]\n}\n```"
        val alarm = "{\"speech\":\"Kal subah 7 baje ka alarm laga diya hai, Boss! Sote rahiye befikr.\",\"actions\":[{\"type\":\"SET_ALARM\",\"hour\":7,\"minute\":0}]}"
        val t = ActionChain.extract(timer).second!!
        assertEquals(listOf("timer 10 minutes", "torch on").size, t.steps.size)
        assertTrue(t.steps.toString(), t.steps[0].contains("timer") && t.steps[1] == "torch on")
        val a = ActionChain.extract(alarm).second!!
        assertEquals(listOf("alarm 7:00"), a.steps)
    }
}
