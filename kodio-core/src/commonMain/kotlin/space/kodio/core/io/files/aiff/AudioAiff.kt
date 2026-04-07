package space.kodio.core.io.files.aiff

import kotlinx.io.*
import kotlin.math.pow
import kotlin.math.roundToInt
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileWriteError

private fun Source.readIntBe(): Int {
    val b0 = readByte().toInt() and 0xFF
    val b1 = readByte().toInt() and 0xFF
    val b2 = readByte().toInt() and 0xFF
    val b3 = readByte().toInt() and 0xFF
    return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
}

private fun Sink.writeIntBe(value: Int) {
    writeByte(((value ushr 24) and 0xFF).toByte())
    writeByte(((value ushr 16) and 0xFF).toByte())
    writeByte(((value ushr 8) and 0xFF).toByte())
    writeByte((value and 0xFF).toByte())
}

private fun Source.readUInt16Be(): Int {
    val h = readByte().toInt() and 0xFF
    val l = readByte().toInt() and 0xFF
    return (h shl 8) or l
}

private fun Sink.writeUInt16Be(value: Int) {
    writeByte(((value ushr 8) and 0xFF).toByte())
    writeByte((value and 0xFF).toByte())
}

private fun Source.skipChunkPadded(dataSize: Long) {
    skip(dataSize)
    if (dataSize and 1L == 1L) skip(1)
}

private fun Source.readExactly(byteCount: Int): ByteArray {
    val buf = Buffer()
    readTo(buf, byteCount.toLong())
    return buf.readByteArray()
}

