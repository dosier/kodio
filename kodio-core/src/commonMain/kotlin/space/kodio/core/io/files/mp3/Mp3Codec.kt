package space.kodio.core.io.files.mp3

import kotlinx.io.Sink
import kotlinx.io.Source
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileCodec
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileWriteError
import space.kodio.core.io.files.metadata.AudioFileMetadata
import space.kodio.core.io.files.mp3.Mp3Constants.FRAME_HEADER_BYTES
import space.kodio.core.io.files.mp3.Mp3Constants.MAGIC_PROBE_SCAN_BYTES

/**
 * MP3 codec entry for [AudioFileCodecs]: decode + metadata only (no encode).
 */
internal object Mp3Codec : AudioFileCodec {
    override val format: AudioFileFormat = AudioFileFormat.Mp3
    override val extensions: Set<String> = setOf("mp3")
    override val canEncode: Boolean = false

    override fun matchesMagic(prefix: ByteArray): Boolean {
        if (prefix.size >= 3 &&
            prefix[0] == 'I'.code.toByte() &&
            prefix[1] == 'D'.code.toByte() &&
            prefix[2] == '3'.code.toByte()
        ) {
            return true
        }
        // Scan prefix for a validated MPEG frame header (not bare 0xFFE sync).
        val limit = minOf(prefix.size - FRAME_HEADER_BYTES, MAGIC_PROBE_SCAN_BYTES)
        for (i in 0..limit) {
            if (Mp3FrameHeader.tryParse(prefix, i) != null) return true
        }
        return false
    }

    override fun readMetadata(source: Source): AudioFileMetadata = readMp3Metadata(source)

    override fun decode(source: Source): AudioSource = readMp3(source)

    override fun encode(from: AudioSource, to: Sink) {
        throw AudioFileWriteError.UnsupportedFormat("MP3 encoding is not supported")
    }
}
