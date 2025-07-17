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
     * The session does not start recording until start() is called.
     *
     * @param device The input device to record from.
     * @return A RecordingSession object.
     */
    @Throws(AudioPermissionDeniedException::class, CancellationException::class)
    suspend fun createRecordingSession(device: AudioDevice.Input): AudioRecordingSession

    /**
     * Creates a playback session with the specified output device.
     *
     * @param device The output device to play to.
     * @return A PlaybackSession object.
     */
    suspend fun createPlaybackSession(device: AudioDevice.Output): AudioPlaybackSession
}

abstract class SystemAudioSystemImpl : AudioSystem