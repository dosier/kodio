package space.kodio.core

import space.kodio.core.AudioRecordingSession.State
import space.kodio.core.util.namedLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

private val logger = namedLogger("BaseRecording")

/**
 * An abstract base class for [AudioRecordingSession] that handles the logic for
 * providing a live "hot" flow during recording, and a replayable "cold" flow
 * after the recording is finished.
 *
 * This implementation solves the problem of the playback session never finishing
 * by swapping the underlying flow implementation from a hot [SharedFlow] during
 * recording to a cold, completable flow (backed by a [List]) once the
 * recording is stopped.
 */
abstract class BaseAudioRecordingSession : AudioRecordingSession {

    private val _state = MutableStateFlow<State>(State.Idle)
    override val state: StateFlow<State> = _state.asStateFlow()

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow.asStateFlow()

    private var recordingJob: Job? = null
    protected val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    private var hotFlowSource: MutableSharedFlow<ByteArray>? = null
    private var recordingAudioFormat: AudioFormat? = null

    override suspend fun start() {
        logger.debug { "start() called, current state=${state.value}" }
        if (state.value is State.Recording) {
            logger.debug { "Already recording, returning" }
            return
        }

        try {
            _audioFlow.value = null // Clear previous flow

            val format = prepareRecording()
            recordingAudioFormat = format
            logger.debug { "Prepared recording with format: $format" }

            // 1. Create a hot flow for live data, caching all values for later.
            val hotSource = MutableSharedFlow<ByteArray>(replay = Int.MAX_VALUE)
            this.hotFlowSource = hotSource
            logger.debug { "Created hotSource SharedFlow: ${hotSource.hashCode()}" }

            // 2. The publicly exposed flow is now the hot one.
            val flow = AudioFlow(format, hotSource.asSharedFlow())
            _audioFlow.value = flow
            _state.value = State.Recording
            logger.debug { "Set audioFlow (hash=${flow.hashCode()}) and state=Recording" }

            // 3. Launch a job to collect audio from the platform-specific implementation.
            recordingJob = scope.launch {
                logger.debug { "Recording job started" }
                var emitCount = 0
                // callbackFlow wraps the platform-specific recording logic
                // and provides a SendChannel to push audio chunks into the flow.
                val platformFlow = callbackFlow {
                    startRecording(this) // Call the abstract method
                    awaitClose(::cleanup)
                }

                // Collect from the platform and emit to our hot flow
                platformFlow.collect { chunk ->
                    emitCount++
                    if (emitCount <= 3 || emitCount % 50 == 0) {
                        val nonZero = chunk.count { it != 0.toByte() }
                        logger.debug { "Emitting chunk #$emitCount to hotSource: size=${chunk.size}, nonZeroBytes=$nonZero" }
                    }
                    hotSource.emit(chunk)
                }
                logger.debug { "Recording job finished, emitted $emitCount chunks" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in start(): ${e.message}" }
            e.printStackTrace()
            _state.value = State.Error(e)
            cleanupRecording()
        }
    }

    override fun stop() {
        logger.debug { "stop() called, current state=${state.value}" }
        if (state.value !is State.Recording) {
            logger.debug { "Not recording, returning" }
            return
        }

        try {
            // 1. Stop the recording job. This triggers onCleanup() via awaitClose.
            cleanupRecording()

            // 2. Get the recorded data from the hot flow's replay cache.
            val recordedChunks = hotFlowSource?.replayCache
            val format = recordingAudioFormat
            logger.debug { "Recorded ${recordedChunks?.size ?: 0} chunks" }

            if (recordedChunks != null && format != null) {
                val totalNonZero = recordedChunks.sumOf { chunk -> chunk.count { it != 0.toByte() } }
                logger.debug { "Total non-zero bytes in recording: $totalNonZero" }
                
                // 3. Create a new COLD, replayable flow from the captured data.
                val coldFlow = AudioFlow(format, recordedChunks.asFlow())

                // 4. Update the public audioFlow to the new cold flow.
                _audioFlow.value = coldFlow
                _state.value = State.Stopped
                logger.debug { "Created cold flow, state=Stopped" }
            } else {
                _state.value = State.Stopped // Or an error state if appropriate
                logger.warn { "No recorded data available!" }
            }
        } finally {
            // 5. Clean up internal references.
            hotFlowSource = null
            recordingAudioFormat = null
        }
    }

    override fun reset() {
        cleanupRecording()
        _audioFlow.value = null
        _state.value = State.Idle
        hotFlowSource = null
        recordingAudioFormat = null
    }

    private fun cleanupRecording() {
        recordingJob?.cancel()
        recordingJob = null
    }

    /**
     * Prepare the platform-specific resources for recording.
     * @return The [AudioFormat] of the audio that will be recorded.
     */
    protected abstract suspend fun prepareRecording(): AudioFormat

    /**
     * Start the platform-specific recording.
     * Audio chunks should be sent to the provided [SendChannel].
     * The implementation should suspend until the recording is intended to be stopped.
     */
    protected abstract suspend fun startRecording(channel: SendChannel<ByteArray>)

    /**
     * Clean up any platform-specific resources used for recording. This is called
     * when the recording is stopped or an error occurs.
     */
    protected abstract fun cleanup()
}