package space.kodio.core.io

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.io.Buffer
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt

/**
 * Decodes raw PCM bytes to normalized samples in [-1.0, 1.0].
 *
 * For interleaved multi-channel input, this returns a flat stream
 * (L R L R ...). If you need per-channel arrays, split by channels.count.
 *
 * For planar (non-interleaved), call this per plane.
 */
fun decode(chunk: ByteArray, format: AudioFormat): Array<BigDecimal> {
    val buffer = Buffer().apply { write(chunk) }
    val bytesPerSample = format.bytesPerSample
    val numSamples = if (bytesPerSample == 0) 0 else chunk.size / bytesPerSample
    val out = Array(numSamples) { BigDecimal.ZERO }

    when (val enc = format.encoding) {
        is PcmInt -> decodePcmInt(buffer, out, enc)
        is PcmFloat -> decodePcmFloat(buffer, out, enc)
    }
    return out
}

/* -------------------- PCM-INT -------------------- */

private fun decodePcmInt(
    buffer: Buffer,
    out: Array<BigDecimal>,
    enc: PcmInt
) {
    val bits = enc.bitDepth.bits
    val divisor = normalizingDivisor(bits).let(BigDecimal::fromBigInteger)

    for (i in out.indices) {
        val raw: Long = when (bits) {
            8  -> buffer.readU8AsLong(enc.signed)
            16 -> buffer.readSOrU16(enc.endianness, enc.signed).toLong()
            24 -> buffer.readSOrU24(enc.endianness, enc.signed)
            32 -> buffer.readSOrU32(enc.endianness, enc.signed)
            else -> error("Unsupported PCM-Int bit depth: $bits")
        }

        val centered = if (enc.signed) {
            raw.toBigInteger()
        } else {
            // Unsigned → shift to signed-like domain by subtracting half-range
            val halfRange = (1L shl (bits - 1)).toBigInteger()
            raw.toBigInteger() - halfRange
        }

        out[i] = centered.let(BigDecimal::fromBigInteger) / divisor
    }
}

/* -------------------- PCM-FLOAT -------------------- */

private fun decodePcmFloat(
    buffer: Buffer,
    out: Array<BigDecimal>,
    enc: PcmFloat
) {
    for (i in out.indices) {
        val v: Double = when (enc.precision) {
            FloatPrecision.F32 -> buffer.readF32(enc.layoutEndianness()).toDouble()
            FloatPrecision.F64 -> buffer.readF64(enc.layoutEndianness())
        }
        // Most CoreAudio float streams are already in [-1, 1], but clamp just in case.
        val clamped = v.coerceIn(-1.0, 1.0)
        out[i] = clamped.toBigDecimal()
    }
}

/* -------------------- Helpers -------------------- */

/**
 * For floats we store endianness on the format via PcmFloat + ASBD flags.
 * If you also track endianness separately, adapt this. By default CoreAudio uses native endian.
 * Here we assume little-endian unless you’ve built big-endian buffers deliberately.
 */
private fun PcmFloat.layoutEndianness(): Endianness = Endianness.Little

private fun normalizingDivisor(bits: Int): BigInteger =
    BigInteger.ONE shl (bits - 1)

/* ---- Buffer integer readers ---- */

private fun Buffer.readU8AsLong(signed: Boolean): Long {
    val b = readByte()
    return if (signed) b.toLong() else (b.toInt() and 0xFF).toLong()
}

private fun Buffer.readSOrU16(endian: Endianness, signed: Boolean): Int {
    val v = when (endian) {
        Endianness.Little -> readInt16LE()
        Endianness.Big    -> readInt16BE()
    }
    return if (signed) v.toInt() else v.toInt() and 0xFFFF
}

/** Read 24-bit sample; sign-extend if needed. */
private fun Buffer.readSOrU24(endian: Endianness, signed: Boolean): Long {
    val b0: Int; val b1: Int; val b2: Int
    when (endian) {
        Endianness.Little -> {
            b0 = readByte().toInt() and 0xFF
            b1 = readByte().toInt() and 0xFF
            b2 = readByte().toInt() and 0xFF
        }
        Endianness.Big -> {
            b2 = readByte().toInt() and 0xFF
            b1 = readByte().toInt() and 0xFF
            b0 = readByte().toInt() and 0xFF
        }
    }
    var u = (b2 shl 16) or (b1 shl 8) or b0
    if (signed && (u and 0x800000) != 0) {
        u = u or -0x1000000 // sign-extend to 32 bits
    }
    return if (signed) u.toLong() else (u and 0xFFFFFF).toLong()
}

private fun Buffer.readSOrU32(endian: Endianness, signed: Boolean): Long {
    val v = when (endian) {
        Endianness.Little -> readInt32LE()
        Endianness.Big    -> readInt32BE()
    }
    return if (signed) v.toLong() else v.toLong() and 0xFFFF_FFFFL
}

/* ---- Buffer float readers (IEEE-754) ---- */

private fun Buffer.readF32(endian: Endianness): Float {
    val bits = when (endian) {
        Endianness.Little -> readInt32LE()
        Endianness.Big    -> readInt32BE()
    }
    return Float.fromBits(bits)
}

private fun Buffer.readF64(endian: Endianness): Double {
    val bits = when (endian) {
        Endianness.Little -> readInt64LE()
        Endianness.Big    -> readInt64BE()
    }
    return Double.fromBits(bits)
}

/* ---- Minimal endian helpers for Buffer ---- */
/* If your kotlinx-io already has endian reads, you can delete these and use the built-ins. */

private fun Buffer.readInt16LE(): Short {
    val b0 = readByte().toInt() and 0xFF
    val b1 = readByte().toInt()
    return ((b1 shl 8) or b0).toShort()
}

private fun Buffer.readInt16BE(): Short {
    val b0 = readByte().toInt()
    val b1 = readByte().toInt() and 0xFF
    return ((b0 shl 8) or b1).toShort()
}

private fun Buffer.readInt32LE(): Int {
    val b0 = readByte().toInt() and 0xFF
    val b1 = readByte().toInt() and 0xFF
    val b2 = readByte().toInt() and 0xFF
    val b3 = readByte().toInt()
    return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
}

private fun Buffer.readInt32BE(): Int {
    val b0 = readByte().toInt()
    val b1 = readByte().toInt() and 0xFF
    val b2 = readByte().toInt() and 0xFF
    val b3 = readByte().toInt() and 0xFF
    return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
}

private fun Buffer.readInt64LE(): Long {
    var res = 0L
    repeat(8) { i ->
        res = res or ((readByte().toLong() and 0xFFL) shl (8 * i))
    }
    return res
}

private fun Buffer.readInt64BE(): Long {
    var res = 0L
    repeat(8) {
        res = (res shl 8) or (readByte().toLong() and 0xFFL)
    }
    return res
}