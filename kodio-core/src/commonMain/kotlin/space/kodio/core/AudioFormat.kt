package space.kodio.core

/** Channels: number + layout if you need it later. */
enum class Channels(val count: Int) {
    Mono(1),
    Stereo(2);
    companion object {
        fun fromInt(count: Int) = when (count) {
            1 -> Mono
            2 -> Stereo
            else -> throw IllegalArgumentException("Only 1 or 2 channels are supported")
        }
    }
}

enum class Endianness { Little, Big }
enum class IntBitDepth(val bits: Int) { Eight(8), Sixteen(16), TwentyFour(24), ThirtyTwo(32) }
enum class FloatPrecision { F32, F64 }

/** How samples are laid out in memory. */
enum class SampleLayout { Interleaved, Planar }

/** Audio sample encoding. No “signed/unsigned” for floats anymore. */
sealed interface SampleEncoding {
    data class PcmInt(
        val bitDepth: IntBitDepth,
        val endianness: Endianness = Endianness.Little,
        val layout: SampleLayout = SampleLayout.Interleaved,
        val signed: Boolean = true,           // true for standard PCM; keep in case you ever need u8
        val packed: Boolean = true            // matches kAudioFormatFlagIsPacked (no padding)
    ) : SampleEncoding

    data class PcmFloat(
        val precision: FloatPrecision = FloatPrecision.F32,
        val layout: SampleLayout = SampleLayout.Interleaved
    ) : SampleEncoding

    // Room for compressed / encoded streams, if you ever add them:
    // data class Compressed(val codec: Codec, val profile: String?, val bitrate: Int?) : SampleEncoding
}

/** Canonical audio format */
data class AudioFormat(
    val sampleRate: Int,
    val channels: Channels,
    val encoding: SampleEncoding
) {
    /** Bytes per (single-channel) sample. */
    val bytesPerSample: Int = when (val e = encoding) {
        is SampleEncoding.PcmInt   -> e.bitDepth.bits / 8
        is SampleEncoding.PcmFloat -> when (e.precision) {
            FloatPrecision.F32 -> 4
            FloatPrecision.F64 -> 8
        }
    }

    /** Bytes per frame = per-sample * channels (for interleaved); planar uses per-plane frame size. */
    val bytesPerFrame: Int = when (val e = encoding) {
        is SampleEncoding.PcmInt   ->
            if (e.layout == SampleLayout.Interleaved) bytesPerSample * channels.count else bytesPerSample
        is SampleEncoding.PcmFloat ->
            if (e.layout == SampleLayout.Interleaved) bytesPerSample * channels.count else bytesPerSample
    }
}

/** Sensible defaults you can swap depending on your pipeline */
val DefaultRecordingInt16 = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen, Endianness.Little, SampleLayout.Interleaved, signed = true)
)

val DefaultRecordingFloat32 = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
)