package space.kodio.core.io

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.transform
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt
import space.kodio.core.util.namedLogger
import kotlin.math.floor
import kotlin.math.min

private val logger = namedLogger("AudioConversion")

/**
 * Convert an AudioFlow to a different AudioFormat.
 * Pipeline: bytes -> normalized samples -> resample -> channel convert -> bytes
 */
fun AudioFlow.convertAudio(
    targetFormat: AudioFormat
): AudioFlow {
    val sourceFormat = this.format
    if (sourceFormat == targetFormat) return this

    fun resample(
        samples: Array<BigDecimal>,
        sourceRate: Int,
        targetRate: Int,
        channels: Int
    ): Array<BigDecimal> {
        if (channels <= 0 || sourceRate == targetRate) return samples

        // De-interleave: [L R L R ...] -> [L...], [R...]
        val frames = samples.size / channels
        val deinterleaved = Array(channels) { c ->
            Array(frames) { i -> samples[i * channels + c] }
        }

        val ratio = targetRate.toDouble() / sourceRate.toDouble()
        val mode = DecimalMode(10, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)

        val resampledDeinterleaved = Array(channels) { c ->
            val src = deinterleaved[c]
            if (src.isEmpty()) return@Array emptyArray<BigDecimal>()

            // output frames by scale
            val outFrames = maxOf(1, floor(src.size * ratio).toInt())
            val out = Array(outFrames) { BigDecimal.ZERO }
            val step = 1.0 / ratio

            for (i in 0 until outFrames) {
                val srcPos = i * step
                val idx = floor(srcPos).toInt()
                val frac = BigDecimal.fromDouble(srcPos - idx, mode)

                val s1 = src[min(idx, src.lastIndex)]
                val s2 = src[min(idx + 1, src.lastIndex)]

                // lerp: s1*(1-frac) + s2*frac
                out[i] = s1 * (BigDecimal.ONE - frac) + s2 * frac
            }
            out
        }

        // Re-interleave
        val outFrames = resampledDeinterleaved.firstOrNull()?.size ?: 0
        val out = Array(outFrames * channels) { BigDecimal.ZERO }
        for (c in 0 until channels) {
            val ch = resampledDeinterleaved[c]
            for (i in ch.indices) {
                out[i * channels + c] = ch[i]
            }
        }
        return out
    }

    fun convertChannels(samples: Array<BigDecimal>, from: Channels, to: Channels): Array<BigDecimal> {
        if (from == to) return samples
        val fromN = from.count
        val toN = to.count

        return when {
            fromN == 1 && toN == 2 -> { // mono -> stereo (duplicate)
                val out = Array(samples.size * 2) { BigDecimal.ZERO }
                for (i in samples.indices) {
                    val s = samples[i]
                    out[2 * i] = s
                    out[2 * i + 1] = s
                }
                out
            }
            fromN == 2 && toN == 1 -> { // stereo -> mono (average)
                val frames = samples.size / 2
                Array(frames) { i ->
                    val l = samples[2 * i]
                    val r = samples[2 * i + 1]
                    (l + r) / BigDecimal.fromInt(2)
                }
            }
            else -> {
                // Generic N->M: simple downmix/upmix via average/duplication
                val frames = samples.size / fromN
                val out = Array(frames * toN) { BigDecimal.ZERO }
                for (i in 0 until frames) {
                    // average all input channels for base
                    var sum = BigDecimal.ZERO
                    for (c in 0 until fromN) sum += samples[i * fromN + c]
                    val avg = sum / BigDecimal.fromInt(fromN)
                    for (c in 0 until toN) out[i * toN + c] = avg
                }
                out
            }
        }
    }

    fun encode(samples: Array<BigDecimal>, fmt: AudioFormat): ByteArray {
        val buffer = Buffer()
        when (val enc = fmt.encoding) {
            is PcmInt -> encodePcmInt(samples, fmt.channels.count, enc, buffer)
            is PcmFloat -> encodePcmFloat(samples, fmt.channels.count, enc, buffer)
        }
        return buffer.readByteArray()
    }

    return AudioFlow(targetFormat, transform { chunk ->
        logger.trace { "Processing chunk of ${chunk.size} bytes" }
        
        // 1) Bytes -> normalized samples [-1, 1]
        val normalized = decode(chunk, sourceFormat)
        logger.trace { "Decoded to ${normalized.size} samples" }

        // 2) Resample if needed (per-channel, linear)
        val resampled = resample(
            samples = normalized,
            sourceRate = sourceFormat.sampleRate,
            targetRate = targetFormat.sampleRate,
            channels = sourceFormat.channels.count
        )
        logger.trace { "Resampled to ${resampled.size} samples" }

        // 3) Channel convert if needed
        val channelConverted = convertChannels(resampled, sourceFormat.channels, targetFormat.channels)
        logger.trace { "Channel converted to ${channelConverted.size} samples" }

        // 4) Encode into target bytes
        val encoded = encode(channelConverted, targetFormat)
        logger.trace { "Encoded to ${encoded.size} bytes" }
        emit(encoded)
    })
}

/* ===================== ENCODERS ===================== */

