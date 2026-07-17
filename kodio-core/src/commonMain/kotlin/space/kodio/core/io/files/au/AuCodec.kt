package space.kodio.core.io.files.au

import kotlinx.io.Source
import kotlinx.io.Sink
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileCodec
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.metadata.AudioFileMetadata

internal object AuCodec : AudioFileCodec {
    override val format: AudioFileFormat = AudioFileFormat.Au
    override val extensions: Set<String> = setOf("au", "snd")
    override val canEncode: Boolean = true

    override fun matchesMagic(prefix: ByteArray): Boolean =
        prefix.size >= 4 &&
            prefix[0] == 0x2E.toByte() &&
            prefix[1] == 's'.code.toByte() &&
            prefix[2] == 'n'.code.toByte() &&
            prefix[3] == 'd'.code.toByte()

    override fun readMetadata(source: Source): AudioFileMetadata = readAuMetadata(source)

    override fun decode(source: Source): AudioSource = readAu(source)

    override fun encode(from: AudioSource, to: Sink) = writeAu(from, to)
}
