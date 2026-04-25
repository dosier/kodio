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

    /**
     * Pause latch. While `true`, [awaitNotPaused] suspends; flipped back to
     * `false` by [resume]. Reset on [stop] / [reset].
     */
    private val _pausedFlow = MutableStateFlow(false)

    /**
     * Suspends as long as the session is paused; returns immediately
     * otherwise. Platform [startRecording] loops should call this once per
     * iteration when the underlying read API does not naturally block on a
     * paused device (e.g. Android `AudioRecord.read` returns `0` instead of
     * blocking after `stop()`).
     *
     * `MutableStateFlow.first { !it }` is the idiomatic Kotlin "wait until
     * condition" primitive: returns synchronously when the predicate already
     * holds, suspends with zero polling otherwise.
     */
    protected suspend fun awaitNotPaused() {
        _pausedFlow.first { !it }
    }

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
            launchRecordingJob(hotSource)
        } catch (e: Exception) {
            logger.error(e) { "Error in start(): ${e.message}" }
            e.printStackTrace()
            _state.value = State.Error(e)
            cleanupRecording()
        }
    }

    /**
     * Pauses recording while preserving the captured buffer, the live
     * recording job, and the publicly exposed [AudioFlow]. Calling [resume]
     * appends further chunks to the same hot flow, so consumers see one
     * continuous stream.
     *
     * The implementation flips an internal pause latch and delegates to the
     * platform [pauseRecording] hook, which must halt the device's data flow
     * **without releasing it** (e.g. `AudioRecord.stop()`,
     * `TargetDataLine.stop()`, `AVAudioEngine.pause()`, `AudioQueuePause`,
     * `AudioWorklet.disconnect()`). The producer coroutine stays alive, so
     * the pause/resume primitives operate on the same live device.
     */
    override suspend fun pause() {
        logger.debug { "pause() called, current state=${state.value}" }
        if (state.value !is State.Recording) {
            logger.debug { "Not recording, ignoring pause()" }
            return
        }

        try {
            // Signal first so any awaitNotPaused() callers suspend before the
            // next read on the platform side.
            _pausedFlow.value = true
            pauseRecording()
            _state.value = State.Paused
            logger.debug { "state=Paused" }
        } catch (e: Exception) {
            logger.error(e) { "Error in pause(): ${e.message}" }
            _pausedFlow.value = false
            _state.value = State.Error(e)
        }
    }

    /**
     * Resumes a recording previously [paused][pause]. The buffered chunks from
     * before the pause remain in the audio flow; new chunks are appended to
     * the same hot flow.
     */
    override suspend fun resume() {
        logger.debug { "resume() called, current state=${state.value}" }
        if (state.value !is State.Paused) {
            logger.debug { "Not paused, ignoring resume()" }
            return
        }
        try {
            // Wake the device first, then release loops awaiting awaitNotPaused()
            // so the very first post-resume iteration sees a live device.
            resumeRecording()
            _state.value = State.Recording
            _pausedFlow.value = false
            logger.debug { "state=Recording (resumed)" }
        } catch (e: Exception) {
            logger.error(e) { "Error in resume(): ${e.message}" }
            _state.value = State.Error(e)
            _pausedFlow.value = false
            cleanupRecording()
        }
    }

    private fun launchRecordingJob(hotSource: MutableSharedFlow<ByteArray>) {
        recordingJob = scope.launch {
            logger.debug { "Recording job started" }
            var emitCount = 0
            val platformFlow = callbackFlow {
                startRecording(this)
                awaitClose(::cleanup)
            }

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
    }

    override fun stop() {
        logger.debug { "stop() called, current state=${state.value}" }
        val current = state.value
        if (current !is State.Recording && current !is State.Paused) {
            logger.debug { "Not recording or paused (state=$current), returning" }
            return
        }

        try {
            // Release any pause latch BEFORE cancelling the job, so platform
            // loops that suspended in awaitNotPaused() can wake, observe job
            // cancellation, and exit cleanly.
            _pausedFlow.value = false

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
        _pausedFlow.value = false
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

    /**
     * Hook for native pause support.
     *
     * Implementations must halt the device's data flow **without releasing
     * the device** so that [resumeRecording] can restart capture on the same
     * live handle. Examples: `AudioRecord.stop()` (Android),
     * `TargetDataLine.stop()` (JVM), `AVAudioEngine.pause()` (iOS),
     * `AudioQueuePause` (macOS K/N), `AudioWorklet.disconnect()` (Web).
     *
     * Tearing down the device here (via [cleanup] or similar) breaks
     * [resume], because the recording job stays alive across the pause and
     * will issue reads against a disposed device. If a platform genuinely
     * cannot pause, leave this default in place — [pause] will surface the
     * exception as `State.Error` rather than silently corrupting the device.
     */
    protected open suspend fun pauseRecording() {
        throw UnsupportedOperationException(
            "Override pauseRecording() to support pause on this AudioRecordingSession " +
                "(must halt the device WITHOUT releasing it; pair with resumeRecording())."
        )
    }

    /**
     * Hook paired with [pauseRecording]. Implementations must restart data
     * flow on the same device handle that [pauseRecording] halted, so the
     * already-running recording job sees fresh audio on its next read.
     */
    protected open suspend fun resumeRecording() {
        throw UnsupportedOperationException(
            "Override resumeRecording() to support resume on this AudioRecordingSession."
        )
    }
}