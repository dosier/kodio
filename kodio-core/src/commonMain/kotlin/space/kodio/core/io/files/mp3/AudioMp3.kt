package space.kodio.core.io.files.mp3

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import space.kodio.core.AudioFormat
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.IntBitDepth
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.metadata.AudioEncodingInfo
import space.kodio.core.io.files.metadata.AudioFileMetadata
import space.kodio.core.io.files.mp3.id3.Id3Parser
import space.kodio.core.util.namedLogger

private val logger = namedLogger("AudioMp3")

private val Pcm16Le = SampleEncoding.PcmInt(
    bitDepth = IntBitDepth.Sixteen,
    endianness = Endianness.Little,
    layout = SampleLayout.Interleaved,
    signed = true,
)

/** Cold path: kotlinx-io [Source] → bytes, then [ByteArray] decode (see [Mp3Decoder]). */
internal fun readMp3(source: Source): AudioSource {
    val bytes = source.readByteArray()
    return decodeMp3Bytes(bytes)
}

/** Cold path: kotlinx-io [Source] → bytes for ID3 / Xing / frame-header metadata. */
internal fun readMp3Metadata(source: Source): AudioFileMetadata {
    val bytes = source.readByteArray()
    return readMp3MetadataBytes(bytes)
}

internal fun decodeMp3Bytes(bytes: ByteArray): AudioSource {
    try {
        val decoded = Mp3Decoder.decode(bytes)
        val format = AudioFormat(
            sampleRate = decoded.sampleRate,
            channels = Channels.fromInt(decoded.channels),
            encoding = Pcm16Le,
        )
        logger.info {
            "Decoded MP3: ${decoded.pcm.size} PCM bytes, ${format.sampleRate}Hz ${format.channels}"
        }
        return AudioSource.of(format, Buffer().apply { write(decoded.pcm) })
    } catch (e: AudioFileReadError) {
        throw e
    } catch (e: Exception) {
        throw AudioFileReadError.InvalidFile("Failed to decode MP3: ${e.message}", e)
    }
}

internal fun readMp3MetadataBytes(bytes: ByteArray): AudioFileMetadata {
    val tags = Id3Parser.parse(bytes)
    val found = Mp3FrameHeader.findFirst(bytes)
        ?: throw AudioFileReadError.InvalidFile("No MPEG audio frame found in MP3 data")
    val (frameOffset, header) = found
    if (header.layer != 3) {
        throw AudioFileReadError.UnsupportedFormat(
            "Only MPEG Layer III is supported for metadata (found Layer ${header.layer})"
        )
    }

    val xing = Mp3XingHeader.tryParse(bytes, frameOffset, header)
    val duration = durationFrom(header, xing, bytes.size - frameOffset)
    val vbr = xing?.vbr == true
    val bitrate = when {
        xing?.frames != null && xing.frames > 0 && xing.bytes != null && xing.bytes > 0 -> {
            val samples = xing.frames.toLong() * header.samplesPerFrame
            if (samples > 0) {
                ((xing.bytes.toLong() * 8L * header.sampleRate) / samples).toInt()
            } else {
                header.bitrate
            }
        }
        else -> header.bitrate
    }

    logger.debug {
        "MP3 metadata: ${header.sampleRate}Hz ch=${header.channels} " +
            "bitrate=$bitrate vbr=$vbr duration=$duration"
    }

    return AudioFileMetadata(
        container = AudioFileFormat.Mp3,
        sampleRate = header.sampleRate,
        channels = Channels.fromInt(header.channels),
        encoding = AudioEncodingInfo.Compressed.Mp3(
            bitrate = bitrate,
            vbr = vbr,
            mpegVersion = header.mpegVersion,
            layer = header.layer,
            encoderDelay = xing?.encoderDelay,
            encoderPadding = xing?.encoderPadding,
        ),
        duration = duration,
        tags = tags,
    )
}

private fun durationFrom(
    header: Mp3FrameHeader,
    xing: Mp3XingHeader?,
    streamBytes: Int,
): Duration? {
    val frames = xing?.frames
    if (frames != null && frames > 0) {
        val ms = frames.toLong() * header.samplesPerFrame * 1000L / header.sampleRate
        return ms.milliseconds
    }
    if (header.bitrate > 0 && streamBytes > 0) {
        val ms = streamBytes.toLong() * 8000L / header.bitrate
        return ms.milliseconds
    }
    return null
}
