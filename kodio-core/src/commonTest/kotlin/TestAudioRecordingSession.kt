import kotlinx.coroutines.channels.SendChannel

internal class TestAudioRecordingSession(
    private val format: AudioFormat,
    private val audioFrames: Iterable<ByteArray>
) : BaseAudioRecordingSession() {

    override suspend fun prepareRecording(): AudioFormat =
        format

    override suspend fun startRecording(channel: SendChannel<ByteArray>) =
        audioFrames.forEach(channel::trySend)

    override fun cleanup() = Unit
}