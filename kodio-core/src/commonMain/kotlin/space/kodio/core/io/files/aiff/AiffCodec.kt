package space.kodio.core.io.files.aiff

import kotlinx.io.Source
import kotlinx.io.Sink
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileCodec
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.metadata.AudioFileMetadata

internal object AiffCodec : AudioFileCodec {
    override val format: AudioFileFormat = AudioFileFormat.Aiff
    override val extensions: Set<String> = setOf("aiff", "aif", "aifc")
    override val canEncode: Boolean = true

    override fun matchesMagic(prefix: ByteArray): Boolean {
        if (prefix.size < 12) return false
        if (prefix[0] != 'F'.code.toByte() || prefix[1] != 'O'.code.toByte() ||
            prefix[2] != 'R'.code.toByte() || prefix[3] != 'M'.code.toByte()
        ) return false
        val formType = prefix.copyOfRange(8, 12).decodeToString()
        return formType == "AIFF" || formType == "AIFC"
    }

    override fun readMetadata(source: Source): AudioFileMetadata = readAiffMetadata(source)

    override fun decode(source: Source): AudioSource = readAiff(source)

    override fun encode(from: AudioSource, to: Sink) = writeAiff(from, to)
}
