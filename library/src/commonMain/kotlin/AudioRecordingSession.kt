import kotlinx.coroutines.flow.StateFlow

/**
 * Represents an active recording session.
 */
interface AudioRecordingSession {

    /** A flow that emits the current state of the recording. */
    val state: StateFlow<AudioRecordingState>
    
    /** A flow that emits audio data chunks as ByteArrays while recording. */
    val audioFlow: StateFlow<AudioFlow?>

    /** Starts the recording. Sets the state to RECORDING. */
    suspend fun start()

    /** Stops the current recording. Sets the state to STOPPED. */
    fun stop()
}