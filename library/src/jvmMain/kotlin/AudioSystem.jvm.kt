import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioSystem as JvmAudioSystem

/**
 * JVM implementation for [AudioSystem] using javax.sound.sampled.
 *
 * @see JvmPlaybackSession for playback session implementation.
 * @see JvmRecordingSession for recording session implementation.
 */
actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {

    /**
     * Lists available audio input devices (microphones).
     * It iterates through all system mixers and checks for TargetDataLine support.
     */
    override suspend fun listInputDevices(): List<AudioDevice.Input> = withContext(Dispatchers.IO) {
        JvmAudioSystem.getMixerInfo()
            .map { JvmAudioSystem.getMixer(it) }
            .filter { it.isLineSupported(DataLine.Info(TargetDataLine::class.java, null)) }
            .map { mixer ->
                // For simplicity, we treat each mixer as one device.
                // A more complex implementation could inspect individual lines.
                val mixerInfo = mixer.mixerInfo
                val supportedFormats = mixer.targetLineInfo.toCommonAudioFormats()
                AudioDevice.Input(
                    id = mixerInfo.name, // Using mixer name as a unique ID
                    name = mixerInfo.description,
                    formatSupport = toAudioFormatSupport(supportedFormats)
                )
            }
    }

    /**
     * Lists available audio output devices (speakers).
     * It iterates through all system mixers and checks for SourceDataLine support.
     */
    override suspend fun listOutputDevices(): List<AudioDevice.Output> = withContext(Dispatchers.IO) {
        JvmAudioSystem.getMixerInfo()
            .map { JvmAudioSystem.getMixer(it) }
            .filter { it.isLineSupported(DataLine.Info(SourceDataLine::class.java, null)) }
            .map { mixer ->
                val mixerInfo = mixer.mixerInfo
                val supportedFormats = mixer.sourceLineInfo.toCommonAudioFormats()
                AudioDevice.Output(
                    id = mixerInfo.name,
                    name = mixerInfo.description,
                    formatSupport = toAudioFormatSupport(supportedFormats)
                )
            }
    }

    override fun createRecordingSession(device: AudioDevice.Input): RecordingSession =
        JvmRecordingSession(device)

    override fun createPlaybackSession(device: AudioDevice.Output): PlaybackSession =
        JvmPlaybackSession(device)
}

private fun toAudioFormatSupport(supportedFormats: List<AudioFormat>): AudioFormatSupport = when {
    supportedFormats.isNotEmpty() -> {
        AudioFormatSupport.Known(
            supportedFormats = supportedFormats,
            defaultFormat = supportedFormats.first()
        )
    }
    else ->
        AudioFormatSupport.Unknown
}