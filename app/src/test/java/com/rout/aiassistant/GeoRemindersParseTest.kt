package com.rout.aiassistant

import com.rout.aiassistant.service.GeoReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoRemindersParseTest {

    @Test
    fun english_reach() {
        val r = GeoReminders.parse("remind me to buy milk when I reach the market")
        assertNotNull(r)
        assertEquals("buy milk", r!!.task)
        assertEquals("market", r.place)
        assertFalse(r.onExit)
    }

    @Test
    fun english_leave() {
        val r = GeoReminders.parse("remind me to call mom when I leave office")
        assertNotNull(r)
        assertTrue(r!!.onExit)
        assertEquals("office", r.place)
    }

    @Test
    fun hinglish_jab_pahunchun() {
        val r = GeoReminders.parse("jab main ghar pahunchun to mujhe dawai lene ko yaad dila dena")
        assertNotNull(r)
        assertFalse(r!!.onExit)
        assertEquals("ghar", r.place)
        assertTrue(r.task.contains("dawai"))
    }

    @Test
    fun hinglish_pahunchte_hi() {
        val r = GeoReminders.parse("market pahunchte hi doodh yaad dilana")
        assertNotNull(r)
        assertEquals("market", r!!.place)
        assertEquals("doodh", r.task)
        assertFalse(r.onExit)
    }

    @Test
    fun hinglish_nikalte_hi_exit() {
        val r = GeoReminders.parse("office se nikalte hi yaad dila dena ki gym jaana hai")
        assertNotNull(r)
        assertTrue(r!!.onExit)
    }

    @Test
    fun current_location_marker() {
        val r = GeoReminders.parse("remind me to check the lock when I leave here")
        assertNotNull(r)
        assertTrue(r!!.useCurrentLocation)
        assertTrue(r.onExit)
    }

    @Test
    fun not_a_location_reminder() {
        assertNull(GeoReminders.parse("remind me to buy milk at 5 pm"))
        assertNull(GeoReminders.parse("kya mausam hai"))
        assertNull(GeoReminders.parse("yaad rakho: wifi password 1234"))
    }
}
