package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit: every capability the Help text claims must be recognized by the local command engine
 * (Latin and Devanagari/STT forms) and NOT fall through to the AI fallback.
 * With no AI configured, the fallback answers "To enable AI answers..." - so that text means "unrouted".
 * This checks ROUTING only; real device effects (accessibility, camera, call dial) are phone-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpClaimsRoutingTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val router by lazy {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {})
    }

    private val claimed = listOf(
        "youtube kholo", "यूट्यूब ओपन करो", "whatsapp kholo", "settings kholo",
        "rahul ko call karo", "राहुल को कॉल करो",
        "mausam batao", "मौसम बताओ", "kitne baje", "कितने बजे", "time batao",
        "alarm lagao 7 baje", "अलार्म लगाओ 7 बजे", "5 minute ka timer lagao", "टाइमर लगाओ 5 मिनट",
        "torch on karo", "batti chalu karo", "बत्ती चालू करो", "flashlight on",
        "note banao: doodh lena", "mere notes batao", "task add karo: bill bharna", "mere tasks batao",
        "remind me to drink water at 6pm", "google par search karo cricket score", "youtube par search karo arijit singh songs",
        "spotify par search karo kesariya", "maps par navigate karo railway station",
        "rahul ko whatsapp karo ki main aa raha hoon", "राहुल को व्हाट्सएप करो कि मैं आ रहा हूँ",
        "kal 5 baje dentist ka appointment rakh do", "good morning", "गुड मॉर्निंग",
        "dnd on karo", "silent mode", "wifi on karo", "bluetooth on karo", "screenshot lo", "स्क्रीनशॉट लो", "स्क्रीनशॉट लो स्क्रीनशॉट लो",
        "45 guna 12", "10 km kitne miles", "100 dollar kitne rupaye", "sikka uchhalo", "dice roll karo",
        "31 december mein kitne din", "chand ki phase", "moon phase", "delhi ka mausam", "delhi ka aqi", "mumbai sunrise kab hai",
        "missed calls batao", "mere messages padho", "email to a@b.com saying hello", "eta to railway station", "mujhe kal 5 baje dawai yaad dilana", "wifi on karo", "timer 10 minute", "battery kitni hai"
    )

    @Test fun helpClaimsAreRouted() {
        val unrouted = mutableListOf<String>()
        for (cmd in claimed) {
            val out = try { runBlocking { router.handle(cmd) } } catch (e: Throwable) { "EXC ${e.javaClass.simpleName}: ${e.message}" }
            println("AUDIT | $cmd => ${out.take(110).replace('\n', ' ')}")
            if ((out.startsWith("To enable AI answers") || out.startsWith("That needs ROUT AI")) || out.startsWith("EXC")) unrouted += "$cmd => ${out.take(80)}"
        }
        println("AUDIT-UNROUTED: ${unrouted.size}\n" + unrouted.joinToString("\n"))
        assertTrue("Unrouted Help claims:\n" + unrouted.joinToString("\n"), unrouted.isEmpty())
    }
}
