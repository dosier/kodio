import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.currentRoute

/**
 * IOS implementation for [AudioSystem].
 *
 * @see IosPlaybackSession for playback session implementation.
 * @see IosRecordingSession for recording session implementation.
 */
actual val SystemAudioSystem: AudioSystem = object  : SystemAudioSystemImpl() {

    private val audioSession = AVAudioSession.sharedInstance()

    override suspend fun listInputDevices(): List<AudioDevice.Input> {
        if (!requestMicrophonePermission())
            return emptyList()
        return audioSession.availableInputs
            ?.filterIsInstance<AVAudioSessionPortDescription>()
            ?.map { port ->
                AudioDevice.Input(
                    id = port.UID(),
                    name = port.portName(),
                    // Querying all formats is complex on iOS; provide common ones.
                    supportedFormats = listOf(DefaultIosRecordingAudioFormat),
                    defaultFormat = DefaultIosRecordingAudioFormat
                )
            }
            ?: emptyList()
    }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> {
        return audioSession.currentRoute.outputs
            .filterIsInstance<AVAudioSessionPortDescription>()
            .map { port ->
                AudioDevice.Output(
                    id = port.UID(),
                    name = port.portName(),
                    supportedFormats = listOf(DefaultIosPlaybackAudioFormat),
                    defaultFormat = DefaultIosPlaybackAudioFormat
                )
            }
    }

    override fun createRecordingSession(device: AudioDevice.Input): RecordingSession =
        IosRecordingSession(device)

    /**
     * In IOS, there is no control over the output device, so we ignore it.
     */
    override fun createPlaybackSession(device: AudioDevice.Output): PlaybackSession =
        IosPlaybackSession()
}