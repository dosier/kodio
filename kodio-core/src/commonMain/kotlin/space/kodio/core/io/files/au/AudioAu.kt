package space.kodio.core.io.files.au

import kotlinx.io.*
import kotlin.time.Duration.Companion.milliseconds
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileWriteError
import space.kodio.core.io.files.metadata.AudioEncodingInfo
import space.kodio.core.io.files.metadata.AudioFileMetadata
import space.kodio.core.io.files.metadata.AudioTags
import space.kodio.core.io.readIntBe
import space.kodio.core.io.swapEndianness
import space.kodio.core.io.writeIntBe

private const val AU_MAGIC = 0x2E736E64
private const val AU_DATA_SIZE_UNKNOWN = 0xFFFFFFFFL

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
    val payload = pcmBytes.swapEndianness(bps)

    val dataOffset = 24
    val dataSizeField = when {
        from.byteCount > Int.MAX_VALUE -> AU_DATA_SIZE_UNKNOWN.toInt()
        else -> from.byteCount.toInt()
    }
    val encodingCode = auEncodingCode(format.encoding)

    with(to) {
        writeIntBe(AU_MAGIC)
        writeIntBe(dataOffset)
        writeIntBe(dataSizeField)
        writeIntBe(encodingCode)
        writeIntBe(format.sampleRate)
        writeIntBe(format.channels.count)
    }
    to.write(payload)
}

internal fun readAu(from: Source): AudioSource {
    val header = readAuHeader(from)
    val rawBytes = readAuPayload(from, header)
    val pcmBytes = rawBytes.swapEndianness(header.bytesPerSample)
    val format = AudioFormat(
        sampleRate = header.sampleRate,
        channels = Channels.fromInt(header.channelCount),
        encoding = header.encoding,
    )
    return AudioSource.of(format, *pcmBytes)
}

internal fun readAuMetadata(from: Source): AudioFileMetadata {
    val header = readAuHeader(from)
    skipAuPayload(from, header)
    val sampleEncoding = when (header.encoding) {
        is PcmInt -> AudioEncodingInfo.Pcm(header.encoding)
        is PcmFloat -> AudioEncodingInfo.Pcm(header.encoding)
    }
    val bytesPerFrame = header.channelCount * header.bytesPerSample
    val duration = if (header.dataSize != AU_DATA_SIZE_UNKNOWN && bytesPerFrame > 0 && header.sampleRate > 0) {
        val frames = header.dataSize / bytesPerFrame
        (frames * 1000L / header.sampleRate).milliseconds
    } else {
        null
    }
    return AudioFileMetadata(
        container = AudioFileFormat.Au,
        sampleRate = header.sampleRate,
        channels = Channels.fromInt(header.channelCount),
        encoding = sampleEncoding,
        duration = duration,
        tags = AudioTags.Empty,
    )
}

private data class AuHeader(
    val dataOffset: Long,
    val dataSize: Long,
    val encoding: SampleEncoding,
    val sampleRate: Int,
    val channelCount: Int,
    val bytesPerSample: Int,
)

private fun readAuHeader(from: Source): AuHeader {
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

    val encoding = auEncodingFromCode(encodingCode)
    val bps = bytesPerSampleForWrite(encoding)

    return AuHeader(
        dataOffset = dataOffset,
        dataSize = dataSize,
        encoding = encoding,
        sampleRate = sampleRate,
        channelCount = channelCount,
        bytesPerSample = bps,
    )
}

private fun auEncodingFromCode(encodingCode: Int): SampleEncoding = when (encodingCode) {
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

private fun readAuPayload(from: Source, header: AuHeader): ByteArray = try {
    if (header.dataSize == AU_DATA_SIZE_UNKNOWN) {
        from.readByteArray()
    } else {
        val buf = Buffer()
        from.readTo(buf, header.dataSize)
        buf.readByteArray()
    }
} catch (e: Exception) {
    throw AudioFileReadError.IO(e)
}

private fun skipAuPayload(from: Source, header: AuHeader) {
    try {
        if (header.dataSize == AU_DATA_SIZE_UNKNOWN) {
            from.readByteArray()
        } else {
            from.skip(header.dataSize)
        }
    } catch (e: Exception) {
        throw AudioFileReadError.IO(e)
    }
}
