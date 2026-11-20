package space.kodio.core

/**
 * How much signal processing the platform applies to captured microphone input.
 */
enum class InputProcessing {
    /**
     * Whatever the platform does by default.
     *
     * On web this is the browser's voice processing (echo cancellation, noise
     * suppression and automatic gain control): louder, more even speech, at the
     * cost of some fidelity. Native platforms capture the unprocessed signal.
     */
    PlatformDefault,

    /**
     * Ask for the unprocessed microphone signal.
     *
     * On web this turns off echo cancellation, noise suppression and automatic
     * gain control. Recordings keep their natural dynamics but are noticeably
     * quieter (often 10 dB or more), and speaker playback can bleed into the mic.
     * Native platforms already capture raw input, so this changes nothing there.
     */
    Raw,
}
