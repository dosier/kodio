package space.kodio.core

import kotlinx.coroutines.CancellationException
import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager

public expect val SystemAudioSystem: AudioSystem

/**
 * Manages audio devices and sessions.
 */
public sealed interface AudioSystem {

    val permissionManager: AudioPermissionManager get() = TODO()

    /** Lists all available audio input devices. */
    suspend fun listInputDevices(): List<AudioDevice.Input>

    /** Lists all available audio output devices. */
    suspend fun listOutputDevices(): List<AudioDevice.Output>

    /**
     * Creates a recording session with the specified input device.
     * The session does not start recording until [AudioRecordingSession.start] is called.
     *
     * @param requestedDevice The requested device to record from (not all platforms support this).
     * @return A RecordingSession object.
     */
    @Throws(AudioPermissionDeniedException::class, CancellationException::class)
    suspend fun createRecordingSession(requestedDevice: AudioDevice.Input? = null): AudioRecordingSession

    /**
     * Creates a playback session with the specified output device.
     *
     * @param requestedDevice The requested output device to play to (not all platforms support this).
     */
    suspend fun createPlaybackSession(requestedDevice: AudioDevice.Output? = null): AudioPlaybackSession
}

abstract class SystemAudioSystemImpl : AudioSystem