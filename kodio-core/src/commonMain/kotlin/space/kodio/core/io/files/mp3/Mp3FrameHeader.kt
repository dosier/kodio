package space.kodio.core.io.files.mp3

import space.kodio.core.io.files.mp3.Mp3Constants.CHANNEL_MODE_MONO
import space.kodio.core.io.files.mp3.Mp3Constants.FIRST_FRAME_SCAN_LIMIT
import space.kodio.core.io.files.mp3.Mp3Constants.FRAME_HEADER_BYTES
import space.kodio.core.io.files.mp3.Mp3Constants.MPEG_VERSION_1
import space.kodio.core.io.files.mp3.Mp3Constants.MPEG_VERSION_2
import space.kodio.core.io.files.mp3.Mp3Constants.MPEG_VERSION_25
import space.kodio.core.io.files.mp3.Mp3Constants.SAMPLES_LAYER1
import space.kodio.core.io.files.mp3.Mp3Constants.SAMPLES_LAYER2
import space.kodio.core.io.files.mp3.Mp3Constants.SAMPLES_LAYER3_MPEG1
import space.kodio.core.io.files.mp3.Mp3Constants.SAMPLES_LAYER3_MPEG2
import space.kodio.core.io.files.mp3.Mp3Constants.SYNC_BYTE
import space.kodio.core.io.files.mp3.Mp3Constants.SYNC_MASK
import space.kodio.core.io.files.mp3.id3.skipId3v2Tag

/**
 * Parsed MPEG audio frame header (MPEG-1/2/2.5 Layer I/II/III).
 * Used for metadata / Xing / magic probe — not the minimp3 hot path.
 */
