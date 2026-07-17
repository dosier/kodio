/*
 * Pure-Kotlin port of lieff/minimp3 (https://github.com/lieff/minimp3).
 * Original work is dedicated to the public domain under CC0 1.0 Universal.
 */
package space.kodio.core.io.files.mp3.minimp3


/**
 * Frame decoder API mirroring minimp3's mp3dec_decode_frame.
 */
internal object MiniMp3 {
    fun init(dec: Mp3dec) {
        dec.header[0] = 0
    }

    /**
     * Decode one frame from [mp3] starting at offset 0 (caller advances by info.frameBytes).
     * @return number of PCM samples per channel produced (0 if sync-only / failure).
     */
    fun decodeFrame(
        dec: Mp3dec,
        mp3: ByteArray,
        mp3Off: Int,
        mp3Bytes: Int,
        pcm: ShortArray?,
        pcmOff: Int,
        info: Mp3decFrameInfo,
    ): Int {
        var i = 0
        var frameSize = 0
        val scratch = Mp3decScratch()
        val bsFrame = BitStream()

        if (mp3Bytes > 4 && (dec.header[0].toInt() and 0xFF) == 0xff &&
            MiniMp3Header.hdrCompare(dec.header, 0, mp3, mp3Off)
        ) {
            frameSize = MiniMp3Header.hdrFrameBytes(mp3, mp3Off, dec.freeFormatBytes) +
                MiniMp3Header.hdrPadding(mp3, mp3Off)
            if (frameSize != mp3Bytes &&
                (frameSize + HDR_SIZE > mp3Bytes ||
                    !MiniMp3Header.hdrCompare(mp3, mp3Off, mp3, mp3Off + frameSize))
            ) {
                frameSize = 0
            }
        }
        if (frameSize == 0) {
            dec.clear()
            val freeFmt = intArrayOf(dec.freeFormatBytes)
            val frameBytesOut = intArrayOf(0)
            // find_frame works on a slice view — pass offset by copying logic with mp3Off
            i = findFrameAt(mp3, mp3Off, mp3Bytes, freeFmt, frameBytesOut)
            dec.freeFormatBytes = freeFmt[0]
            frameSize = frameBytesOut[0]
            if (frameSize == 0 || i + frameSize > mp3Bytes) {
                info.frameBytes = i
                return 0
            }
        }

        val hdrOff = mp3Off + i
        mp3.copyInto(dec.header, 0, hdrOff, hdrOff + HDR_SIZE)
        info.frameBytes = i + frameSize
        info.frameOffset = i
        val hdr = Mp3Hdr.from(mp3, hdrOff)
        info.channels = if (hdr.isMono()) 1 else 2
        info.hz = MiniMp3Header.hdrSampleRateHz(mp3, hdrOff)
        info.layer = 4 - hdr.layerBits()
        info.bitrateKbps = MiniMp3Header.hdrBitrateKbps(mp3, hdrOff)

        if (pcm == null) {
            return MiniMp3Header.hdrFrameSamples(mp3, hdrOff)
        }

        bsFrame.init(mp3, hdrOff + HDR_SIZE, frameSize - HDR_SIZE)
        if (hdr.isCrc()) {
            bsFrame.getBits(16)
        }

        if (info.layer != 3) return 0

        val mainDataBegin = MiniMp3Layer3.readSideInfo(bsFrame, scratch.grInfo, 0, mp3, hdrOff)
        if (mainDataBegin < 0 || bsFrame.pos > bsFrame.limit) {
            init(dec)
            return 0
        }
        val success = MiniMp3Layer3.restoreReservoir(dec, bsFrame, scratch, mainDataBegin)
        if (success) {
            val granules = if (hdr.testMpeg1()) 2 else 1
            var pcmI = pcmOff
            for (igr in 0 until granules) {
                scratch.grbuf.fill(0f)
                MiniMp3Layer3.decode(dec, scratch, scratch.grInfo, igr * info.channels, info.channels)
                MiniMp3Synth.synthGranule(
                    dec.qmfState,
                    scratch.grbuf,
                    18,
                    info.channels,
                    pcm,
                    pcmI,
                    scratch.syn,
                )
                pcmI += 576 * info.channels
            }
        }
        MiniMp3Layer3.saveReservoir(dec, scratch)
        return if (success) MiniMp3Header.hdrFrameSamples(dec.header, 0) else 0
    }

