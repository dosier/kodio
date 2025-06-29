import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioSystem as JvmAudioSystem

/**
 * JVM implementation for AudioSystem using javax.sound.sampled.
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
                val supportedFormats = mixer.targetLineInfo.getSupportedFormats()
                AudioDevice.Input(
                    id = mixerInfo.name, // Using mixer name as a unique ID
                    name = mixerInfo.description,
                    supportedFormats = supportedFormats,
                    defaultFormat = supportedFormats.firstOrNull() ?: AudioFormat(44100, 16, 2) // A sensible default
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
                val supportedFormats = mixer.sourceLineInfo.getSupportedFormats()
                AudioDevice.Output(
                    id = mixerInfo.name,
                    name = mixerInfo.description,
                    supportedFormats = supportedFormats,
                    defaultFormat = supportedFormats.firstOrNull() ?: AudioFormat(44100, 16, 2)
                )
            }
    }

    override fun createRecordingSession(device: AudioDevice.Input): RecordingSession {
        return JvmRecordingSession(device)
    }

    override fun createPlaybackSession(device: AudioDevice.Output): PlaybackSession {
        return JvmPlaybackSession(device)
    }
}

/**
 * Helper function to determine supported formats for a given line type on a mixer.
 * This implementation directly queries the DataLine.Info for its supported formats.
 */
private fun Array<Line.Info>.getSupportedFormats() = filterIsInstance<DataLine.Info>()
    .flatMap {
        it.formats.map { jvmFormat ->
            AudioFormat(
                sampleRate = jvmFormat.sampleRate.toInt(),
                bitDepth = jvmFormat.sampleSizeInBits,
                channels = jvmFormat.channels
            )
        }
    }
    // Filter out formats with unspecified values (which can be returned as -1)
    .filter { it.bitDepth > 0 && it.channels > 0 && it.sampleRate > 0 }
    .distinct() // Remove any duplicate formats