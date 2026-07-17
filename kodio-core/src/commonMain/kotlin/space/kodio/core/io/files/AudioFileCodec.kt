package space.kodio.core.io.files

import kotlinx.io.Source
import kotlinx.io.Sink
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.aiff.AiffCodec
import space.kodio.core.io.files.au.AuCodec
import space.kodio.core.io.files.metadata.AudioFileMetadata
import space.kodio.core.io.files.wav.WavCodec

internal interface AudioFileCodec {
    val format: AudioFileFormat
    val extensions: Set<String>
    val canEncode: Boolean

    fun matchesMagic(prefix: ByteArray): Boolean
    fun readMetadata(source: Source): AudioFileMetadata
    fun decode(source: Source): AudioSource
    fun encode(from: AudioSource, to: Sink)
}

internal object AudioFileCodecs {
    val all: List<AudioFileCodec> = listOf(WavCodec, AiffCodec, AuCodec)

    fun of(format: AudioFileFormat): AudioFileCodec =
        all.firstOrNull { it.format == format }
            ?: throw AudioFileReadError.UnsupportedFormat("Unsupported audio file format: $format")

    fun probe(prefix: ByteArray): AudioFileCodec? {
        val matches = all.filter { it.matchesMagic(prefix) }
        return when (matches.size) {
            0 -> null
            1 -> matches[0]
            else -> throw AudioFileReadError.UnsupportedFormat("Ambiguous format magic")
        }
    }

    fun fromExtension(extension: String): AudioFileCodec? {
        val ext = extension.removePrefix(".").lowercase()
        return all.firstOrNull { ext in it.extensions }
    }
}

private const val MIN_PROBE_PREFIX = 4

/**
 * Resolves the codec to use for reading/decoding.
 *
 * @throws AudioFileReadError.UnsupportedFormat if format cannot be determined
 * @throws AudioFileReadError.InvalidFile if bytes are empty / too short
 */
internal fun resolveCodec(
    prefix: ByteArray,
    fileName: String? = null,
    explicit: AudioFileFormat? = null,
): AudioFileCodec {
    if (prefix.isEmpty()) {
        throw AudioFileReadError.InvalidFile("Empty audio data.")
    }

    if (explicit != null) {
        return AudioFileCodecs.of(explicit)
    }

    val magicCodec = try {
        AudioFileCodecs.probe(prefix)
    } catch (e: AudioFileReadError.UnsupportedFormat) {
        throw e
    }

    if (magicCodec != null) {
        return magicCodec
    }

    val ext = fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    if (ext != null) {
        AudioFileCodecs.fromExtension(ext)?.let { return it }
    }

    if (prefix.size < MIN_PROBE_PREFIX) {
        throw AudioFileReadError.InvalidFile(
            "Audio data too short to detect format (${prefix.size} bytes)."
        )
    }

    val nameHint = fileName?.let { "fileName='$it'" } ?: "no fileName"
    throw AudioFileReadError.UnsupportedFormat(
        "Could not detect audio format (no magic match; $nameHint)"
    )
}

internal fun extensionFromFileName(fileName: String): String? =
    fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.lowercase()

internal fun probePrefix(bytes: ByteArray, maxLength: Int = 64): ByteArray =
    bytes.copyOfRange(0, minOf(bytes.size, maxLength))
