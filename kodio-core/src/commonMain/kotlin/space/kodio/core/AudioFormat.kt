package space.kodio.core

/**
 * Defines the format of the audio data.
 */
data class AudioFormat(
    val sampleRate: Int,
    val bitDepth: BitDepth,
    val channels: Channels,
    val encoding: Encoding = Encoding.Unknown,
    val endianness: Endianness = Endianness.Little,
) {

    companion object {
        val DEFAULT = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono)
    }
}

/**
 * Represents the valid bit depths for audio data.
 * Each object holds its integer value.
 */
sealed class BitDepth(val value: Int) {

    data object Eight : BitDepth(8)
    data object Sixteen : BitDepth(16)
    data object ThirtyTwo : BitDepth(32)
    data object SixtyFour : BitDepth(64)

    companion object {
        fun fromInt(value: Int): BitDepth {
            return when (value) {
                8 -> Eight
                16 -> Sixteen
                32 -> ThirtyTwo
                64 -> SixtyFour
                else -> throw IllegalArgumentException("Invalid bit depth: $value")
            }
        }
    }
}

/**
 * Represents the valid channel configurations for audio data.
 * Each object holds its integer count.
 */
sealed class Channels(val count: Int) {

    data object Mono : Channels(1)
    data object Stereo : Channels(2)

    companion object {
        fun fromInt(count: Int): Channels {
            return when (count) {
                1 -> Mono
                2 -> Stereo
                else -> throw IllegalArgumentException("Invalid channel count: $count")
            }
        }
    }
}

sealed class Encoding {
    sealed class Pcm(val signed: Boolean) : Encoding() {
        data object Signed : Pcm(true)
        data object Unsigned : Pcm(false)
    }
    data object Unknown : Encoding()
}

sealed class Endianness {
    data object Little : Endianness()
    data object Big : Endianness()
}