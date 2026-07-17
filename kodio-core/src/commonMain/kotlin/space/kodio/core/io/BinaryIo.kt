package space.kodio.core.io

import kotlinx.io.Source
import kotlinx.io.Sink
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readTo
import kotlin.math.pow
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileWriteError

internal fun Source.readIntBe(): Int {
    val b0 = readByte().toInt() and 0xFF
    val b1 = readByte().toInt() and 0xFF
    val b2 = readByte().toInt() and 0xFF
    val b3 = readByte().toInt() and 0xFF
    return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
}

internal fun Sink.writeIntBe(value: Int) {
    writeByte(((value ushr 24) and 0xFF).toByte())
    writeByte(((value ushr 16) and 0xFF).toByte())
    writeByte(((value ushr 8) and 0xFF).toByte())
    writeByte((value and 0xFF).toByte())
}

internal fun Source.readUInt16Be(): Int {
    val h = readByte().toInt() and 0xFF
    val l = readByte().toInt() and 0xFF
    return (h shl 8) or l
}

internal fun Sink.writeUInt16Be(value: Int) {
    writeByte(((value ushr 8) and 0xFF).toByte())
    writeByte((value and 0xFF).toByte())
}

internal fun Source.skipChunkPadded(dataSize: Long) {
    skip(dataSize)
    if (dataSize and 1L == 1L) skip(1)
}

internal fun Source.readExactly(byteCount: Int): ByteArray {
    val buf = Buffer()
    readTo(buf, byteCount.toLong())
    return buf.readByteArray()
}

internal fun ByteArray.swapEndianness(bytesPerSample: Int): ByteArray {
    if (bytesPerSample <= 1) return this
    val result = copyOf()
    var i = 0
    while (i + bytesPerSample <= result.size) {
        for (j in 0 until bytesPerSample / 2) {
            val tmp = result[i + j]
            result[i + j] = result[i + bytesPerSample - 1 - j]
            result[i + bytesPerSample - 1 - j] = tmp
        }
        i += bytesPerSample
    }
    return result
}

private fun uint64BitsToDouble(raw: Long): Double {
    val high = (raw ushr 32).toUInt().toDouble()
    val low = (raw and 0xFFFFFFFFL).toUInt().toDouble()
    return high * 4294967296.0 + low
}

internal fun readExtended80BitFloat(bytes: ByteArray): Double {
    require(bytes.size >= 10)
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val sign = b0 shr 7
    val exp = ((b0 and 0x7F) shl 8) or b1
    var mantissa = 0L
    for (i in 2..9) {
        mantissa = (mantissa shl 8) or (bytes[i].toLong() and 0xFF)
    }
    if (exp == 0 && mantissa == 0L) return if (sign != 0) -0.0 else 0.0
    if (exp == 0 && mantissa != 0L)
        throw AudioFileReadError.UnsupportedFormat("Unsupported extended sample rate (denormal).")
    if (exp == 0x7FFF)
        throw AudioFileReadError.InvalidFile("Invalid COMM sample rate (infinity or NaN).")
    val unbiased = exp - 16383
    val sig = uint64BitsToDouble(mantissa) / 9223372036854775808.0
    var v = sig * 2.0.pow(unbiased.toDouble())
    if (sign != 0) v = -v
    return v
}

internal fun writeExtended80BitFloat(value: Double): ByteArray {
    if (value == 0.0) return ByteArray(10)
    if (!value.isFinite() || value < 0.0)
        throw AudioFileWriteError.UnsupportedFormat("Sample rate must be finite and non-negative.")
    val bits = value.toBits()
    val sign = (bits ushr 63).toInt()
    val expDouble = ((bits ushr 52) and 0x7FFL).toInt()
    val frac = bits and 0xFFFFFFFFFFFFFL
    val (trueExp, mant53) = if (expDouble == 0) {
        if (frac == 0L) return ByteArray(10)
        var f = frac
        var e = -1022
        while (f != 0L && (f and (1L shl 52)) == 0L) {
            f = f shl 1
            e--
        }
        e to f
    } else {
        (expDouble - 1023) to (frac or (1L shl 52))
    }
    val extExp = trueExp + 16383
    if (extExp < 0 || extExp > 0x7FFE)
        throw AudioFileWriteError.UnsupportedFormat("Sample rate out of range for AIFF.")
    val mant64 = mant53 shl 11
    val out = ByteArray(10)
    val expU = extExp and 0x7FFF
    out[0] = ((sign shl 7) or (expU shr 8)).toByte()
    out[1] = (expU and 0xFF).toByte()
    var m = mant64
    for (i in 9 downTo 2) {
        out[i] = (m and 0xFF).toByte()
        m = m ushr 8
    }
    return out
}
