import kotlinx.coroutines.flow.Flow

class AudioDataFlow(
    val format: AudioFormat,
    private val dataFlow: Flow<ByteArray>
) : Flow<ByteArray> by dataFlow