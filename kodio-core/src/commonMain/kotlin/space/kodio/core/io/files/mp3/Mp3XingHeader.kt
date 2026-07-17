package space.kodio.core.io.files.mp3

/**
 * Xing / Info / VBRI VBR header used for duration and average bitrate.
 */
internal data class Mp3XingHeader(
    val frames: Int?,
    val bytes: Int?,
    val vbr: Boolean,
    val encoderDelay: Int? = null,
    val encoderPadding: Int? = null,
) {
    companion object {
        fun tryParse(bytes: ByteArray, frameOffset: Int, header: Mp3FrameHeader): Mp3XingHeader? {
            parseXingOrInfo(bytes, frameOffset, header)?.let { return it }
            return parseVbri(bytes, frameOffset, header)
        }

        private fun parseXingOrInfo(
            bytes: ByteArray,
            frameOffset: Int,
            header: Mp3FrameHeader,
        ): Mp3XingHeader? {
            val sideInfo = sideInfoSize(header)
            val tagOffset = frameOffset + 4 + sideInfo
            if (tagOffset + 8 > bytes.size) return null
            val tag = bytes.decodeToString(tagOffset, tagOffset + 4)
            if (tag != "Xing" && tag != "Info") return null

            var pos = tagOffset + 4
            if (pos + 4 > bytes.size) return null
            val flags = readIntBe(bytes, pos)
            pos += 4

            var frames: Int? = null
            var byteCount: Int? = null
            if ((flags and 0x1) != 0) {
                if (pos + 4 > bytes.size) return null
                frames = readIntBe(bytes, pos)
                pos += 4
            }
            if ((flags and 0x2) != 0) {
                if (pos + 4 > bytes.size) return null
                byteCount = readIntBe(bytes, pos)
                pos += 4
            }
            if ((flags and 0x4) != 0) {
                pos += 100 // TOC
            }
            if ((flags and 0x8) != 0) {
                pos += 4 // quality
            }

            var delay: Int? = null
            var padding: Int? = null
            // LAME extension (optional)
            if (pos + 20 <= bytes.size) {
                val encoder = bytes.decodeToString(pos, pos + 4)
                if (encoder.startsWith("LAME") || encoder.startsWith("Lavf") || bytes[pos].toInt() != 0) {
                    val delayPadOffset = pos + 21
                    if (delayPadOffset + 3 <= bytes.size) {
                        val b0 = bytes[delayPadOffset].toInt() and 0xFF
                        val b1 = bytes[delayPadOffset + 1].toInt() and 0xFF
                        val b2 = bytes[delayPadOffset + 2].toInt() and 0xFF
                        delay = (b0 shl 4) or (b1 ushr 4)
                        padding = ((b1 and 0x0F) shl 8) or b2
                    }
                }
            }

            return Mp3XingHeader(
                frames = frames,
                bytes = byteCount,
                vbr = tag == "Xing",
                encoderDelay = delay,
                encoderPadding = padding,
            )
        }

        private fun parseVbri(
            bytes: ByteArray,
            frameOffset: Int,
            header: Mp3FrameHeader,
        ): Mp3XingHeader? {
            // VBRI is at a fixed offset 32 bytes after the frame header for Layer III
            if (header.layer != 3) return null
            val tagOffset = frameOffset + 4 + 32
            if (tagOffset + 26 > bytes.size) return null
            val tag = bytes.decodeToString(tagOffset, tagOffset + 4)
            if (tag != "VBRI") return null
            val bytesTotal = readIntBe(bytes, tagOffset + 10)
            val frames = readIntBe(bytes, tagOffset + 14)
            return Mp3XingHeader(
                frames = frames,
                bytes = bytesTotal,
                vbr = true,
            )
        }

        private fun sideInfoSize(header: Mp3FrameHeader): Int {
            return when {
                header.mpegVersion == 1 && header.channels == 1 -> 17
                header.mpegVersion == 1 -> 32
                header.channels == 1 -> 9
                else -> 17
            }
        }

        private fun readIntBe(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }
}