    private fun findFrameAt(
        mp3: ByteArray,
        mp3Off: Int,
        mp3Bytes: Int,
        freeFormatBytes: IntArray,
        ptrFrameBytes: IntArray,
    ): Int {
        // Operate on absolute offsets; MiniMp3Header.findFrame expects offset 0 base.
        // Create a thin wrapper by searching with absolute indices.
        var i = 0
        while (i < mp3Bytes - HDR_SIZE) {
            val abs = mp3Off + i
            if (MiniMp3Header.hdrValid(mp3, abs)) {
                var frameBytes = MiniMp3Header.hdrFrameBytes(mp3, abs, freeFormatBytes[0])
                var frameAndPadding = frameBytes + MiniMp3Header.hdrPadding(mp3, abs)
                var k = HDR_SIZE
                while (frameBytes == 0 && k < MAX_FREE_FORMAT_FRAME_SIZE && i + 2 * k < mp3Bytes - HDR_SIZE) {
                    if (MiniMp3Header.hdrCompare(mp3, abs, mp3, abs + k)) {
                        val fb = k - MiniMp3Header.hdrPadding(mp3, abs)
                        val nextfb = fb + MiniMp3Header.hdrPadding(mp3, abs + k)
                        if (i + k + nextfb + HDR_SIZE > mp3Bytes ||
                            !MiniMp3Header.hdrCompare(mp3, abs, mp3, abs + k + nextfb)
                        ) {
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
                        MiniMp3Header.matchFrame(mp3, abs, mp3Bytes - i, frameBytes)) ||
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

    /**
     * Decode an entire MP3 buffer to interleaved S16LE PCM bytes.
     * Skips a leading ID3v2 tag and a trailing ID3v1 tag when present.
     */
    fun decodeAll(bytes: ByteArray): Triple<ByteArray, Int, Int> {
        val start = skipId3v2(bytes)
        val end = skipId3v1End(bytes, start)
        require(start < end) { "No MPEG audio frames found" }

        val dec = Mp3dec()
        init(dec)
        val info = Mp3decFrameInfo()
        val pcmFrame = ShortArray(MINIMP3_MAX_SAMPLES_PER_FRAME)
        val out = ArrayList<Byte>((end - start) * 8)
        var sampleRate = 0
        var channels = 0
        var offset = start
        while (offset < end) {
            val samples = decodeFrame(
                dec = dec,
                mp3 = bytes,
                mp3Off = offset,
                mp3Bytes = end - offset,
                pcm = pcmFrame,
                pcmOff = 0,
                info = info,
            )
            if (info.frameBytes == 0) break
            if (samples > 0) {
                if (sampleRate == 0) {
                    sampleRate = info.hz
                    channels = info.channels
                }
                val n = samples * info.channels
                for (s in 0 until n) {
                    val v = pcmFrame[s].toInt()
                    out.add((v and 0xFF).toByte())
                    out.add(((v ushr 8) and 0xFF).toByte())
                }
            }
            offset += info.frameBytes
        }
        check(out.isNotEmpty()) { "MP3 decode produced no PCM samples" }
        check(sampleRate > 0 && channels in 1..2) { "invalid MP3 format hz=$sampleRate ch=$channels" }
        return Triple(out.toByteArray(), sampleRate, channels)
    }

    private fun skipId3v2(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() ||
            bytes[1] != 'D'.code.toByte() ||
            bytes[2] != '3'.code.toByte()
        ) {
            return 0
        }
        val size = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        return (10 + size).coerceAtMost(bytes.size)
    }

    private fun skipId3v1End(bytes: ByteArray, start: Int): Int {
        if (bytes.size - start < 128) return bytes.size
        val off = bytes.size - 128
        if (off < start) return bytes.size
        if (bytes[off] == 'T'.code.toByte() &&
            bytes[off + 1] == 'A'.code.toByte() &&
            bytes[off + 2] == 'G'.code.toByte()
        ) {
            return off
        }
        return bytes.size
    }
}
