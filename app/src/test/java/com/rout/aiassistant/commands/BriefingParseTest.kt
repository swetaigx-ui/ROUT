package com.rout.aiassistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BriefingParseTest {
    @Test fun rozSubahWeather() {
        assertEquals(Triple("weather", 7, 0), parseBriefing("roz subah 7 baje weather bata dena"))
    }
    @Test fun rozShaam() {
        val (cmd, h, m) = parseBriefing("har roz shaam 6:30 baje good morning routine sunao")!!
        assertEquals(18, h); assertEquals(30, m); assertEquals("good morning routine", cmd)
    }
    @Test fun englishDaily() {
        assertEquals(Triple("weather", 7, 0), parseBriefing("every day 7am weather"))
    }
    @Test fun eveningDefaultPm() {
        val (_, h, _) = parseBriefing("roz 5 baje weather batao")!!
        assertEquals(17, h)
    }
    @Test fun notDailyIsNull() { assertNull(parseBriefing("kal 7 baje weather batao")) }
    @Test fun noTimeIsNull() { assertNull(parseBriefing("roz weather batao")) }
}
