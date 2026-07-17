/*
 * Pure-Kotlin port of lieff/minimp3 (https://github.com/lieff/minimp3).
 * Original work is dedicated to the public domain under CC0 1.0 Universal.
 */
package space.kodio.core.io.files.mp3.minimp3

internal object MiniMp3Header {
    fun hdrValid(h: ByteArray, off: Int = 0): Boolean {
        if (off + HDR_SIZE > h.size) return false
        val b0 = h[off].toInt() and 0xFF
        val b1 = h[off + 1].toInt() and 0xFF
        val b2 = h[off + 2].toInt() and 0xFF
        return b0 == 0xff &&
            ((b1 and 0xF0) == 0xf0 || (b1 and 0xFE) == 0xe2) &&
            (((b1 ushr 1) and 3) != 0) &&
            ((b2 ushr 4) != 15) &&
            (((b2 ushr 2) and 3) != 3)
    }

    fun hdrCompare(h1: ByteArray, o1: Int, h2: ByteArray, o2: Int): Boolean {
        if (!hdrValid(h2, o2)) return false
        val a1 = h1[o1 + 1].toInt() and 0xFF
        val a2 = h2[o2 + 1].toInt() and 0xFF
        val b1 = h1[o1 + 2].toInt() and 0xFF
        val b2 = h2[o2 + 2].toInt() and 0xFF
        val free1 = (b1 and 0xF0) == 0
        val free2 = (b2 and 0xF0) == 0
        return ((a1 xor a2) and 0xFE) == 0 &&
            ((b1 xor b2) and 0x0C) == 0 &&
            free1 == free2
    }

    fun hdrBitrateKbps(h: ByteArray, off: Int = 0): Int {
        val hdr = Mp3Hdr.from(h, off)
        val mpeg1 = if (hdr.testMpeg1()) 1 else 0
        val layer = hdr.layerBits() - 1
        val br = hdr.bitrateIndex()
        if (layer !in 0..2 || br !in 0..14) return 0
        return 2 * MiniMp3Tables.halfrate[(mpeg1 * 3 + layer) * 15 + br]
    }

    fun hdrSampleRateHz(h: ByteArray, off: Int = 0): Int {
        val hdr = Mp3Hdr.from(h, off)
        val gHz = intArrayOf(44100, 48000, 32000)
        val sr = hdr.sampleRateIndex()
        if (sr !in 0..2) return 0
        var hz = gHz[sr]
        if (!hdr.testMpeg1()) hz = hz shr 1
        if (!hdr.testNotMpeg25()) hz = hz shr 1
        return hz
    }

    fun hdrFrameSamples(h: ByteArray, off: Int = 0): Int {
        val hdr = Mp3Hdr.from(h, off)
        return if (hdr.isLayer1()) 384 else (1152 shr if (hdr.isFrame576()) 1 else 0)
    }

    fun hdrFrameBytes(h: ByteArray, off: Int, freeFormatSize: Int): Int {
        val samples = hdrFrameSamples(h, off)
        val kbps = hdrBitrateKbps(h, off)
        val hz = hdrSampleRateHz(h, off)
        if (hz == 0) return freeFormatSize
        var frameBytes = samples * kbps * 125 / hz
        if (Mp3Hdr.from(h, off).isLayer1()) {
            frameBytes = frameBytes and 3.inv()
        }
        return if (frameBytes != 0) frameBytes else freeFormatSize
    }

    fun hdrPadding(h: ByteArray, off: Int = 0): Int {
        val hdr = Mp3Hdr.from(h, off)
        if (!hdr.testPadding()) return 0
        return if (hdr.isLayer1()) 4 else 1
    }

    fun matchFrame(mp3: ByteArray, hdrOff: Int, mp3Bytes: Int, frameBytes: Int): Boolean {
        var i = 0
        var nmatch = 0
        while (nmatch < MAX_FRAME_SYNC_MATCHES) {
            i += hdrFrameBytes(mp3, hdrOff + i, frameBytes) + hdrPadding(mp3, hdrOff + i)
            if (i + HDR_SIZE > mp3Bytes) return nmatch > 0
            if (!hdrCompare(mp3, hdrOff, mp3, hdrOff + i)) return false
            nmatch++
        }
        return true
    }

    /**
     * @return pair of (frame offset into [mp3], frame byte size including padding).
     * Updates [freeFormatBytes] in-place via the IntArray singleton holder.
     */
    fun findFrame(
        mp3: ByteArray,
        mp3Bytes: Int,
        freeFormatBytes: IntArray,
        ptrFrameBytes: IntArray,
    ): Int {
        var i = 0
        while (i < mp3Bytes - HDR_SIZE) {
            if (hdrValid(mp3, i)) {
                var frameBytes = hdrFrameBytes(mp3, i, freeFormatBytes[0])
                var frameAndPadding = frameBytes + hdrPadding(mp3, i)
                var k = HDR_SIZE
                while (frameBytes == 0 && k < MAX_FREE_FORMAT_FRAME_SIZE && i + 2 * k < mp3Bytes - HDR_SIZE) {
                    if (hdrCompare(mp3, i, mp3, i + k)) {
                        val fb = k - hdrPadding(mp3, i)
                        val nextfb = fb + hdrPadding(mp3, i + k)
                        if (i + k + nextfb + HDR_SIZE > mp3Bytes || !hdrCompare(mp3, i, mp3, i + k + nextfb)) {
                            k++
                            continue
                        }
                        frameAndPadding = k
                        frameBytes = fb
                        freeFormatBytes[0] = fb
                    }
                    k++
                }
                if (
                    (frameBytes != 0 && i + frameAndPadding <= mp3Bytes &&
                        matchFrame(mp3, i, mp3Bytes - i, frameBytes)) ||
                    (i == 0 && frameAndPadding == mp3Bytes)
                ) {
                    ptrFrameBytes[0] = frameAndPadding
                    return i
                }
                freeFormatBytes[0] = 0
            }
            i++
        }
        ptrFrameBytes[0] = 0
        return mp3Bytes
    }
}
