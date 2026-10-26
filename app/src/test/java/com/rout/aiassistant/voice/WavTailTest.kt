package com.rout.aiassistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class WavTailTest {
    private fun wav(rate: Int, ch: Int, samples: ShortArray): ByteArray {
        val data = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, v -> data[2 * i] = v.toInt().toByte(); data[2 * i + 1] = (v.toInt() shr 8).toByte() }
        val h = ByteArray(44)
        fun w32(o: Int, v: Int) { for (k in 0..3) h[o + k] = (v shr (8 * k)).toByte() }
        fun w16(o: Int, v: Int) { h[o] = v.toByte(); h[o + 1] = (v shr 8).toByte() }
        "RIFF".toByteArray().copyInto(h, 0); w32(4, 36 + data.size); "WAVE".toByteArray().copyInto(h, 8)
        "fmt ".toByteArray().copyInto(h, 12); w32(16, 16); w16(20, 1); w16(22, ch); w32(24, rate); w32(28, rate * 2 * ch); w16(32, 2 * ch); w16(34, 16)
        "data".toByteArray().copyInto(h, 36); w32(40, data.size)
        return h + data
    }
    private fun speechThenTail(rate: Int, speechSec: Double, tailSec: Double, tailAmp: Int): ShortArray {
        val n1 = (rate * speechSec).toInt(); val n2 = (rate * tailSec).toInt()
        val a = ShortArray(n1) { (8000 * sin(2 * PI * 220 * it / rate)).toInt().toShort() }
        val b = ShortArray(n2) { (if (it % 7 < 3) tailAmp else -tailAmp).toShort() }
        return a + b
    }
    @Test fun trimsNoisyTailKeepsPad() {
        val out = WavTail.process(wav(24000, 1, speechThenTail(24000, 1.0, 1.0, 60)))
        val dataBytes = out.size - 44
        val sec = dataBytes / 2.0 / 24000
        assertTrue("trimmed to speech + pad, got $sec", sec in 1.2..1.4)
        assertEquals("RIFF", String(out, 0, 4))
    }
    @Test fun usesHeaderRate() {
        val out = WavTail.process(wav(16000, 1, speechThenTail(16000, 1.0, 1.0, 60)))
        val sec = (out.size - 44) / 2.0 / 16000
        assertTrue("got $sec", sec in 1.2..1.4)
    }
    @Test fun endsAtZero() {
        val out = WavTail.process(wav(24000, 1, speechThenTail(24000, 1.0, 0.0, 0)))
        val n = out.size
        val last = (out[n - 2].toInt() and 0xFF) or (out[n - 1].toInt() shl 8)
        assertEquals(0, last)
    }
    @Test fun shortAndNonWavUntouched() {
        val tiny = wav(24000, 1, ShortArray(1000))
        assertTrue(WavTail.process(tiny).contentEquals(tiny))
        val junk = ByteArray(200) { 1 }
        assertTrue(WavTail.process(junk).contentEquals(junk))
    }

    @Test fun quietEndingAndPauseKept() {
        val r = 24000
        val a = ShortArray(r) { (8000 * sin(2 * PI * 220 * it / r)).toInt().toShort() }
        val pause = ShortArray(r / 2)
        val quiet = ShortArray(r / 4) { (400 * sin(2 * PI * 5000 * it / r)).toInt().toShort() }
        val out = WavTail.process(wav(r, 1, a + pause + quiet))
        assertTrue((out.size - 44) / 2 >= (r * 1.75).toInt())
    }
    @Test fun stereoHandled() {
        val r = 24000
        val mono = speechThenTail(r, 1.0, 1.0, 60)
        val st = ShortArray(mono.size * 2) { mono[it / 2] }
        val out = WavTail.process(wav(r, 2, st))
        val sec = (out.size - 44) / 4.0 / r
        assertTrue("got $sec", sec in 1.2..1.4)
        assertEquals(2, out[22].toInt())
    }
    @Test fun nonPcm16Untouched() {
        val w = wav(24000, 1, speechThenTail(24000, 1.0, 1.0, 60)); w[34] = 8
        assertTrue(WavTail.process(w).contentEquals(w))
    }

    @Test fun unwrapsDoubleWrappedGeminiWavWithC2paTail() {
        val r = 24000
        val inner = wav(r, 1, speechThenTail(r, 1.0, 0.0, 0)) + ByteArray(6000) { 0x7f } // trailing metadata chunk bytes
        // outer header (as old worker built) around the whole inner file
        val outer = wav(r, 1, ShortArray(0)).copyOf(44)
        fun w32(o: Int, v: Int) { for (k in 0..3) outer[o + k] = (v shr (8 * k)).toByte() }
        w32(4, 36 + inner.size); w32(40, inner.size)
        val out = WavTail.process(outer + inner)
        val sec = (out.size - 44) / 2.0 / r
        assertTrue("inner header/metadata must be gone, got $sec", sec in 1.0..1.3)
        // no loud static at the very end
        val n = out.size
        val last = (out[n - 2].toInt() and 0xFF) or (out[n - 1].toInt() shl 8)
        assertEquals(0, last)
    }
}
