import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

class AudioFlow(
    val format: AudioFormat,
    data: Flow<ByteArray>
) : Flow<ByteArray> by data

fun AudioFlow.convertAudio(
    targetFormat: AudioFormat
): AudioFlow {
    val sourceFormat = this.format
    if (sourceFormat == targetFormat)
        return this
    if (sourceFormat.sampleRate != targetFormat.sampleRate)
        throw UnsupportedOperationException("Sample rate conversion is not supported.")
    /**
     * Decodes a chunk of bytes into normalized Float samples ranging from -1.0 to 1.0.
     * This platform-agnostic function reads bytes and converts them to integers for each sample.
     */
    fun decode(chunk: ByteArray, format: AudioFormat): FloatArray {
        val bytesPerSample = format.bitDepth.value / 8
        val numSamples = chunk.size / bytesPerSample
        val samples = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            val byteIndex = i * bytesPerSample
            samples[i] = when (format.bitDepth) {
                is BitDepth.Eight -> {
                    // Signed 8-bit, from -128 to 127
                    chunk[byteIndex] / 128.0f
                }
                is BitDepth.Sixteen -> {
                    // Little-endian 16-bit signed integer
                    val value = ((chunk[byteIndex + 1].toInt() shl 8) or (chunk[byteIndex].toInt() and 0xFF)).toShort()
                    value / 32768.0f
                }
                is BitDepth.ThirtyTwo -> {
                    // Little-endian 32-bit signed integer
                    val value = (chunk[byteIndex + 3].toInt() shl 24) or
                            ((chunk[byteIndex + 2].toInt() and 0xFF) shl 16) or
                            ((chunk[byteIndex + 1].toInt() and 0xFF) shl 8) or
                            (chunk[byteIndex].toInt() and 0xFF)
                    value / 2147483648.0f
                }
                is BitDepth.SixtyFour -> {
                    // Little-endian 64-bit signed integer
                    val value = (chunk[byteIndex + 7].toLong() shl 56) or
                            ((chunk[byteIndex + 6].toLong() and 0xFF) shl 48) or
                            ((chunk[byteIndex + 5].toLong() and 0xFF) shl 40) or
                            ((chunk[byteIndex + 4].toLong() and 0xFF) shl 32) or
                            ((chunk[byteIndex + 3].toLong() and 0xFF) shl 24) or
                            ((chunk[byteIndex + 2].toLong() and 0xFF) shl 16) or
                            ((chunk[byteIndex + 1].toLong() and 0xFF) shl 8) or
                            (chunk[byteIndex].toLong() and 0xFF)
                    (value / 9223372036854775808.0).toFloat()
                }
            }
        }
        return samples
    }

    /**
     * Converts the channel configuration of the audio samples.
     * It can convert from mono to stereo by duplicating samples,
     * and from stereo to mono by averaging the left and right channels.
     */
    fun convertChannels(samples: FloatArray, from: Channels, to: Channels): FloatArray {
        if (from == to) return samples
        return when {
            from is Channels.Mono && to is Channels.Stereo -> {
                val output = FloatArray(samples.size * 2)
                for (i in samples.indices) {
                    output[i * 2] = samples[i]      // Left channel
                    output[i * 2 + 1] = samples[i]  // Right channel
                }
                output
            }
            from is Channels.Stereo && to is Channels.Mono -> {
                val output = FloatArray(samples.size / 2)
                for (i in output.indices) {
                    val left = samples[i * 2]
                    val right = samples[i * 2 + 1]
                    output[i] = (left + right) / 2.0f
                }
                output
            }
            else -> samples
        }
    }

    /**
     * Encodes normalized Float samples back into a byte array according to the target format.
     * This platform-agnostic function manually writes bytes for each sample value.
     */
    fun encode(samples: FloatArray, format: AudioFormat): ByteArray {
        val bytesPerSample = format.bitDepth.value / 8
        val output = ByteArray(samples.size * bytesPerSample)
        for (i in samples.indices) {
            val sample = samples[i].coerceIn(-1.0f, 1.0f)
            val byteIndex = i * bytesPerSample
            when (format.bitDepth) {
                is BitDepth.Eight -> {
                    output[byteIndex] = (sample * 127.0f).toInt().toByte()
                }
                is BitDepth.Sixteen -> {
                    val value = (sample * 32767.0f).toInt()
                    output[byteIndex] = (value and 0xFF).toByte()
                    output[byteIndex + 1] = (value shr 8 and 0xFF).toByte()
                }
                is BitDepth.ThirtyTwo -> {
                    val value = (sample * 2147483647.0f).toInt()
                    output[byteIndex] = (value and 0xFF).toByte()
                    output[byteIndex + 1] = (value shr 8 and 0xFF).toByte()
                    output[byteIndex + 2] = (value shr 16 and 0xFF).toByte()
                    output[byteIndex + 3] = (value shr 24 and 0xFF).toByte()
                }
                is BitDepth.SixtyFour -> {
                    val value = (sample.toDouble() * 9223372036854775807.0).toLong()
                    output[byteIndex] = (value and 0xFF).toByte()
                    output[byteIndex + 1] = (value shr 8 and 0xFF).toByte()
                    output[byteIndex + 2] = (value shr 16 and 0xFF).toByte()
                    output[byteIndex + 3] = (value shr 24 and 0xFF).toByte()
                    output[byteIndex + 4] = (value shr 32 and 0xFF).toByte()
                    output[byteIndex + 5] = (value shr 40 and 0xFF).toByte()
                    output[byteIndex + 6] = (value shr 48 and 0xFF).toByte()
                    output[byteIndex + 7] = (value shr 56 and 0xFF).toByte()
                }
            }
        }
        return output
    }

    return AudioFlow(targetFormat, transform { chunk ->
        val floatSamples = decode(chunk, sourceFormat)
        val convertedChannelSamples = convertChannels(floatSamples, sourceFormat.channels, targetFormat.channels)
        val outputChunk = encode(convertedChannelSamples, targetFormat)
        emit(outputChunk)
    })
}