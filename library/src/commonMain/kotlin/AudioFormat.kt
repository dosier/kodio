/**
 * Defines the format of the audio data.
 */
data class AudioFormat(
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int
) {

    companion object {
        val DEFAULT = AudioFormat(44100, 16, 2)
    }
}