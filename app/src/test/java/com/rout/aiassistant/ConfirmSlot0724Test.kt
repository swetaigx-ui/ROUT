package com.rout.aiassistant

import com.rout.aiassistant.commands.ConfirmRequest
import com.rout.aiassistant.commands.ConfirmSlot
import com.rout.aiassistant.commands.ConfirmSlot.Take
import org.junit.Assert.*
import org.junit.Test

class ConfirmSlot0724Test {
    private var t = 0L
    private val slot = ConfirmSlot { t }
    private fun img(tag: String = "i") = ConfirmRequest("screen-image", tag, byteArrayOf(9, 9)) { "ran-$tag" }
    private fun txt() = ConfirmRequest("screen-cloud", "t") { "ran-t" }
    private fun sms() = ConfirmRequest("sms", "s") { "ran-s" }

    @Test fun spokenYesIsRefusedForImageAndTextPreviewsAndTheRequestStays() {
        slot.arm(img()); assertEquals(Take.NeedsButton, slot.take(true)); assertNotNull(slot.state.value)
        slot.arm(txt()); assertEquals(Take.NeedsButton, slot.take(true)); assertNotNull(slot.state.value)
    }

    @Test fun buttonConfirmRunsOnceThenSlotIsEmpty() {
        slot.arm(img())
        val r = slot.take(false) as Take.Run
        assertEquals("i", r.req.description)
        assertNull(slot.state.value); assertEquals(Take.None, slot.take(false))
    }

    @Test fun ordinarySendConfirmStillAcceptsVoice() {
        slot.arm(sms()); assertTrue(slot.take(true) is Take.Run)
    }

    @Test fun expiryDropsTheReferenceAndTheBytes() {
        slot.arm(img()); t = ConfirmSlot.IMAGE_TTL_MS - 1
        assertFalse(slot.expireIfDue()); assertNotNull(slot.state.value)
        t = ConfirmSlot.IMAGE_TTL_MS
        assertTrue(slot.expireIfDue()); assertNull(slot.state.value)           // dropped, not merely refused later
        slot.arm(img()); t += ConfirmSlot.IMAGE_TTL_MS + 5
        assertEquals(Take.Expired, slot.take(false)); assertNull(slot.state.value)
    }

    @Test fun ordinaryConfirmsDoNotExpireHere() {
        slot.arm(sms()); t = 10 * ConfirmSlot.IMAGE_TTL_MS
        assertFalse(slot.expireIfDue()); assertTrue(slot.take(false) is Take.Run)
    }

    @Test fun newerRequestReplacesOlderPreviewSoOldOneCannotConfirm() {
        slot.arm(img("old")); slot.arm(img("new"))
        assertEquals("new", (slot.take(false) as Take.Run).req.description)
        assertEquals(Take.None, slot.take(false))
    }

    @Test fun cancelAndStopClear() {
        slot.arm(img()); slot.clear()
        assertNull(slot.state.value); assertEquals(Take.None, slot.take(false))
    }

    @Test fun replacementAfterExpiryStartsAFreshWindow() {
        slot.arm(img("a")); t = 80_000
        slot.arm(img("b")); t = 150_000                     // 70s after b, 150s after a
        assertTrue(slot.take(false) is Take.Run)
    }
}
