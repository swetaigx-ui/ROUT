package com.rout.aiassistant.commands

import org.junit.Assert.assertEquals
import org.junit.Test

class AppAliasTest {
    @Test fun sttShortYoutube() { assertEquals("youtube", DeviceActions.normalizeAppName("\u092f\u0942\u091f\u094d\u092c")) } // यूट्ब
    @Test fun sttYoutubeWithoutYa() { assertEquals("youtube", DeviceActions.normalizeAppName("\u092f\u0942\u091f\u0942\u092c")) } // यूटूब
    @Test fun exactYoutube() { assertEquals("youtube", DeviceActions.normalizeAppName("\u092f\u0942\u091f\u094d\u092f\u0942\u092c")) }
    @Test fun whatsappStillWorks() { assertEquals("whatsapp", DeviceActions.normalizeAppName("\u0935\u094d\u0939\u093e\u091f\u094d\u0938\u090f\u092a")) }
    @Test fun unrelatedDevanagariUnchanged() { assertEquals("\u0930\u093e\u092e", DeviceActions.normalizeAppName("\u0930\u093e\u092e")) } // राम
    @Test fun latinUntouched() { assertEquals("yt", DeviceActions.normalizeAppName("yt")) }
}