internal data class Mp3FrameHeader(
    val mpegVersion: Int, // 1, 2, or 25 (2.5)
    val layer: Int, // 1, 2, or 3
    val bitrate: Int, // bits per second
    val sampleRate: Int,
    val channels: Int, // 1 or 2
    val mode: Int, // 0 stereo, 1 joint, 2 dual, 3 mono
    val padding: Boolean,
    val protection: Boolean,
    val frameLength: Int,
    val samplesPerFrame: Int,
) {
    companion object {
        private val BITRATE_V1_L3 = intArrayOf(
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0,
        )
        private val BITRATE_V1_L2 = intArrayOf(
            0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0,
        )
        private val BITRATE_V1_L1 = intArrayOf(
            0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0,
        )
        private val BITRATE_V2_L3 = intArrayOf(
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0,
        )
        private val BITRATE_V2_L1 = intArrayOf(
            0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0,
        )
        private val SAMPLE_RATE_V1 = intArrayOf(44100, 48000, 32000)
        private val SAMPLE_RATE_V2 = intArrayOf(22050, 24000, 16000)
        private val SAMPLE_RATE_V25 = intArrayOf(11025, 12000, 8000)

        private const val MIN_FRAME_LENGTH = 24
        private const val VERSION_BITS_RESERVED = 0b01
        private const val LAYER_BITS_RESERVED = 0b00
        private const val BITRATE_FREE = 0x0
        private const val BITRATE_BAD = 0xF
        private const val SAMPLE_RATE_RESERVED = 0b11

        /**
         * Tries to parse a frame header starting at [offset] (must point at sync byte).
         * Returns null if the header is not a valid MPEG audio frame.
         */
        fun tryParse(bytes: ByteArray, offset: Int = 0): Mp3FrameHeader? {
            if (offset + FRAME_HEADER_BYTES > bytes.size) return null
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            val b2 = bytes[offset + 2].toInt() and 0xFF
            val b3 = bytes[offset + 3].toInt() and 0xFF
            if (b0 != SYNC_BYTE || (b1 and SYNC_MASK) != SYNC_MASK) return null

            val versionBits = (b1 ushr 3) and 0x3
            val layerBits = (b1 ushr 1) and 0x3
            val protection = (b1 and 0x1) == 0
            val bitrateIndex = (b2 ushr 4) and 0xF
            val sampleRateIndex = (b2 ushr 2) and 0x3
            val padding = ((b2 ushr 1) and 0x1) == 1
            val mode = (b3 ushr 6) and 0x3

            if (versionBits == VERSION_BITS_RESERVED) return null
            if (layerBits == LAYER_BITS_RESERVED) return null
            if (bitrateIndex == BITRATE_FREE || bitrateIndex == BITRATE_BAD) return null
            if (sampleRateIndex == SAMPLE_RATE_RESERVED) return null

            val mpegVersion = when (versionBits) {
                0b11 -> MPEG_VERSION_1
                0b10 -> MPEG_VERSION_2
                0b00 -> MPEG_VERSION_25
                else -> return null
            }
            val layer = when (layerBits) {
                0b01 -> 3
                0b10 -> 2
                0b11 -> 1
                else -> return null
            }

            val sampleRate = when (mpegVersion) {
                MPEG_VERSION_1 -> SAMPLE_RATE_V1[sampleRateIndex]
                MPEG_VERSION_2 -> SAMPLE_RATE_V2[sampleRateIndex]
                else -> SAMPLE_RATE_V25[sampleRateIndex]
            }

            val bitrateKbps = when {
                mpegVersion == MPEG_VERSION_1 && layer == 1 -> BITRATE_V1_L1[bitrateIndex]
                mpegVersion == MPEG_VERSION_1 && layer == 2 -> BITRATE_V1_L2[bitrateIndex]
                mpegVersion == MPEG_VERSION_1 && layer == 3 -> BITRATE_V1_L3[bitrateIndex]
                layer == 1 -> BITRATE_V2_L1[bitrateIndex]
                else -> BITRATE_V2_L3[bitrateIndex] // Layer II/III MPEG-2/2.5
            }
            if (bitrateKbps == 0) return null
            val bitrate = bitrateKbps * 1000

            val samplesPerFrame = when {
                layer == 1 -> SAMPLES_LAYER1
                layer == 2 -> SAMPLES_LAYER2
                mpegVersion == MPEG_VERSION_1 -> SAMPLES_LAYER3_MPEG1
                else -> SAMPLES_LAYER3_MPEG2
            }

            val paddingBytes = if (padding) {
                if (layer == 1) 4 else 1
            } else {
                0
            }
            val frameLength = if (layer == 1) {
                (12 * bitrate / sampleRate + paddingBytes) * 4
            } else {
                val coef = if (mpegVersion == MPEG_VERSION_1) 144 else 72
                coef * bitrate / sampleRate + paddingBytes
            }
            if (frameLength < MIN_FRAME_LENGTH) return null

            val channels = if (mode == CHANNEL_MODE_MONO) 1 else 2
            return Mp3FrameHeader(
                mpegVersion = mpegVersion,
                layer = layer,
                bitrate = bitrate,
                sampleRate = sampleRate,
                channels = channels,
                mode = mode,
                padding = padding,
                protection = protection,
                frameLength = frameLength,
                samplesPerFrame = samplesPerFrame,
            )
        }

        /** Finds the first parseable MPEG frame in [bytes], skipping ID3v2 if present. */
        fun findFirst(bytes: ByteArray): Pair<Int, Mp3FrameHeader>? {
            var offset = skipId3v2Tag(bytes)
            val limit = minOf(bytes.size - FRAME_HEADER_BYTES, offset + FIRST_FRAME_SCAN_LIMIT)
            while (offset <= limit) {
                if ((bytes[offset].toInt() and 0xFF) == SYNC_BYTE) {
                    val header = tryParse(bytes, offset)
                    if (header != null) {
                        val next = offset + header.frameLength
                        if (next + FRAME_HEADER_BYTES <= bytes.size) {
                            val nextHeader = tryParse(bytes, next)
                            if (nextHeader != null) {
                                return offset to header
                            }
                        } else {
                            return offset to header
                        }
                    }
                }
                offset++
            }
            return null
        }
    }
}
