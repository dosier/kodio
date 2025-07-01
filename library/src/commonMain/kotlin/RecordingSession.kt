import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents an active recording session.
 */
interface RecordingSession {

    /** A flow that emits the current state of the recording. */
    val state: StateFlow<RecordingState>
    
    /** A flow that emits audio data chunks as ByteArrays while recording. */
    val audioDataFlow: Flow<ByteArray>

    /** The current format of the recorded audio. */
    val actualFormat: StateFlow<AudioFormat?>

    /** Starts the recording. Sets the state to RECORDING. */
    suspend fun start(format: AudioFormat)
    
    /** Stops the current recording. Sets the state to STOPPED. */
    fun stop()
}