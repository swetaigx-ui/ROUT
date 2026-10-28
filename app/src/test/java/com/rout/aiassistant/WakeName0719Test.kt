package com.rout.aiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.voice.WakeName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeName0719Test {

    @Test fun defaultBehaviourUnchangedWhenNoNameSaved() {
        assertEquals("youtube kholo", WakeName.match("hey rout youtube kholo", ""))
        assertEquals("youtube kholo", WakeName.match("Hey Route, youtube kholo", ""))
        assertEquals("time batao", WakeName.match("ok rout time batao", ""))
        assertEquals("time batao", WakeName.match("rout time batao", ""))
        assertEquals("", WakeName.match("hey rout", ""))
        assertNull(WakeName.match("youtube kholo", ""))
    }

    @Test fun savedNameReplacesDefaultAndOthersDoNotActivate() {
        assertEquals("call karo", WakeName.match("hey Maya call karo", "Maya"))
        assertEquals("", WakeName.match("Maya", "maya"))
        assertEquals("time batao", WakeName.match("maya, time batao", "Maya"))
        assertNull(WakeName.match("hey rout call karo", "Maya"))     // default no longer addresses it
        assertNull(WakeName.match("hey jarvis call karo", "Maya"))   // another name
    }

    @Test fun noSubstringOrMidSentenceActivation() {
        assertNull(WakeName.match("mayank call karo", "Maya"))
        assertNull(WakeName.match("hey mayank call karo", "Maya"))
        assertNull(WakeName.match("call karo maya", "Maya"))
        assertNull(WakeName.match("kal maya ko bulana", "Maya"))
        assertNull(WakeName.match("routine banao", ""))
        assertNull(WakeName.match("hey routine banao", ""))
        assertNull(WakeName.match("hey maya-ji call karo", "Maya"))
        assertNull(WakeName.match("", "Maya"))
    }

    @Test fun multiWordNameAndSanitising() {
        assertEquals("gaana chalao", WakeName.match("hey meri jaan gaana chalao", "Meri Jaan"))
        assertNull(WakeName.match("hey meri gaana chalao", "Meri Jaan"))
        assertEquals("Meri Jaan", WakeName.sanitize("  Meri   Jaan!! "))
        assertEquals(20, WakeName.sanitize("a".repeat(40)).length)
    }

    @Test fun invalidNamesFallBackToDefault() {
        assertFalse(WakeName.isValid("a"))
        assertFalse(WakeName.isValid("hey"))
        assertFalse(WakeName.isValid("!!"))
        assertTrue(WakeName.isValid("Maya"))
        assertEquals("x", WakeName.match("hey rout x", "hey"))       // saved invalid -> default ROUT still works
    }

    @Test fun saveEditClearPersistsAndIsSeparateFromUserName() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx)
        prefs.setUserName("Gyan")
        assertTrue(prefs.setAssistantName("  Maya! "))
        assertEquals("Maya", prefs.assistantName.first())
        assertEquals("Gyan", prefs.userName.first())
        assertTrue(prefs.setAssistantName("Zara"))                    // edit
        assertEquals("Zara", prefs.assistantName.first())
        assertFalse(prefs.setAssistantName("hey"))                    // invalid: rejected, previous valid value KEPT
        assertFalse(prefs.setAssistantName("a"))
        assertEquals("Zara", prefs.assistantName.first())
        assertTrue(prefs.setAssistantName(""))                        // explicit clear
        assertEquals("", prefs.assistantName.first())
        assertEquals("Gyan", prefs.userName.first())
    }

    @Test fun liveGateAllowsEverythingWithoutNameAndGatesWithName() {
        val g = com.rout.aiassistant.voice.LiveAddressGate()
        assertEquals("youtube kholo", g.accept("youtube kholo", "", 0L))          // default unchanged
        val h = com.rout.aiassistant.voice.LiveAddressGate()
        assertNull(h.accept("youtube kholo", "Maya", 1_000L))                      // not addressed
        assertNull(h.accept("hey rout youtube kholo", "Maya", 2_000L))             // other name
        assertEquals("youtube kholo", h.accept("hey maya youtube kholo", "Maya", 3_000L))
        assertNull(h.accept("rohan ko call karo", "Maya", 4_000L))                 // no follow-up window: a new call must name the assistant
        assertNull(h.accept("rohan ko message bhejo hello", "Maya", 5_000L))
        assertNull(h.accept("maya", "Maya", 6_000L))                               // name alone runs nothing
    }

    @Test fun xTargetIsInsetAwareAndDensityScaled() {
        val d = 2.75f
        val bottom = com.rout.aiassistant.ui.DismissTargetView.bottomOffsetPx(d, 90)
        assertEquals((72f * d).toInt() + 90, bottom)
        val size = com.rout.aiassistant.ui.DismissTargetView.sizePx(d)
        assertTrue(com.rout.aiassistant.ui.DismissTargetView.isOver(540f, 2400f - bottom.toFloat(), 1080, 2400, bottom, size))
        assertFalse(com.rout.aiassistant.ui.DismissTargetView.isOver(540f, 2400f - bottom - 3f * size, 1080, 2400, bottom, size))
        // a higher system inset moves the X up, so a drop at the old position no longer hits
        val higher = com.rout.aiassistant.ui.DismissTargetView.bottomOffsetPx(d, 400)
        assertFalse(com.rout.aiassistant.ui.DismissTargetView.isOver(540f, 2400f - bottom.toFloat(), 1080, 2400, higher, size))
    }
}
