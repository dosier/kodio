package gg.kodio.core

/**
 * Represents an audio device, sealed for type safety between inputs and outputs.
 */
sealed interface AudioDevice {

    /** A unique identifier for the device. */
    val id: String

    /** A human-readable name for the device (e.g., "Built-in Microphone"). */
    val name: String

    /** The supported audio formats. */
    val formatSupport: AudioFormatSupport

    /**
     * Represents an audio input device (e.g., a microphone).
     */
    class Input(
        override val id: String,
        override val name: String,
        override val formatSupport: AudioFormatSupport
    ) : AudioDevice

    /**
     * Represents an audio output device (e.g., speakers).
     */
    class Output(
        override val id: String,
        override val name: String,
        override val formatSupport: AudioFormatSupport
    ) : AudioDevice
}
