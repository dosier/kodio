package space.kodio.core.io.files.mp3

/**
 * Parsed MPEG audio frame header (MPEG-1/2/2.5 Layer I/II/III).
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

        /**
         * Tries to parse a frame header starting at [offset] (must point at 0xFF sync byte).
         * Returns null if the header is not a valid MPEG audio frame.
         */
        fun tryParse(bytes: ByteArray, offset: Int = 0): Mp3FrameHeader? {
            if (offset + 4 > bytes.size) return null
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            val b2 = bytes[offset + 2].toInt() and 0xFF
            val b3 = bytes[offset + 3].toInt() and 0xFF
            if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

            val versionBits = (b1 ushr 3) and 0x3
            val layerBits = (b1 ushr 1) and 0x3
            val protection = (b1 and 0x1) == 0
            val bitrateIndex = (b2 ushr 4) and 0xF
            val sampleRateIndex = (b2 ushr 2) and 0x3
            val padding = ((b2 ushr 1) and 0x1) == 1
            val mode = (b3 ushr 6) and 0x3

            if (versionBits == 0b01) return null // reserved
            if (layerBits == 0b00) return null // reserved
            if (bitrateIndex == 0x0 || bitrateIndex == 0xF) return null // free / bad
            if (sampleRateIndex == 0b11) return null // reserved

            val mpegVersion = when (versionBits) {
                0b11 -> 1
                0b10 -> 2
                0b00 -> 25
                else -> return null
            }
            val layer = when (layerBits) {
                0b01 -> 3
                0b10 -> 2
                0b11 -> 1
                else -> return null
            }

            val sampleRate = when (mpegVersion) {
                1 -> SAMPLE_RATE_V1[sampleRateIndex]
                2 -> SAMPLE_RATE_V2[sampleRateIndex]
                else -> SAMPLE_RATE_V25[sampleRateIndex]
            }

            val bitrateKbps = when {
                mpegVersion == 1 && layer == 1 -> BITRATE_V1_L1[bitrateIndex]
                mpegVersion == 1 && layer == 2 -> BITRATE_V1_L2[bitrateIndex]
                mpegVersion == 1 && layer == 3 -> BITRATE_V1_L3[bitrateIndex]
                layer == 1 -> BITRATE_V2_L1[bitrateIndex]
                else -> BITRATE_V2_L3[bitrateIndex] // Layer II/III MPEG-2/2.5
            }
            if (bitrateKbps == 0) return null
            val bitrate = bitrateKbps * 1000

            val samplesPerFrame = when {
                layer == 1 -> 384
                layer == 2 -> 1152
                mpegVersion == 1 -> 1152 // Layer III MPEG-1
                else -> 576 // Layer III MPEG-2/2.5
            }

            val paddingBytes = if (padding) {
                if (layer == 1) 4 else 1
            } else {
                0
            }
            val frameLength = if (layer == 1) {
                (12 * bitrate / sampleRate + paddingBytes) * 4
            } else {
                val coef = if (mpegVersion == 1) 144 else 72
                coef * bitrate / sampleRate + paddingBytes
            }
            if (frameLength < 24) return null

            val channels = if (mode == 3) 1 else 2
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
            var offset = skipId3v2(bytes)
            val limit = minOf(bytes.size - 4, offset + 64 * 1024)
            while (offset <= limit) {
                if ((bytes[offset].toInt() and 0xFF) == 0xFF) {
                    val header = tryParse(bytes, offset)
                    if (header != null) {
                        // Validate by checking the next frame sync when possible
                        val next = offset + header.frameLength
                        if (next + 4 <= bytes.size) {
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

        fun skipId3v2(bytes: ByteArray): Int {
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
    }
}
