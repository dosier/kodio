package space.kodio.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.kodio.core.security.AudioPermissionManager
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioSystem as JavaSoundAudioSystem

/**
 * JVM implementation for [AudioSystem].
 *
 * On macOS, this will attempt to use the native CoreAudio implementation via Panama FFI
 * for better audio quality and device support. If the native library is not available,
 * it falls back to the pure JVM implementation using javax.sound.sampled.
 *
 * On other platforms (Windows, Linux), uses javax.sound.sampled directly.
 */
actual val SystemAudioSystem: AudioSystem = run {
    val osName = System.getProperty("os.name").lowercase()
    val isMacOS = "mac" in osName

    if (isMacOS && NativeMacosAudioSystem.isAvailable) {
        println("Using native macOS audio system (CoreAudio via FFI)")
        NativeMacosAudioSystem
    } else {
        if (isMacOS) {
            println("Native macOS audio library not available, falling back to JavaSound")
        }
        JvmAudioSystem
    }
}

/**
 * Pure JVM implementation for [AudioSystem] using javax.sound.sampled (JavaSound API).
 *
 * This is used as a fallback when native implementations are not available,
 * or on platforms that don't have native audio support (e.g., Windows, Linux).
 *
 * @see JvmAudioPlaybackSession for playback session implementation.
 * @see JvmAudioRecordingSession for recording session implementation.
 */
internal object JvmAudioSystem : SystemAudioSystemImpl() {

    override val permissionManager: AudioPermissionManager
        get() = JvmAudioPermissionManager

    /**
     * Lists available audio input devices (microphones).
     * It iterates through all system mixers and checks for TargetDataLine support.
     */
    override suspend fun listInputDevices(): List<AudioDevice.Input> = withContext(Dispatchers.IO) {
        JavaSoundAudioSystem.getMixerInfo()
            .map { JavaSoundAudioSystem.getMixer(it) }
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
        JavaSoundAudioSystem.getMixerInfo()
            .map { JavaSoundAudioSystem.getMixer(it) }
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

    override suspend fun createRecordingSession(requestedDevice: AudioDevice.Input?): AudioRecordingSession =
        JvmAudioRecordingSession(requestedDevice ?: listInputDevices().first())

    override suspend fun createPlaybackSession(requestedDevice: AudioDevice.Output?): AudioPlaybackSession =
        JvmAudioPlaybackSession(requestedDevice ?: listOutputDevices().first())
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
