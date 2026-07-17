package space.kodio.core.io.files.wav

import kotlinx.io.Source
import kotlinx.io.Sink
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileCodec
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.metadata.AudioFileMetadata

internal object WavCodec : AudioFileCodec {
    override val format: AudioFileFormat = AudioFileFormat.Wav
    override val extensions: Set<String> = setOf("wav", "wave")
    override val canEncode: Boolean = true

    override fun matchesMagic(prefix: ByteArray): Boolean =
        prefix.size >= 12 &&
            prefix[0] == 'R'.code.toByte() &&
            prefix[1] == 'I'.code.toByte() &&
            prefix[2] == 'F'.code.toByte() &&
            prefix[3] == 'F'.code.toByte() &&
            prefix[8] == 'W'.code.toByte() &&
            prefix[9] == 'A'.code.toByte() &&
            prefix[10] == 'V'.code.toByte() &&
            prefix[11] == 'E'.code.toByte()

    override fun readMetadata(source: Source): AudioFileMetadata = readWavMetadata(source)

    override fun decode(source: Source): AudioSource = readWav(source)

    override fun encode(from: AudioSource, to: Sink) = writeWav(from, to)
}
