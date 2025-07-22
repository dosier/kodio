package space.kodio.core.io

import kotlinx.io.Buffer
import kotlinx.io.Source
import space.kodio.core.AudioFormat

/**
 * A simple container for raw audio data and its format.
 */
class AudioSource private constructor(
    val source: Source,
    val format: AudioFormat,
    val byteCount: Long
) {
    companion object {
        fun of(format: AudioFormat, buffer: Buffer) = AudioSource(buffer, format, buffer.size)
        fun of(format: AudioFormat, vararg bytes: Byte) = AudioSource(Buffer().apply { write(bytes) }, format, bytes.size.toLong())
    }
}