private fun encodePcmInt(
    samples: Array<BigDecimal>,
    channels: Int,
    enc: PcmInt,
    out: Buffer
) {
    val bits = enc.bitDepth.bits
    val div = BigInteger.ONE shl (bits - 1) // for signed scaling

    // bounds
    val maxSigned = (BigInteger.ONE shl (bits - 1)) - BigInteger.ONE
    val minSigned = (BigInteger.ONE shl (bits - 1)).negate()
    val maxUnsigned = (BigInteger.ONE shl bits) - BigInteger.ONE
    val minUnsigned = BigInteger.ZERO

    for (s in samples) {
        val clamped = s.coerceIn(BigDecimal.ONE.negate(), BigDecimal.ONE)

        val asBigInt: BigInteger = if (enc.signed) {
            // scale into [-2^(bits-1), 2^(bits-1)-1]
            (clamped * BigDecimal.fromBigInteger(div))
                .roundToDigitPositionAfterDecimalPoint(0, RoundingMode.ROUND_HALF_TO_EVEN)
                .toBigInteger()
                .coerceIn(minSigned, maxSigned)
        } else {
            // map [-1,1] -> [0, 2^bits)
            val full = BigDecimal.fromBigInteger(BigInteger.ONE shl bits)
            (((clamped + BigDecimal.ONE) / BigDecimal.TWO) * full)
                .roundToDigitPositionAfterDecimalPoint(0, RoundingMode.ROUND_HALF_CEILING)
                .toBigInteger()
                .coerceIn(minUnsigned, maxUnsigned)
        }

        when (bits) {
            8  -> out.writeByte(asBigInt.byteValue())
            16 -> writeInt16(out, asBigInt.intValue().toShort(), enc.endianness)
            24 -> writeInt24(out, asBigInt.longValue(), enc.endianness) // if you support 24-bit in your model
            32 -> writeInt32(out, asBigInt.intValue(), enc.endianness)
            64 -> writeInt64(out, normalizeU64ToS64(asBigInt), enc.endianness)
            else -> error("Unsupported PCM-Int bit depth: $bits")
        }
    }
}

private fun encodePcmFloat(
    samples: Array<BigDecimal>,
    channels: Int,
    enc: PcmFloat,
    out: Buffer
) {
    for (s in samples) {
        // Safely convert BigDecimal to float/double, handling edge cases
        val clamped = s.coerceIn(BigDecimal.ONE.negate(), BigDecimal.ONE)
        val v: Float = try {
            clamped.floatValue()
        } catch (e: ArithmeticException) {
            // Fallback: convert through string to handle precision issues
            clamped.toStringExpanded().toFloatOrNull() ?: 0f
        }
        when (enc.precision) {
            FloatPrecision.F32 -> writeF32(out, v, Endianness.Little)
            FloatPrecision.F64 -> writeF64(out, v.toDouble(), Endianness.Little)
        }
    }
}

/* ===================== BUFFER WRITE HELPERS ===================== */
/* Use these if your Buffer doesn't already have endian-aware writers. */

private fun writeInt16(buf: Buffer, v: Short, e: Endianness) {
    when (e) {
        Endianness.Little -> {
            buf.writeByte((v.toInt() and 0xFF).toByte())
            buf.writeByte(((v.toInt() ushr 8) and 0xFF).toByte())
        }
        Endianness.Big -> {
            buf.writeByte(((v.toInt() ushr 8) and 0xFF).toByte())
            buf.writeByte((v.toInt() and 0xFF).toByte())
        }
    }
}

private fun writeInt24(buf: Buffer, v: Long, e: Endianness) {
    val u = (v and 0xFFFFFF).toInt()
    when (e) {
        Endianness.Little -> {
            buf.writeByte((u and 0xFF).toByte())
            buf.writeByte(((u ushr 8) and 0xFF).toByte())
            buf.writeByte(((u ushr 16) and 0xFF).toByte())
        }
        Endianness.Big -> {
            buf.writeByte(((u ushr 16) and 0xFF).toByte())
            buf.writeByte(((u ushr 8) and 0xFF).toByte())
            buf.writeByte((u and 0xFF).toByte())
        }
    }
}

private fun writeInt32(buf: Buffer, v: Int, e: Endianness) {
    when (e) {
        Endianness.Little -> {
            buf.writeByte((v and 0xFF).toByte())
            buf.writeByte(((v ushr 8) and 0xFF).toByte())
            buf.writeByte(((v ushr 16) and 0xFF).toByte())
            buf.writeByte(((v ushr 24) and 0xFF).toByte())
        }
        Endianness.Big -> {
            buf.writeByte(((v ushr 24) and 0xFF).toByte())
            buf.writeByte(((v ushr 16) and 0xFF).toByte())
            buf.writeByte(((v ushr 8) and 0xFF).toByte())
            buf.writeByte((v and 0xFF).toByte())
        }
    }
}

private fun writeInt64(buf: Buffer, v: Long, e: Endianness) {
    when (e) {
        Endianness.Little -> {
            var x = v
            repeat(8) {
                buf.writeByte((x and 0xFF).toInt().toByte())
                x = x ushr 8
            }
        }
        Endianness.Big -> {
            var x = v
            val bytes = ByteArray(8)
            for (i in 7 downTo 0) {
                bytes[i] = (x and 0xFF).toInt().toByte()
                x = x ushr 8
            }
            buf.write(bytes)
        }
    }
}

private fun writeF32(buf: Buffer, f: Float, e: Endianness) {
    writeInt32(buf, f.toRawBits(), e)
}

private fun writeF64(buf: Buffer, d: Double, e: Endianness) {
    writeInt64(buf, d.toRawBits(), e)
}

/** Map BigInteger in [0, 2^64-1] to signed Long for 64-bit PCM path. */
private fun normalizeU64ToS64(v: BigInteger): Long {
    val two64 = BigInteger.ONE shl 64
    val maxLong = BigInteger.fromLong(Long.MAX_VALUE)
    // If value > Long.MAX_VALUE, wrap into signed range
    val signed = if (v > maxLong) v - two64 else v
    return signed.longValue()
}