package com.rout.aiassistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class Phase1ParseTest {
    @Test fun waNameKoKaro() {
        assertEquals("rahul" to "main aa raha hoon", parseWhatsapp("rahul ko whatsapp karo ki main aa raha hoon"))
    }
    @Test fun waPrefix() {
        assertEquals("rahul" to "hello bhai", parseWhatsapp("whatsapp rahul ki hello bhai"))
    }
    @Test fun waDevanagari() {
        assertEquals("राहुल" to "मैं आ रहा हूँ", parseWhatsapp("राहुल को व्हाट्सएप करो कि मैं आ रहा हूँ"))
    }
    @Test fun waNoMessageIsNull() { assertNull(parseWhatsapp("rahul ko whatsapp karo")) }
    @Test fun waOpenCommandIsNull() { assertNull(parseWhatsapp("whatsapp kholo")) }

    private fun now(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 25, 9, 0, 0); set(Calendar.MILLISECOND, 0)
    }

    @Test fun calHindiEvening() {
        val (title, start, _) = parseCalendar("kal 5 baje dentist ka appointment rakh do", now())!!
        assertTrue(title.contains("dentist", true))
        val c = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(26, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, c.get(Calendar.HOUR_OF_DAY))
    }
    @Test fun calEnglish() {
        val (title, start, _) = parseCalendar("schedule team meeting tomorrow 6pm", now())!!
        assertTrue(title.isNotBlank())
        val c = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(18, c.get(Calendar.HOUR_OF_DAY))
    }
    @Test fun calTodayMorning() {
        val (_, start, _) = parseCalendar("aaj subah 7 baje walk ka event banao", now())!!
        val c = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(7, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(25, c.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun calNoCueIsNull() { assertNull(parseCalendar("kal 5 baje", now())) }
    @Test fun calNoTimeIsNull() { assertNull(parseCalendar("kal meeting rakh do", now())) }
    @Test fun calEndIsOneHourLater() {
        val (_, start, end) = parseCalendar("kal 5 baje dentist ka appointment rakh do", now())!!
        assertEquals(3600_000L, end - start)
    }
}

class ReminderAltTest {
    private fun now(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 26, 9, 0, 0); set(Calendar.MILLISECOND, 0)
    }
    @Test fun kalBaje() {
        val (title, c) = parseReminderAlt("mujhe kal 5 baje dawai yaad dilana", now())!!
        assertEquals("dawai", title)
        assertEquals(27, c.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun shaamBaje() {
        val (_, c) = parseReminderAlt("aaj shaam 7 baje chai ki yaad dilao", now())!!
        assertEquals(19, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(26, c.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun nonReminderIsNull() { assertNull(parseReminderAlt("mausam batao", now())) }
}