private fun swapEndianness(data: ByteArray, bytesPerSample: Int): ByteArray {
    if (bytesPerSample <= 1) return data
    val result = data.copyOf()
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

private fun extended80ToDouble(bytes: ByteArray): Double {
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

private fun doubleToExtended80(value: Double): ByteArray {
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

internal fun writeAiff(from: AudioSource, to: Sink) {
    val format = from.format
    val enc = when (val e = format.encoding) {
        is PcmFloat ->
            throw AudioFileWriteError.UnsupportedFormat("AIFF does not support IEEE float (use AIFF-C).")
        is PcmInt -> {
            if (e.layout == SampleLayout.Planar)
                throw AudioFileWriteError.UnsupportedFormat("AIFF writer requires interleaved PCM-Int.")
            if (e.endianness != Endianness.Little)
                throw AudioFileWriteError.UnsupportedFormat("AIFF writer expects little-endian PCM-Int input.")
            if (!e.packed)
                throw AudioFileWriteError.UnsupportedFormat("AIFF writer requires packed PCM-Int.")
            if (e.bitDepth == IntBitDepth.Eight && !e.signed)
                throw AudioFileWriteError.UnsupportedFormat("AIFF 8-bit PCM is signed; unsigned input is not supported.")
            e
        }
    }
    val bitsPerSample = enc.bitDepth.bits
    val bytesPerSample = bitsPerSample / 8
    if (from.byteCount % format.bytesPerFrame != 0L)
        throw AudioFileWriteError.UnsupportedFormat("Audio byte count is not aligned to frame size.")
    val framesLong = from.byteCount / format.bytesPerFrame
    if (framesLong > 0xFFFFFFFFL)
        throw AudioFileWriteError.UnsupportedFormat("AIFF frame count does not fit in 32 bits.")
    val numSampleFrames = (framesLong and 0xFFFFFFFFL).toInt()
    val pcmBytes = try {
        Buffer().apply { write(from.source, from.byteCount) }.readByteArray()
    } catch (e: Exception) {
        throw AudioFileWriteError.IO(e)
    }
    val payload = swapEndianness(pcmBytes, bytesPerSample)
    val ssndChunkDataSize = 8 + payload.size
    val ssndPad = ssndChunkDataSize and 1
    val formBodySize = 4 + (8 + 18) + (8 + ssndChunkDataSize + ssndPad)
    with(to) {
        writeString("FORM")
        writeIntBe(formBodySize)
        writeString("AIFF")
        writeString("COMM")
        writeIntBe(18)
        writeUInt16Be(format.channels.count)
        writeIntBe(numSampleFrames)
        writeUInt16Be(bitsPerSample)
        write(doubleToExtended80(format.sampleRate.toDouble()))
        writeString("SSND")
        writeIntBe(ssndChunkDataSize)
        writeIntBe(0)
        writeIntBe(0)
    }
    to.write(payload)
    if (ssndPad == 1) to.write(byteArrayOf(0))
}

internal fun readAiff(from: Source): AudioSource {
    // --- FORM header ---
    val formId: String
    val formSize: Long
    val aiffType: String
    try {
        formId = from.readString(4)
        formSize = from.readIntBe().toLong() and 0xFFFFFFFFL
        aiffType = from.readString(4)
    } catch (e: Exception) {
        throw AudioFileReadError.InvalidFile("Cannot read AIFF FORM header: file is too short or unreadable.", cause = e)
    }
    if (formId != "FORM")
        throw AudioFileReadError.InvalidFile("Not a FORM file (got '$formId').")
    if (aiffType != "AIFF")
        throw AudioFileReadError.InvalidFile("Not a classic AIFF file (got '$aiffType').")

    // --- Chunk scanning ---
    var commFound = false
    var numChannels = -1
    var numSampleFrames = -1L
    var bitsPerSample = -1
    lateinit var rateBytes: ByteArray
    var innerRemaining = formSize - 4L

    while (innerRemaining >= 8L) {
        val chunkId: String
        val chunkSize: Long
        try {
            chunkId = from.readString(4)
            chunkSize = from.readIntBe().toLong() and 0xFFFFFFFFL
        } catch (e: Exception) {
            throw AudioFileReadError.InvalidFile("Unexpected end of file inside AIFF container.", cause = e)
        }
        innerRemaining -= 8L
        if (chunkSize > innerRemaining)
            throw AudioFileReadError.InvalidFile("AIFF chunk '$chunkId' extends past FORM data.")

        when (chunkId) {
            "COMM" -> {
                if (chunkSize != 18L)
                    throw AudioFileReadError.InvalidFile("COMM chunk must be 18 bytes, got $chunkSize.")
                try {
                    numChannels = from.readUInt16Be()
                    numSampleFrames = from.readIntBe().toLong() and 0xFFFFFFFFL
                    bitsPerSample = from.readUInt16Be()
                    rateBytes = from.readExactly(10)
                } catch (e: Exception) {
                    throw AudioFileReadError.InvalidFile("Failed to read COMM chunk.", cause = e)
                }
                if (numChannels <= 0 || numChannels > 2)
                    throw AudioFileReadError.UnsupportedFormat("AIFF channel count $numChannels is not supported (only 1 or 2).")
                if (numSampleFrames > Int.MAX_VALUE.toLong())
                    throw AudioFileReadError.UnsupportedFormat("COMM numSampleFrames is too large.")
                commFound = true
                innerRemaining -= chunkSize
                if (chunkSize and 1L == 1L) {
                    from.skip(1)
                    innerRemaining -= 1L
                }
            }
            "SSND" -> {
                if (!commFound)
                    throw AudioFileReadError.InvalidFile("'SSND' chunk found before 'COMM' chunk.")
                if (chunkSize < 8L)
                    throw AudioFileReadError.InvalidFile("SSND chunk too small.")

                val offset: Long
                val blockSize: Long
                try {
                    offset = from.readIntBe().toLong() and 0xFFFFFFFFL
                    blockSize = from.readIntBe().toLong() and 0xFFFFFFFFL
                } catch (e: Exception) {
                    throw AudioFileReadError.InvalidFile("Failed to read SSND chunk header.", cause = e)
                }
                if (blockSize != 0L)
                    throw AudioFileReadError.UnsupportedFormat("Non-zero SSND blockSize is not supported.")
                val waveBytes = chunkSize - 8L
                if (offset > waveBytes)
                    throw AudioFileReadError.InvalidFile("SSND offset exceeds chunk sound data.")
                val pcmLength = (waveBytes - offset).toInt()
                if (pcmLength.toLong() != waveBytes - offset)
                    throw AudioFileReadError.UnsupportedFormat("SSND PCM length is too large.")

                val rawPcm: ByteArray
                try {
                    if (offset > 0L) from.skip(offset)
                    val buf = Buffer()
                    from.readTo(buf, pcmLength.toLong())
                    rawPcm = buf.readByteArray()
                    if (chunkSize and 1L == 1L) from.skip(1)
                } catch (e: Exception) {
                    throw AudioFileReadError.IO(e)
                }
                innerRemaining -= chunkSize
                if (chunkSize and 1L == 1L) innerRemaining -= 1L

                val sampleRateDouble = try {
                    extended80ToDouble(rateBytes)
                } catch (e: AudioFileReadError) {
                    throw e
                } catch (e: Exception) {
                    throw AudioFileReadError.InvalidFile("Invalid COMM sample rate: ${e.message}", cause = e)
                }
                if (!sampleRateDouble.isFinite() || sampleRateDouble <= 0.0)
                    throw AudioFileReadError.InvalidFile("Invalid COMM sample rate.")

                val sampleRate = sampleRateDouble.roundToInt()
                val bitDepth = when (bitsPerSample) {
                    8 -> IntBitDepth.Eight
                    16 -> IntBitDepth.Sixteen
                    24 -> IntBitDepth.TwentyFour
                    32 -> IntBitDepth.ThirtyTwo
                    else -> throw AudioFileReadError.UnsupportedFormat("Unsupported AIFF bit depth: $bitsPerSample")
                }
                val bytesPerSample = bitsPerSample / 8
                val expectedFrames = if (bytesPerSample > 0) rawPcm.size / (bytesPerSample * numChannels) else 0
                if (rawPcm.size % (bytesPerSample * numChannels) != 0)
                    throw AudioFileReadError.InvalidFile("SSND PCM size is not aligned to frames.")
                if (expectedFrames.toLong() != numSampleFrames)
                    throw AudioFileReadError.InvalidFile(
                        "SSND frame count mismatch: COMM says $numSampleFrames, data implies $expectedFrames."
                    )
                val encoding = PcmInt(
                    bitDepth = bitDepth,
                    endianness = Endianness.Little,
                    layout = SampleLayout.Interleaved,
                    signed = true,
                    packed = true,
                )
                val format = AudioFormat(
                    sampleRate = sampleRate,
                    channels = Channels.fromInt(numChannels),
                    encoding = encoding,
                )
                val pcmLe = swapEndianness(rawPcm, bytesPerSample)
                val buffer = Buffer().apply { write(pcmLe) }
                return AudioSource.of(format, buffer)
            }
            else -> {
                try {
                    from.skipChunkPadded(chunkSize)
                } catch (e: Exception) {
                    throw AudioFileReadError.IO(e)
                }
                innerRemaining -= chunkSize
                if (chunkSize and 1L == 1L) innerRemaining -= 1L
            }
        }
    }
    throw AudioFileReadError.InvalidFile("No SSND chunk found in AIFF file.")
}
