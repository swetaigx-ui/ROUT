package com.rout.aiassistant.voice

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure WAV post-processing for spoken replies (16-bit PCM only; anything else is returned untouched):
 *  - reads the real sample rate / channel count from the header (no 24 kHz mono assumption),
 *  - trims the quiet/noisy tail after the last real speech energy (keeps a short natural pad),
 *  - applies a short fade-in and a longer fade-out so there is no click at either end.
 * Result is always a valid RIFF/WAVE with corrected chunk sizes.
 */
object WavTail {
    private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    fun process(wav: ByteArray, padMs: Int = 260, fadeOutMs: Int = 90, fadeInMs: Int = 12): ByteArray {
        if (wav.size < 64 || String(wav, 0, 4) != "RIFF" || String(wav, 8, 4) != "WAVE") return wav
        var off = 12
        var channels = 1; var rate = 24000; var bits = 16; var fmtSeen = false
        var dataOff = -1; var dataLen = 0
        while (off + 8 <= wav.size) {
            val id = String(wav, off, 4)
            val len = le32(wav, off + 4)
            if (id == "fmt " && off + 8 + 16 <= wav.size) {
                channels = max(1, le16(wav, off + 10)); rate = le32(wav, off + 12); bits = le16(wav, off + 22); fmtSeen = true
            }
            if (id == "data") {
                dataOff = off + 8
                dataLen = if (len < 0 || len > wav.size - dataOff) wav.size - dataOff else len
                break
            }
            if (len < 0) return wav
            off += 8 + len + (len and 1)
        }
        if (!fmtSeen || bits != 16 || dataOff < 0 || rate < 8000) return wav
        val frameBytes = 2 * channels
        dataLen -= dataLen % frameBytes
        if (dataLen < frameBytes * rate / 4) return wav // under 250 ms: leave alone
        val pcm = wav.copyOfRange(dataOff, dataOff + dataLen)
        fun s(i: Int): Int = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
        fun put(i: Int, v: Int) { val c = v.coerceIn(-32768, 32767); pcm[i] = (c and 0xFF).toByte(); pcm[i + 1] = ((c shr 8) and 0xFF).toByte() }
        // Energy per 10 ms window (all channels)
        val winFrames = max(1, rate / 100)
        val winBytes = winFrames * frameBytes
        val windows = dataLen / winBytes
        val rms = DoubleArray(windows)
        var peakRms = 0.0
        for (w in 0 until windows) {
            var acc = 0.0; var n = 0
            var i = w * winBytes
            while (i + 1 < (w + 1) * winBytes) { val v = s(i).toDouble(); acc += v * v; n++; i += 2 }
            rms[w] = sqrt(acc / max(1, n)); peakRms = max(peakRms, rms[w])
        }
        // Last window that is genuinely speech: >= 4% of loudest window (and above a floor)
        val thr = max(peakRms * 0.015, 40.0)
        var last = windows - 1
        while (last > 0 && rms[last] < thr) last--
        val keepFrames = min(dataLen / frameBytes, ((last + 1) * winFrames) + rate * padMs / 1000)
        val keepBytes = keepFrames * frameBytes
        val out = pcm.copyOf(keepBytes)
        // fades on the kept part
        fun sOut(i: Int): Int = (out[i].toInt() and 0xFF) or (out[i + 1].toInt() shl 8)
        fun putOut(i: Int, v: Int) { val c = v.coerceIn(-32768, 32767); out[i] = (c and 0xFF).toByte(); out[i + 1] = ((c shr 8) and 0xFF).toByte() }
        val inFrames = min(keepFrames / 4, rate * fadeInMs / 1000)
        for (f in 0 until inFrames) for (c in 0 until channels) {
            val i = (f * channels + c) * 2; putOut(i, (sOut(i).toLong() * f / max(1, inFrames)).toInt())
        }
        val outFrames = min(keepFrames / 3, rate * fadeOutMs / 1000)
        for (f in 0 until outFrames) for (c in 0 until channels) {
            val i = ((keepFrames - 1 - f) * channels + c) * 2
            putOut(i, (sOut(i).toLong() * f / max(1, outFrames)).toInt())
        }
        // Rebuild a canonical 44-byte header
        val total = 44 + out.size
        val h = ByteArray(44)
        fun w32(o: Int, v: Int) { h[o] = v.toByte(); h[o + 1] = (v shr 8).toByte(); h[o + 2] = (v shr 16).toByte(); h[o + 3] = (v shr 24).toByte() }
        fun w16(o: Int, v: Int) { h[o] = v.toByte(); h[o + 1] = (v shr 8).toByte() }
        "RIFF".toByteArray().copyInto(h, 0); w32(4, total - 8); "WAVE".toByteArray().copyInto(h, 8)
        "fmt ".toByteArray().copyInto(h, 12); w32(16, 16); w16(20, 1); w16(22, channels); w32(24, rate)
        w32(28, rate * frameBytes); w16(32, frameBytes); w16(34, 16)
        "data".toByteArray().copyInto(h, 36); w32(40, out.size)
        return h + out
    }
    @Suppress("unused") private fun unused(x: Int) = abs(x)
}
