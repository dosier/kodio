import kotlinx.coroutines.flow.Flow

fun Flow<ByteArray>.asAudioDataFlow(format: AudioFormat): AudioDataFlow =
    AudioDataFlow(format, this)