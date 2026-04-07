package space.kodio.core.io.files.au

import kotlinx.io.*
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileWriteError

private const val AU_MAGIC = 0x2E736E64
private const val AU_DATA_SIZE_UNKNOWN = 0xFFFFFFFFL

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

private fun bytesPerSampleForWrite(encoding: SampleEncoding): Int = when (encoding) {
    is PcmInt -> encoding.bitDepth.bits / 8
    is PcmFloat -> when (encoding.precision) {
        FloatPrecision.F32 -> 4
        FloatPrecision.F64 -> 8
    }
}

private fun auEncodingCode(encoding: SampleEncoding): Int = when (encoding) {
    is PcmInt -> when (encoding.bitDepth) {
        IntBitDepth.Eight -> 2
        IntBitDepth.Sixteen -> 3
        IntBitDepth.TwentyFour -> 4
        IntBitDepth.ThirtyTwo -> 5
    }
    is PcmFloat -> when (encoding.precision) {
        FloatPrecision.F32 -> 6
        FloatPrecision.F64 -> 7
    }
}

internal fun writeAu(from: AudioSource, to: Sink) {
    val format = from.format
    when (val enc = format.encoding) {
        is PcmInt -> {
            if (enc.layout == SampleLayout.Planar)
                throw AudioFileWriteError.UnsupportedFormat("AU writer requires interleaved PCM-Int.")
            if (enc.endianness != Endianness.Little)
                throw AudioFileWriteError.UnsupportedFormat("AU writer expects little-endian PCM-Int input.")
            if (!enc.signed)
                throw AudioFileWriteError.UnsupportedFormat("AU linear PCM is signed; unsigned 8-bit is not supported.")
        }
        is PcmFloat -> {
            if (enc.layout == SampleLayout.Planar)
                throw AudioFileWriteError.UnsupportedFormat("AU writer requires interleaved PCM-Float.")
        }
    }

    val pcmBytes = try {
        Buffer().apply { write(from.source, from.byteCount) }.readByteArray()
    } catch (e: Exception) {
        throw AudioFileWriteError.IO(e)
    }

    val bps = bytesPerSampleForWrite(format.encoding)
    val payload = swapEndianness(pcmBytes, bps)

    val dataOffset = 24
    val dataSize = from.byteCount.toInt()
    val encodingCode = auEncodingCode(format.encoding)

    with(to) {
        writeIntBe(AU_MAGIC)
        writeIntBe(dataOffset)
        writeIntBe(dataSize)
        writeIntBe(encodingCode)
        writeIntBe(format.sampleRate)
        writeIntBe(format.channels.count)
    }
    to.write(payload)
}

internal fun readAu(from: Source): AudioSource {
    // --- Header (6 big-endian int32 fields) ---
    val magic: Int
    val dataOffset: Long
    val dataSize: Long
    val encodingCode: Int
    val sampleRate: Int
    val channelCount: Int
    try {
        magic = from.readIntBe()
        dataOffset = from.readIntBe().toLong() and 0xFFFFFFFFL
        dataSize = from.readIntBe().toLong() and 0xFFFFFFFFL
        encodingCode = from.readIntBe()
        sampleRate = from.readIntBe()
        channelCount = from.readIntBe()
    } catch (e: Exception) {
        throw AudioFileReadError.InvalidFile("Cannot read AU header: file is too short or unreadable.", cause = e)
    }
    if (magic != AU_MAGIC)
        throw AudioFileReadError.InvalidFile("Not an AU file (magic 0x${magic.toUInt().toString(16)}).")
    if (dataOffset < 24L)
        throw AudioFileReadError.InvalidFile("Invalid AU data offset: $dataOffset")
    if (sampleRate <= 0)
        throw AudioFileReadError.InvalidFile("Invalid AU sample rate: $sampleRate")
    if (channelCount <= 0 || channelCount > 2)
        throw AudioFileReadError.UnsupportedFormat("AU channel count $channelCount is not supported (only 1 or 2).")

    val annotationBytes = dataOffset - 24L
    if (annotationBytes > 0) {
        try {
            from.skip(annotationBytes)
        } catch (e: Exception) {
            throw AudioFileReadError.IO(e)
        }
    }

    val encoding: SampleEncoding = when (encodingCode) {
        2 -> PcmInt(
            bitDepth = IntBitDepth.Eight,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        )
        3 -> PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        )
        4 -> PcmInt(
            bitDepth = IntBitDepth.TwentyFour,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        )
        5 -> PcmInt(
            bitDepth = IntBitDepth.ThirtyTwo,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        )
        6 -> PcmFloat(precision = FloatPrecision.F32, layout = SampleLayout.Interleaved)
        7 -> PcmFloat(precision = FloatPrecision.F64, layout = SampleLayout.Interleaved)
        else -> throw AudioFileReadError.UnsupportedFormat("Unsupported AU encoding code: $encodingCode")
    }

    val bps = when (encoding) {
        is PcmInt -> encoding.bitDepth.bits / 8
        is PcmFloat -> when (encoding.precision) {
            FloatPrecision.F32 -> 4
            FloatPrecision.F64 -> 8
        }
    }

    val rawBytes: ByteArray = try {
        if (dataSize == AU_DATA_SIZE_UNKNOWN) {
            from.readByteArray()
        } else {
            val buf = Buffer()
            from.readTo(buf, dataSize)
            buf.readByteArray()
        }
    } catch (e: Exception) {
        throw AudioFileReadError.IO(e)
    }

    val pcmBytes = swapEndianness(rawBytes, bps)
    val format = AudioFormat(
        sampleRate = sampleRate,
        channels = Channels.fromInt(channelCount),
        encoding = encoding,
    )
    return AudioSource.of(format, *pcmBytes)
}
