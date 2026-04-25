package space.kodio.compose

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.oshai.kotlinlogging.KotlinLogging
import space.kodio.core.*
import space.kodio.core.security.AudioPermissionManager
import kotlin.math.sqrt

private val logger = KotlinLogging.logger("RecorderState")

/**
 * State holder for audio recording in Compose.
 * 
 * Provides a simplified, reactive API for recording audio that integrates
 * seamlessly with Compose's state management.
 * 
 * ## Example Usage
 * ```kotlin
 * @Composable
 * fun VoiceRecorder() {
 *     val recorderState = rememberRecorderState()
 *     
 *     Column {
 *         // Show recording status
 *         Text(
 *             when {
 *                 recorderState.isRecording -> "Recording..."
 *                 recorderState.isProcessing -> "Processing..."
 *                 else -> "Ready"
 *             }
 *         )
 *         
 *         // Record button
 *         Button(
 *             onClick = { recorderState.toggle() },
 *             enabled = !recorderState.isProcessing
 *         ) {
 *             Text(if (recorderState.isRecording) "Stop" else "Record")
 *         }
 *         
 *         // Show waveform while recording
 *         if (recorderState.isRecording) {
 *             AudioWaveform(amplitudes = recorderState.liveAmplitudes)
 *         }
 *         
 *         // Access the recording when done
 *         recorderState.recording?.let { recording ->
 *             Button(onClick = { recording.play() }) {
 *                 Text("Play Recording")
 *             }
 *         }
 *     }
 * }
 * ```
 */
