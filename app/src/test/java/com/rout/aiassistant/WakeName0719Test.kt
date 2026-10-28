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
        prefs.setAssistantName("  Maya! ")
        assertEquals("Maya", prefs.assistantName.first())
        assertEquals("Gyan", prefs.userName.first())
        prefs.setAssistantName("Zara")                                // edit
        assertEquals("Zara", prefs.assistantName.first())
        prefs.setAssistantName("hey")                                 // invalid: stored name cleared, default applies
        assertEquals("", prefs.assistantName.first())
        prefs.setAssistantName("Zara"); prefs.setAssistantName("")    // clear
        assertEquals("", prefs.assistantName.first())
        assertEquals("Gyan", prefs.userName.first())
    }
}
