package space.kodio.core.io.files.mp3

import space.kodio.core.io.files.mp3.minimp3.MiniMp3
import space.kodio.core.util.namedLogger

private val logger = namedLogger("Mp3Decoder")

/**
 * High-level MP3 decode API wrapping the minimp3-based core.
 *
 * Output is interleaved signed 16-bit little-endian PCM.
 *
 * **I/O strategy:** decode consumes a [ByteArray]. Frame scanning, the Layer III
 * bit reservoir, and Huffman decoding need indexed random access; kotlinx-io
 * [kotlinx.io.Source] is sequential and would add overhead on this hot path.
 * Prefer kotlinx-io at the codec boundary ([AudioMp3] reads the file into bytes /
 * parses ID3), then decode from the array.
 */
internal object Mp3Decoder {
    class Result(
        val pcm: ByteArray,
        val sampleRate: Int,
        val channels: Int,
    )

    fun decode(bytes: ByteArray): Result {
        require(bytes.isNotEmpty()) { "Empty MP3 buffer" }
        logger.debug { "Decoding MP3 buffer: ${bytes.size} bytes" }
        val (pcm, sampleRate, channels) = try {
            MiniMp3.decodeAll(bytes)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException(e.message ?: "MP3 decode failed", e)
        } catch (e: IllegalArgumentException) {
            throw e
        }
        logger.debug { "Decoded MP3: ${sampleRate}Hz, channels=$channels, pcm=${pcm.size} bytes" }
        return Result(pcm = pcm, sampleRate = sampleRate, channels = channels)
    }
}
