import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.io.readLongLe
import kotlinx.io.readShortLe
import kotlinx.io.writeIntLe
import kotlinx.io.writeLongLe
import kotlinx.io.writeShortLe
import kotlin.math.roundToLong

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
     * This function now uses kotlinx.io.Buffer for robust, platform-agnostic byte reading.
     */
    fun decode(chunk: ByteArray, format: AudioFormat): FloatArray {
        val buffer = Buffer().apply { write(chunk) }
        val bytesPerSample = format.bitDepth.value / 8
        val numSamples = chunk.size / bytesPerSample
        val samples = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            samples[i] = when (format.bitDepth) {
                is BitDepth.Eight -> buffer.readByte() / 128.0f
                is BitDepth.Sixteen -> buffer.readShortLe() / 32768.0f
                is BitDepth.ThirtyTwo -> buffer.readIntLe() / 2147483648.0f
                is BitDepth.SixtyFour -> buffer.readLongLe() / 9223372036854775808.0f
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
     * This function now uses kotlinx.io.Buffer for robust, platform-agnostic byte writing.
     */
    fun encode(samples: FloatArray, format: AudioFormat): ByteArray {
        val buffer = Buffer()
        for (sample in samples) {
            val clampedSample = sample.coerceIn(-1.0f, 1.0f)
            when (format.bitDepth) {
                is BitDepth.Eight -> {
                    val value = (clampedSample * 128.0f).toInt()
                    buffer.writeByte(value.coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte())
                }
                is BitDepth.Sixteen -> {
                    val value = (clampedSample * 32768.0f).toInt()
                    buffer.writeShortLe(value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                }
                is BitDepth.ThirtyTwo -> {
                    val value = (clampedSample * 2147483648.0).toLong()
                    buffer.writeIntLe(value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
                }
                is BitDepth.SixtyFour -> {
                    val value = (clampedSample * 9223372036854775808.0).roundToLong()
                    buffer.writeLongLe(value.coerceIn(Long.MIN_VALUE, Long.MAX_VALUE))
                }
            }
        }
        return buffer.readByteArray()
    }

    return AudioFlow(targetFormat, transform { chunk ->
        val floatSamples = decode(chunk, sourceFormat)
        val convertedChannelSamples = convertChannels(floatSamples, sourceFormat.channels, targetFormat.channels)
        val outputChunk = encode(convertedChannelSamples, targetFormat)
        emit(outputChunk)
    })
}