import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.currentRoute

private val IOSAudioFormat = AudioFormat(44100, 32, 1)

actual val SystemAudioSystem: AudioSystem = object  : SystemAudioSystemImpl() {
    private val audioSession = AVAudioSession.sharedInstance()

    override suspend fun listInputDevices(): List<AudioDevice.Input> {
        if (!requestMicrophonePermission()) {
            println("Microphone permission denied.")
            return emptyList()
        }

        return audioSession.availableInputs?.filterIsInstance<AVAudioSessionPortDescription>()?.map { port ->
            AudioDevice.Input(
                id = port.UID(),
                name = port.portName(),
                // Querying all formats is complex on iOS; provide common ones.
                supportedFormats = listOf(IOSAudioFormat),
                defaultFormat = IOSAudioFormat
            )
        } ?: emptyList()
    }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> {
        return audioSession.currentRoute.outputs.filterIsInstance<AVAudioSessionPortDescription>().map { port ->
            AudioDevice.Output(
                id = port.UID(),
                name = port.portName(),
                supportedFormats = listOf(IOSAudioFormat),
                defaultFormat = IOSAudioFormat
            )
        }
    }

    override fun createRecordingSession(device: AudioDevice.Input): RecordingSession {
        return IosRecordingSession(device)
    }

    override fun createPlaybackSession(device: AudioDevice.Output): PlaybackSession {
        return IosPlaybackSession(device)
    }
}