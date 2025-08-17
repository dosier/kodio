package space.kodio.core.io

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.io.Buffer
import space.kodio.core.AudioFormat
import space.kodio.core.BitDepth
import space.kodio.core.Encoding

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

// Normalizes a sample to the range [-1.0, 1.0)
fun getNormalizingDivisor(bitDepth: BitDepth): BigDecimal {
    return BigDecimal.fromBigInteger(BigInteger.ONE.shl(bitDepth.value - 1))
}
