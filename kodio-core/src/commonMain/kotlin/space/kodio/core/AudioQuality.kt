package space.kodio.core

/**
 * Simplified audio quality presets for common use cases.
 * 
 * These presets provide sensible defaults so users don't need to understand
 * the details of sample rates, bit depths, and channel configurations.
 */
enum class AudioQuality(
    /** The underlying audio format configuration */
    val format: AudioFormat
) {
    /**
     * Optimized for voice/speech recording.
     * Mono, 16kHz, 16-bit PCM - efficient for speech recognition and voice messages.
     */
    Voice(
        AudioFormat(
            sampleRate = 16000,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
        )
    ),

    /**
     * Standard quality audio.
     * Mono, 44.1kHz, 16-bit PCM - CD quality mono, good balance of quality and size.
     */
    Standard(
        AudioFormat(
            sampleRate = 44100,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
        )
    ),

    /**
     * High quality stereo audio.
     * Stereo, 48kHz, 16-bit PCM - professional quality for music and high-fidelity recording.
     */
    High(
        AudioFormat(
            sampleRate = 48000,
            channels = Channels.Stereo,
            encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
        )
    ),

    /**
     * Lossless studio quality.
     * Stereo, 96kHz, 24-bit PCM - maximum quality for professional audio production.
     */
    Lossless(
        AudioFormat(
            sampleRate = 96000,
            channels = Channels.Stereo,
            encoding = SampleEncoding.PcmInt(IntBitDepth.TwentyFour)
        )
    );

    companion object {
        /** Default quality preset for general recording */
        val Default = Standard
    }
}


