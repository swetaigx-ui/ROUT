package com.rout.aiassistant

import com.rout.aiassistant.commands.ScreenControl
import com.rout.aiassistant.commands.ScreenControl.Intent
import org.junit.Assert.*
import org.junit.Test

class ScreenControl078Test {
    @Test fun globalActionsParse() {
        assertTrue(ScreenControl.parse("notifications kholo") is Intent.Notifications)
        assertTrue(ScreenControl.parse("quick settings kholo") is Intent.QuickSettings)
        assertTrue(ScreenControl.parse("screen lock karo") is Intent.LockScreen)
        assertTrue(ScreenControl.parse("screenshot lo") is Intent.SystemScreenshot)
    }
    @Test fun gestureAndTextParse() {
        assertEquals("message", (ScreenControl.parse("long press message") as Intent.LongPress).target)
        assertEquals("hello", (ScreenControl.parse("type karo hello") as Intent.TypeText).text)
        assertEquals("send", (ScreenControl.parse("wait for send") as Intent.WaitFor).text)
        assertEquals("left", (ScreenControl.parse("swipe left") as Intent.Swipe).dir)
    }
    @Test fun ordinaryChatAndNotesNotHijacked() {
        assertNull(ScreenControl.parse("kal ka mausam kaisa hai"))
        assertNull(ScreenControl.parse("note likho doodh lana"))
        assertNull(ScreenControl.parse("mujhe ek joke sunao"))
    }
    @Test fun sendLikeStillGuarded() {
        assertTrue(ScreenControl.isSendLike("send"))
        assertTrue(ScreenControl.isSendLike("pay now"))
    }
}
