package com.rout.aiassistant

import com.rout.aiassistant.voice.WakeName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phone-observed 15:01: LIVE told the user to start with "Ruby" even after "Ruby open YouTube" -
 * Hindi-mode transcription renders Latin names in Devanagari or near spellings, and the old exact
 * match rejected them, looping the refusal. */
class WakeNameTranscriptionTest {
    @Test fun `latin name at start still works`() {
        assertEquals("open YouTube", WakeName.match("Ruby open YouTube", "Ruby"))
    }
    @Test fun `devanagari transcription of saved latin name is accepted`() {
        assertEquals("यूट्यूब खोलो", WakeName.match("रूबी यूट्यूब खोलो", "Ruby"))
    }
    @Test fun `near spelling from speech is accepted`() {
        assertEquals("open YouTube", WakeName.match("Rubi open YouTube", "Ruby"))
    }
    @Test fun `unrelated word is still rejected`() {
        assertNull(WakeName.match("routine open YouTube", "Ruby"))
        assertNull(WakeName.match("open YouTube", "Ruby"))
    }
    @Test fun `name only returns empty remainder`() {
        assertEquals("", WakeName.match("Ruby", "Ruby"))
    }
}
