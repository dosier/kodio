package space.kodio.core.io.files.mp3

import space.kodio.core.io.files.mp3.Mp3Constants.FRAME_HEADER_BYTES
import space.kodio.core.io.files.mp3.Mp3Constants.MPEG_VERSION_1
import space.kodio.core.io.files.mp3.Mp3Constants.VBRI_OFFSET_AFTER_HEADER
import space.kodio.core.io.files.mp3.Mp3Constants.XING_TOC_BYTES
import space.kodio.core.io.readIntBe

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
        private const val FLAG_FRAMES = 0x1
        private const val FLAG_BYTES = 0x2
        private const val FLAG_TOC = 0x4
        private const val FLAG_QUALITY = 0x8
        private const val LAME_DELAY_PAD_OFFSET = 21
        private const val VBRI_MIN_BYTES = 26

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
            val tagOffset = frameOffset + FRAME_HEADER_BYTES + sideInfo
            if (tagOffset + 8 > bytes.size) return null
            val tag = bytes.decodeToString(tagOffset, tagOffset + 4)
            if (tag != "Xing" && tag != "Info") return null

            var pos = tagOffset + 4
            if (pos + 4 > bytes.size) return null
            val flags = bytes.readIntBe(pos)
            pos += 4

            var frames: Int? = null
            var byteCount: Int? = null
            if ((flags and FLAG_FRAMES) != 0) {
                if (pos + 4 > bytes.size) return null
                frames = bytes.readIntBe(pos)
                pos += 4
            }
            if ((flags and FLAG_BYTES) != 0) {
                if (pos + 4 > bytes.size) return null
                byteCount = bytes.readIntBe(pos)
                pos += 4
            }
            if ((flags and FLAG_TOC) != 0) {
                pos += XING_TOC_BYTES
            }
            if ((flags and FLAG_QUALITY) != 0) {
                pos += 4
            }

            var delay: Int? = null
            var padding: Int? = null
            // LAME / Lavf encoder delay+padding extension (optional)
            if (pos + 20 <= bytes.size) {
                val encoder = bytes.decodeToString(pos, pos + 4)
                if (encoder.startsWith("LAME") || encoder.startsWith("Lavf") || bytes[pos].toInt() != 0) {
                    val delayPadOffset = pos + LAME_DELAY_PAD_OFFSET
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
            if (header.layer != 3) return null
            val tagOffset = frameOffset + FRAME_HEADER_BYTES + VBRI_OFFSET_AFTER_HEADER
            if (tagOffset + VBRI_MIN_BYTES > bytes.size) return null
            val tag = bytes.decodeToString(tagOffset, tagOffset + 4)
            if (tag != "VBRI") return null
            val bytesTotal = bytes.readIntBe(tagOffset + 10)
            val frames = bytes.readIntBe(tagOffset + 14)
            return Mp3XingHeader(
                frames = frames,
                bytes = bytesTotal,
                vbr = true,
            )
        }

        private fun sideInfoSize(header: Mp3FrameHeader): Int {
            return when {
                header.mpegVersion == MPEG_VERSION_1 && header.channels == 1 -> 17
                header.mpegVersion == MPEG_VERSION_1 -> 32
                header.channels == 1 -> 9
                else -> 17
            }
        }
    }
}