@Stable
class RecorderState internal constructor(
    private val scope: CoroutineScope,
    private val quality: AudioQuality,
    private val device: AudioDevice.Input?,
    private val liveWaveformGain: Float,
    private val getOnRecordingComplete: () -> ((AudioRecording) -> Unit)?
) {
    private var _recorder: Recorder? = null
    private var _recording = mutableStateOf<AudioRecording?>(null)
    private var _isRecording = mutableStateOf(false)
    private var _isPaused = mutableStateOf(false)
    private var _isProcessing = mutableStateOf(false)
    private var _error = mutableStateOf<AudioError?>(null)
    private var _liveAmplitudes = mutableStateOf<List<Float>>(emptyList())
    private var _permissionState = mutableStateOf(AudioPermissionManager.State.Unknown)
    private var _isInitialized = mutableStateOf(false)
    
    // Job for amplitude collection - tracked for proper cancellation
    private var amplitudeCollectionJob: Job? = null

    // Job for permission state collection - tracked for proper cancellation
    private var permissionCollectionJob: Job? = null
    
    // Mutex for thread-safe state transitions
    private val stateMutex = Mutex()

    /**
     * Whether the recorder is currently recording.
     */
    val isRecording: Boolean by _isRecording

    /**
     * Whether the recorder is processing (e.g., collecting recorded data after stop).
     */
    val isProcessing: Boolean by _isProcessing

    /**
     * Whether the recorder is paused (recording can be resumed via [resume]).
     *
     * Backed by a Compose-observable [mutableStateOf] so the UI recomposes when
     * pause / resume flips it.
     */
    val isPaused: Boolean by _isPaused

    /**
     * Whether the recorder is busy (recording, paused, or processing).
     */
    val isBusy: Boolean
        get() = _isRecording.value || _isProcessing.value || _isPaused.value

    /**
     * The completed recording, if available.
     * This becomes non-null after [stop] is called and processing completes.
     */
    val recording: AudioRecording? by _recording

    /**
     * Whether a recording is available.
     */
    val hasRecording: Boolean
        get() = _recording.value != null

    /**
     * Live amplitude values for displaying a waveform.
     * Updates in real-time while recording.
     */
    val liveAmplitudes: List<Float> by _liveAmplitudes

    /**
     * The most recent error, if any.
     */
    val error: AudioError? by _error

    /**
     * Whether there is an error.
     */
    val hasError: Boolean
        get() = _error.value != null

    /**
     * The current microphone permission state.
     */
    val permissionState: AudioPermissionManager.State by _permissionState

    /**
     * Whether the recorder has been initialized successfully.
     */
    val isReady: Boolean
        get() = _isInitialized.value && _permissionState.value == AudioPermissionManager.State.Granted

    /**
     * Whether permission is required before recording.
     */
    val needsPermission: Boolean
        get() = _permissionState.value != AudioPermissionManager.State.Granted

    /**
     * Starts recording audio.
     */
    fun start() {
        scope.launch {
            startAsync()
        }
    }

    /**
     * Starts recording audio (suspend version).
     */
    suspend fun startAsync() {
        logger.debug { "startAsync() called, isRecording=${_isRecording.value}, isProcessing=${_isProcessing.value}" }
        stateMutex.withLock {
            if (_isRecording.value || _isProcessing.value) {
                logger.debug { "Already recording or processing, returning" }
                return
            }
            
            _error.value = null
            _liveAmplitudes.value = emptyList()
            _recording.value = null
            
            // Cancel any existing amplitude collection
            amplitudeCollectionJob?.cancel()
            amplitudeCollectionJob = null
            
            try {
                // Reset existing recorder or create new one
                val existingRecorder = _recorder
                val recorder = if (existingRecorder != null) {
                    logger.debug { "Resetting existing recorder" }
                    // Reset before reusing to clear previous state
                    existingRecorder.reset()
                    existingRecorder
                } else {
                    logger.debug { "Creating new recorder" }
                    createRecorder().also { _recorder = it }
                }
                logger.debug { "Calling recorder.start()" }
                recorder.start()
                _isRecording.value = true
                logger.debug { "Recording started successfully" }
                
                // Start collecting amplitudes
                startAmplitudeCollection(recorder)
            } catch (e: Exception) {
                logger.error(e) { "Error starting recording: ${e.message}" }
                _error.value = AudioError.from(e)
                _isRecording.value = false
            }
        }
    }

    /**
     * Stops recording and makes the recording available.
     * The recording will be available in [recording] once [isProcessing] becomes false.
     */
    fun stop() {
        scope.launch {
            stopAsync()
        }
    }

    /**
     * Stops recording and waits for the recording to be ready (suspend version).
     * @return The recorded audio, or null if no recording was made
     */
    suspend fun stopAsync(): AudioRecording? {
        logger.debug { "stopAsync() called, isRecording=${_isRecording.value}, isPaused=${_isPaused.value}" }
        stateMutex.withLock {
            if (!_isRecording.value && !_isPaused.value) {
                logger.debug { "Not recording or paused, returning existing recording" }
                return _recording.value
            }

            // Cancel amplitude collection first
            amplitudeCollectionJob?.cancel()
            amplitudeCollectionJob = null

            val recorder = _recorder ?: return null

            logger.debug { "Calling recorder.stop()" }
            recorder.stop()
            _isRecording.value = false
            _isPaused.value = false
            _isProcessing.value = true
            logger.debug { "Recording stopped, processing..." }
        }
        
        // Process recording outside the lock to avoid blocking other operations
        return try {
            val recorder = _recorder ?: return null
            logger.debug { "Getting recording..." }
            val rec = recorder.getRecording()
            logger.debug { "Got recording: ${if (rec != null) "available" else "null"}" }
            
            stateMutex.withLock {
                _recording.value = rec
                _isProcessing.value = false
            }
            
            // Notify callback with the latest callback reference
            if (rec != null) {
                getOnRecordingComplete()?.invoke(rec)
            }
            
            logger.debug { "stopAsync() completed successfully" }
            rec
        } catch (e: Exception) {
            logger.error(e) { "Error processing recording: ${e.message}" }
            stateMutex.withLock {
                _error.value = AudioError.from(e)
                _isProcessing.value = false
            }
            null
        }
    }

    /**
     * Toggles recording on/off.
     */
    fun toggle() {
        scope.launch {
            toggleAsync()
        }
    }

    /**
     * Toggles recording on/off (suspend version).
     * @return true if now recording, false if stopped
     */
    suspend fun toggleAsync(): Boolean {
        logger.debug { "toggleAsync() called, isRecording=${_isRecording.value}" }
        return if (_isRecording.value) {
            stopAsync()
            logger.debug { "toggleAsync() -> stopped" }
            false
        } else {
            startAsync()
            logger.debug { "toggleAsync() -> started" }
            true
        }
    }

    /**
     * Pauses recording. The captured audio so far is preserved; call [resume]
     * to continue appending to the same recording, or [stop] to finalize it.
     */
    fun pause() {
        scope.launch { pauseAsync() }
    }

    /** Suspend variant of [pause]. */
    suspend fun pauseAsync() {
        stateMutex.withLock {
            val recorder = _recorder ?: return
            if (!_isRecording.value) return
            recorder.pause()
            _isRecording.value = false
            _isPaused.value = true
        }
    }

    /**
     * Resumes a [paused][pause] recording.
     */
    fun resume() {
        scope.launch { resumeAsync() }
    }

    /** Suspend variant of [resume]. */
    suspend fun resumeAsync() {
        stateMutex.withLock {
            val recorder = _recorder ?: return
            if (!_isPaused.value) return
            recorder.resume()
            _isRecording.value = true
            _isPaused.value = false
        }
    }

    /**
     * Resets the state, discarding any recording.
     */
    fun reset() {
        scope.launch {
            resetAsync()
        }
    }

    /**
     * Resets the state, discarding any recording (suspend version).
     */
    suspend fun resetAsync() {
        stateMutex.withLock {
            amplitudeCollectionJob?.cancel()
            amplitudeCollectionJob = null
            _recorder?.reset()
            _recording.value = null
            _isRecording.value = false
            _isPaused.value = false
            _isProcessing.value = false
            _liveAmplitudes.value = emptyList()
            _error.value = null
        }
    }

    /**
     * Requests microphone permission.
     */
    fun requestPermission() {
        scope.launch {
            requestPermissionAsync()
        }
    }

    /**
     * Requests microphone permission (suspend version).
     */
    suspend fun requestPermissionAsync() {
        try {
            Kodio.microphonePermission.request()
        } catch (e: Exception) {
            _error.value = AudioError.from(e)
        }
    }

    /**
     * Clears the current error.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Cleans up resources. Called automatically when the composable leaves composition.
     */
    internal fun release() {
        permissionCollectionJob?.cancel()
        permissionCollectionJob = null
        amplitudeCollectionJob?.cancel()
        amplitudeCollectionJob = null
        _recorder?.release()
        _recorder = null
    }

    internal suspend fun initialize() {
        try {
            _permissionState.value = Kodio.microphonePermission.refresh()
            _isInitialized.value = true
        } catch (e: Exception) {
            _error.value = AudioError.from(e)
        }
        // Always observe future state changes, even if the initial refresh failed
        // (e.g. on Android before Kodio.initialize(activity) has been called).
        permissionCollectionJob?.cancel()
        permissionCollectionJob = scope.launch {
            Kodio.microphonePermission.state.collect { newState ->
                _permissionState.value = newState
            }
        }
    }

    private suspend fun createRecorder(): Recorder {
        return Kodio.recorder(quality = quality, device = device)
    }

    private fun startAmplitudeCollection(recorder: Recorder) {
        amplitudeCollectionJob = scope.launch {
            try {
                // Get the actual audio flow - it contains the real format from the audio system
                val audioFlow = recorder.audioFlow
                if (audioFlow == null) {
                    logger.warn { "No audio flow available for amplitude collection" }
                    return@launch
                }
                
                // Use the actual format from the audio flow, not the requested quality format
                val format = audioFlow.format
                logger.debug { "Starting amplitude collection with format: sampleRate=${format.sampleRate}, channels=${format.channels}, encoding=${format.encoding}, bytesPerSample=${format.bytesPerSample}" }
                logger.debug { "AudioFlow instance: ${audioFlow.hashCode()}" }
                
                audioFlow.collect { chunk ->
                    val rawAmplitude = calculateAmplitude(chunk, format)
                    val amplitude = mapLiveWaveformAmplitude(rawAmplitude)
                    _liveAmplitudes.value = (_liveAmplitudes.value + amplitude).takeLast(100)
                }
            } catch (e: Exception) {
                // Collection cancelled or failed - ignore (expected during stop)
                logger.debug { "Amplitude collection ended: ${e.message}" }
            }
        }
    }

    /**
     * Map a raw RMS amplitude (0..1-ish) to a more visually useful value for live waveform rendering.
     *
     * We apply a sqrt "perceptual" curve to make low signals more visible, then apply a gain,
     * and finally clamp into [0, 1] (the expected range for [AudioWaveform]).
     */
    private fun mapLiveWaveformAmplitude(raw: Float): Float {
        val safe = raw.coerceAtLeast(0f)
        val curved = sqrt(safe.toDouble()).toFloat()
        val gained = curved * liveWaveformGain.coerceAtLeast(0f)
        return gained.coerceIn(0f, 1f)
    }

    private fun calculateAmplitude(chunk: ByteArray, format: AudioFormat): Float {
        if (chunk.isEmpty()) return 0f
        
        val bytesPerSample = format.bytesPerSample
        
        return when (bytesPerSample) {
            2 -> calculateAmplitude16Bit(chunk)
            1 -> calculateAmplitude8Bit(chunk)
            else -> calculateAmplitudeGeneric(chunk)
        }
    }

    /**
     * Calculate RMS amplitude for 16-bit signed PCM (little-endian).
     */
    private fun calculateAmplitude16Bit(chunk: ByteArray): Float {
        var sum = 0.0
        var count = 0
        
        var i = 0
        while (i + 1 < chunk.size) {
            // Correctly read little-endian 16-bit signed sample
            val low = chunk[i].toInt() and 0xFF
            val high = chunk[i + 1].toInt()  // Keep sign for high byte
            val sample = (high shl 8) or low  // Signed 16-bit value
            
            val normalized = sample / 32768.0
            sum += normalized * normalized
            count++
            i += 2
        }
        
        return if (count > 0) kotlin.math.sqrt(sum / count).toFloat() else 0f
    }

    /**
     * Calculate amplitude for 8-bit unsigned PCM.
     */
    private fun calculateAmplitude8Bit(chunk: ByteArray): Float {
        var sum = 0.0
        for (byte in chunk) {
            // 8-bit PCM is typically unsigned, centered at 128
            val sample = (byte.toInt() and 0xFF) - 128
            val normalized = sample / 128.0
            sum += normalized * normalized
        }
        return if (chunk.isNotEmpty()) kotlin.math.sqrt(sum / chunk.size).toFloat() else 0f
    }

    /**
     * Generic amplitude calculation (fallback).
     */
    private fun calculateAmplitudeGeneric(chunk: ByteArray): Float {
        if (chunk.isEmpty()) return 0f
        return chunk.map { (it.toInt() and 0xFF) / 255f }.average().toFloat()
    }
}

/**
 * Creates and remembers a [RecorderState] for audio recording.
 * 
 * @param quality The audio quality preset
 * @param device Optional specific input device
 * @param onRecordingComplete Callback when a recording is completed
 * @return A remembered RecorderState
 */
@Composable
fun rememberRecorderState(
    quality: AudioQuality = AudioQuality.Default,
    device: AudioDevice.Input? = null,
    liveWaveformGain: Float = 4f,
    onRecordingComplete: ((AudioRecording) -> Unit)? = null
): RecorderState {
    val scope = rememberCoroutineScope()
    
    // Use rememberUpdatedState to always have the latest callback
    val currentCallback by rememberUpdatedState(onRecordingComplete)
    
    val state = remember(quality, device) {
        RecorderState(
            scope = scope,
            quality = quality,
            device = device,
            liveWaveformGain = liveWaveformGain,
            getOnRecordingComplete = { currentCallback }
        )
    }
    
    // Initialize on first composition
    LaunchedEffect(state) {
        state.initialize()
    }
    
    // Cleanup on disposal
    DisposableEffect(state) {
        onDispose {
            state.release()
        }
    }
    
    return state
}
