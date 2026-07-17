package space.kodio.core.io.files.metadata

import space.kodio.core.SampleEncoding

/**
 * Describes how audio samples are encoded inside a container.
 */
sealed interface AudioEncodingInfo {
    data class Pcm(val encoding: SampleEncoding) : AudioEncodingInfo

    sealed interface Compressed : AudioEncodingInfo {
        val bitrate: Int?
        val vbr: Boolean

        data class Mp3(
            override val bitrate: Int?,
            override val vbr: Boolean,
            val mpegVersion: Int,
            val layer: Int,
            val encoderDelay: Int? = null,
            val encoderPadding: Int? = null,
        ) : Compressed
    }
}
