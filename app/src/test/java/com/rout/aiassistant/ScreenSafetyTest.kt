package com.rout.aiassistant

import com.rout.aiassistant.commands.ScreenControl
import com.rout.aiassistant.service.ScreenSafety
import org.junit.Assert.*
import org.junit.Test

class ScreenSafetyTest {

    @Test
    fun sensitiveMarkersEnglish() {
        assertTrue(ScreenSafety.isSensitive("enter your otp to continue"))
        assertTrue(ScreenSafety.isSensitive("card number 4111 cvv"))
        assertTrue(ScreenSafety.isSensitive("proceed to payment"))
        assertTrue(ScreenSafety.isSensitive("enter upi pin"))
        assertTrue(ScreenSafety.isSensitive("password"))
    }

    @Test
    fun sensitiveMarkersHinglishAndDevanagari() {
        assertTrue(ScreenSafety.isSensitive("otp daalo yahan"))
        assertTrue(ScreenSafety.isSensitive("pin dalo"))
        assertTrue(ScreenSafety.isSensitive("कृपया ओटीपी दर्ज करें"))
        assertTrue(ScreenSafety.isSensitive("अपना पासवर्ड डालें"))
        assertTrue(ScreenSafety.isSensitive("भुगतान करें"))
    }

    @Test
    fun benignScreensAreNotBlocked() {
        assertFalse(ScreenSafety.isSensitive("youtube trending videos music lofi"))
        assertFalse(ScreenSafety.isSensitive("settings display brightness wallpaper"))
        assertFalse(ScreenSafety.isSensitive("kal ka mausam accha hai"))
    }

    @Test
    fun redactionCoversCodesContactsCards() {
        val out = ScreenSafety.redact("OTP 482910, call 9876543210 or a.b@x.com, card 4111 1111 1111 1111")
        assertFalse(out.contains("482910"))
        assertFalse(out.contains("9876543210"))
        assertFalse(out.contains("a.b@x.com"))
        assertFalse(out.contains("4111"))
        assertTrue(out.contains("[code]") || out.contains("[number]"))
        assertTrue(out.contains("[phone]"))
        assertTrue(out.contains("[email]"))
    }

    @Test
    fun parseBasicIntents() {
        assertEquals(ScreenControl.Intent.Back, ScreenControl.parse("back dabao"))
        assertEquals(ScreenControl.Intent.Back, ScreenControl.parse("wapas jao"))
        assertEquals(ScreenControl.Intent.Home, ScreenControl.parse("home jao"))
        assertEquals(ScreenControl.Intent.Recents, ScreenControl.parse("recent apps kholo"))
        assertEquals(ScreenControl.Intent.Scroll(true), ScreenControl.parse("scroll down"))
        assertEquals(ScreenControl.Intent.Scroll(true), ScreenControl.parse("neeche karo"))
        assertEquals(ScreenControl.Intent.Scroll(false), ScreenControl.parse("upar karo"))
        assertEquals(ScreenControl.Intent.Tap("login"), ScreenControl.parse("tap login"))
        assertEquals(ScreenControl.Intent.Tap("login"), ScreenControl.parse("login dabao"))
        assertEquals(ScreenControl.Intent.ReadScreenLocal, ScreenControl.parse("screen pe kya hai"))
        val q = ScreenControl.parse("screen ke baare mein poochho: ye form kiska hai")
        assertTrue(q is ScreenControl.Intent.AskAiAboutScreen)
        assertNull(ScreenControl.parse("youtube kholo"))       // not a screen command
        assertNull(ScreenControl.parse("weather batao"))
    }

    @Test
    fun sendLikeTargetsNeedConfirmation() {
        assertTrue(ScreenControl.isSendLike("send"))
        assertTrue(ScreenControl.isSendLike("bhejo button"))
        assertTrue(ScreenControl.isSendLike("pay now"))
        assertFalse(ScreenControl.isSendLike("login"))
        assertFalse(ScreenControl.isSendLike("search icon"))
    }
}
