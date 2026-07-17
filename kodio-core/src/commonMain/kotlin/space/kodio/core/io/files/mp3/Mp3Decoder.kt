package space.kodio.core.io.files.mp3

import space.kodio.core.util.namedLogger

private val logger = namedLogger("Mp3Decoder")

/**
 * High-level MP3 decode API wrapping [Mp3MpegDecoder].
 *
 * Output is interleaved signed 16-bit little-endian PCM.
 */
internal object Mp3Decoder {
    class Result(
        val pcm: ByteArray,
        val sampleRate: Int,
        val channels: Int,
    )

    fun decode(bytes: ByteArray): Result {
        val soundData = Mp3MpegDecoder.init(bytes)
            ?: throw IllegalArgumentException("No MPEG audio frames found")
        val sampleRate = soundData.frequency
        val channels = if (soundData.stereo == 1) 2 else 1
        logger.debug { "Decoding MP3: ${sampleRate}Hz, channels=$channels" }

        val out = ArrayList<Byte>()
        // First frame already decoded by init()
        appendSamples(out, soundData.samplesBuffer)
        try {
            while (Mp3MpegDecoder.decodeFrame(soundData)) {
                appendSamples(out, soundData.samplesBuffer)
            }
        } catch (e: Exception) {
            // Truncated / non-conformant bitstreams may throw mid-stream after valid frames.
            // Keep PCM decoded so far when we already have samples.
            if (out.isEmpty()) throw e
            logger.warn(e) { "MP3 decode stopped early after ${out.size} PCM bytes: ${e.message}" }
        }
        if (out.isEmpty()) {
            throw IllegalArgumentException("MP3 decode produced no PCM samples")
        }
        return Result(
            pcm = out.toByteArray(),
            sampleRate = sampleRate,
            channels = channels,
        )
    }

    private fun appendSamples(out: ArrayList<Byte>, buffer: ByteArray?) {
        if (buffer == null || buffer.isEmpty()) return
        for (b in buffer) out.add(b)
    }
}
