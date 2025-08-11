package space.kodio.core.io

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.transform
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat
import space.kodio.core.BitDepth
import space.kodio.core.Channels
import space.kodio.core.Encoding
import kotlin.math.floor


fun AudioFlow.convertAudio(
    targetFormat: AudioFormat
): AudioFlow {
    val sourceFormat = this.format
    if (sourceFormat == targetFormat)
        return this
    if (sourceFormat.encoding is Encoding.Unknown || targetFormat.encoding is Encoding.Unknown)
        throw IllegalArgumentException("Unknown encoding is not supported for conversion.")

    // Normalizes a sample to the range [-1.0, 1.0)
    fun getNormalizingDivisor(bitDepth: BitDepth): BigDecimal {
        return BigDecimal.fromBigInteger(BigInteger.ONE.shl(bitDepth.value - 1))
    }

    fun decode(chunk: ByteArray, format: AudioFormat): Array<BigDecimal> {
        val buffer = Buffer().apply { write(chunk) }
        val bytesPerSample = format.bitDepth.value / 8
        val numSamples = chunk.size / bytesPerSample
        val samples = Array(numSamples) { BigDecimal.ZERO }
        val pcm = format.encoding as Encoding.Pcm
        val divisor = getNormalizingDivisor(format.bitDepth)

        for (i in 0 until numSamples) {
            val rawValue: BigInteger = when (format.bitDepth) {
                is BitDepth.Eight -> BigInteger.fromByte(buffer.readByte())
                is BitDepth.Sixteen -> BigInteger.fromShort(buffer.readShort(format.endianness))
                is BitDepth.ThirtyTwo -> BigInteger.fromInt(buffer.readInt(format.endianness))
                is BitDepth.SixtyFour -> BigInteger.fromLong(buffer.readLong(format.endianness))
            }

            val sample = if (pcm.signed) {
                BigDecimal.fromBigInteger(rawValue)
            } else {
                // For unsigned, reinterpret the bits as an unsigned integer, then center it around 0.
                val correctedValue = when (format.bitDepth) {
                    is BitDepth.Eight -> BigInteger.fromInt(rawValue.byteValue().toInt() and 0xFF)
                    is BitDepth.Sixteen -> BigInteger.fromInt(rawValue.shortValue().toInt() and 0xFFFF)
                    is BitDepth.ThirtyTwo -> BigInteger.fromLong(rawValue.intValue().toLong() and 0xFFFFFFFF)
                    is BitDepth.SixtyFour -> {
                        val longVal = rawValue.longValue()
                        val bigInt = BigInteger.fromLong(longVal)
                        if (longVal < 0) bigInt + BigInteger.ONE.shl(64) else bigInt
                    }
                }
                // Subtract the offset (half the range) to map it to a signed-like range
                BigDecimal.fromBigInteger(correctedValue) - divisor
            }
            samples[i] = sample / divisor
        }
        return samples
    }

    fun resample(
        samples: Array<BigDecimal>,
        sourceRate: Int,
        targetRate: Int,
        channels: Int
    ): Array<BigDecimal> {
        if (sourceRate == targetRate || channels == 0) return samples

        // De-interleave samples into separate channels
        val deinterleaved = Array(channels) { c ->
            val channelSize = samples.size / channels
            Array(channelSize) { i -> samples[i * channels + c] }
        }

        val ratio = targetRate.toDouble() / sourceRate.toDouble()

        // Resample each channel independently
        val resampledDeinterleaved = Array(channels) { c ->
            val sourceChannel = deinterleaved[c]
            if (sourceChannel.isEmpty()) return@Array emptyArray<BigDecimal>()

            val numOutputSamples = floor(sourceChannel.size * ratio).toInt()
            val resampledChannel = Array(numOutputSamples) { BigDecimal.ZERO }
            val step = 1.0 / ratio

            for (i in 0 until numOutputSamples) {
                val sourcePos = i * step
                val index = floor(sourcePos).toInt()
                // Use a high-precision DecimalMode for interpolation
                val fraction = BigDecimal.fromDouble(sourcePos - index, DecimalMode(10, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO))

                // Get samples for interpolation, handling edge cases at the end of the chunk
                val s1 = sourceChannel.getOrNull(index) ?: sourceChannel.last()
                val s2 = sourceChannel.getOrNull(index + 1) ?: s1 // If next sample is out of bounds, use current one

                // Linear interpolation: s1 * (1 - fraction) + s2 * fraction
                resampledChannel[i] = s1.multiply(BigDecimal.ONE.subtract(fraction)).add(s2.multiply(fraction))
            }
            resampledChannel
        }

        // Re-interleave the resampled channels
        val totalOutputSamples = resampledDeinterleaved.sumOf { it.size }
        val outputSamples = Array(totalOutputSamples) { BigDecimal.ZERO }

        for (c in 0 until channels) {
            val channelData = resampledDeinterleaved[c]
            for (i in channelData.indices) {
                outputSamples[i * channels + c] = channelData[i]
            }
        }
        return outputSamples
    }

    fun convertChannels(samples: Array<BigDecimal>, from: Channels, to: Channels): Array<BigDecimal> {
        if (from == to) return samples
        return when {
            from is Channels.Mono && to is Channels.Stereo -> {
                Array(samples.size * 2) { i -> samples[i / 2] }
            }
            from is Channels.Stereo && to is Channels.Mono -> {
                Array(samples.size / 2) { i ->
                    val left = samples[i * 2]
                    val right = samples[i * 2 + 1]
                    (left + right) / BigDecimal.fromInt(2)
                }
            }
            else -> samples // Should not happen with current Channel types
        }
    }

    fun encode(samples: Array<BigDecimal>, format: AudioFormat): ByteArray {
        val buffer = Buffer()
        val pcm = format.encoding as Encoding.Pcm

        val bitDepthValue = format.bitDepth.value

        val maxValueSigned = BigInteger.ONE.shl(bitDepthValue - 1) - 1
        val minValueSigned = BigInteger.ONE.shl(bitDepthValue - 1).negate()
        val maxValueUnsigned = BigInteger.ONE.shl(bitDepthValue) - 1
        val minValueUnsigned = BigInteger.ZERO

        for (s in samples) {
            val sample = s.coerceIn(BigDecimal.ONE.negate(), BigDecimal.ONE)

            val finalBigInt: BigInteger = if (pcm.signed) {
                val divisor = BigDecimal.fromBigInteger(BigInteger.ONE.shl(bitDepthValue - 1))
                (sample * divisor)
                    .roundToDigitPositionAfterDecimalPoint(0, RoundingMode.ROUND_HALF_TO_EVEN)
                    .toBigInteger()
                    .coerceIn(minValueSigned, maxValueSigned)
            } else {
                val unsignedRange = BigDecimal.fromBigInteger(BigInteger.ONE.shl(bitDepthValue))
                val scaledSample = ((sample + BigDecimal.ONE) / BigDecimal.TWO) * unsignedRange
                scaledSample
                    .roundToDigitPositionAfterDecimalPoint(0, RoundingMode.ROUND_HALF_CEILING)
                    .toBigInteger()
                    .coerceIn(minValueUnsigned, maxValueUnsigned)
            }

            when (format.bitDepth) {
                is BitDepth.Eight -> buffer.writeByte(finalBigInt.byteValue())
                is BitDepth.Sixteen -> buffer.writeShort(format.endianness, finalBigInt.intValue().toShort())
                is BitDepth.ThirtyTwo -> buffer.writeInt(format.endianness, finalBigInt.longValue().toInt())
                is BitDepth.SixtyFour -> {
                    val twoPow64 = BigInteger.ONE.shl(64)
                    val maxLong = BigInteger.fromLong(Long.MAX_VALUE)
                    val longVal = if (finalBigInt > maxLong) {
                        (finalBigInt - twoPow64).longValue()
                    } else {
                        finalBigInt.longValue()
                    }
                    buffer.writeLong(format.endianness, longVal)
                }
            }
        }
        return buffer.readByteArray()
    }

    return AudioFlow(targetFormat, transform { chunk ->
        // 1. Decode bytes to normalized samples
        val bigDecimalSamples = decode(chunk, sourceFormat)

        // 2. Resample if necessary
        val sourceChannelCount = when (sourceFormat.channels) {
            is Channels.Mono -> 1
            is Channels.Stereo -> 2
        }
        val resampledSamples = resample(
            bigDecimalSamples,
            sourceFormat.sampleRate,
            targetFormat.sampleRate,
            sourceChannelCount
        )

        // 3. Convert channels if necessary
        val convertedChannelSamples = convertChannels(resampledSamples, sourceFormat.channels, targetFormat.channels)

        // 4. Encode normalized samples to bytes
        val outputChunk = encode(convertedChannelSamples, targetFormat)
        emit(outputChunk)
    })
}