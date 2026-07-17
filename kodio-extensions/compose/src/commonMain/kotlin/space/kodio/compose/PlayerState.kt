package space.kodio.compose

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kodio.compose.internal.pcmDuration
import space.kodio.compose.internal.seekByteOffset
import space.kodio.core.*
import space.kodio.core.logging.kodioLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val logger = kodioLogger("PlayerState")

/**
 * State holder for audio playback in Compose.
 *
 * Provides a simplified, reactive API for playing audio that integrates
 * with Compose's state management.
 *
 * ## Example Usage
 * ```kotlin
 * @Composable
 * fun AudioPlayer(recording: AudioRecording) {
 *     val playerState = rememberPlayerState(recording)
 *
 *     Column {
 *         // Show playback status
 *         Text(
 *             when {
 *                 playerState.isLoading -> "Loading..."
 *                 playerState.isPlaying -> "Playing"
 *                 playerState.isPaused -> "Paused"
 *                 playerState.isFinished -> "Finished"
 *                 else -> "Ready"
 *             }
 *         )
 *
 *         // Play/Pause button
 *         Button(
 *             onClick = { playerState.toggle() },
 *             enabled = playerState.isReady && !playerState.isLoading
 *         ) {
 *             Text(if (playerState.isPlaying) "Pause" else "Play")
 *         }
 *
 *         // Stop button
 *         Button(onClick = { playerState.stop() }) {
 *             Text("Stop")
 *         }
 *     }
 * }
 * ```
 */
@Stable
class PlayerState internal constructor(
    private val scope: CoroutineScope,
    private val device: AudioDevice.Output?,
    private val getOnPlaybackComplete: () -> (() -> Unit)?,
    private val playerFactory: (suspend () -> Player)? = null,
) {
    private var _player: Player? = null
    private var _isPlaying = mutableStateOf(false)
    private var _isPaused = mutableStateOf(false)
    private var _isReady = mutableStateOf(false)
    private var _isLoading = mutableStateOf(false)
    private var _isFinished = mutableStateOf(false)
    private var _error = mutableStateOf<AudioError?>(null)
    private var _loadedRecording = mutableStateOf<AudioRecording?>(null)
    private var _progress = mutableFloatStateOf(0f)
    private var stateObserverJob: Job? = null
    private var progressJob: Job? = null
    private var seekJob: Job? = null

    /** Full source recording (not a seek slice). */
    private var sourceRecording: AudioRecording? = null

    /**
     * Remembers that playback was active when a scrub/seek began so rapid
     * intermediate seeks (which stop the player) still resume afterward.
     *
     * Exposed to Compose via [isPlaying] so transport UI keeps showing the
     * pause icon while the session briefly passes through Idle/Ready.
     */
    private val _resumeAfterSeek = mutableStateOf(false)

    // Mutex for thread-safe state transitions
    private val stateMutex = Mutex()

    /**
     * Whether the player is currently playing, or seeking with intent to resume.
     *
     * While scrubbing during playback the underlying session is stopped and
     * reloaded; this stays `true` for that window so UI does not flicker to Play.
     */
    val isPlaying: Boolean
        get() = _isPlaying.value || _resumeAfterSeek.value

    /**
     * Whether the player is paused.
     */
    val isPaused: Boolean by _isPaused

    /**
     * Whether the player is ready (has audio loaded and can play).
     */
    val isReady: Boolean by _isReady

    /**
     * Whether the player is loading audio.
     */
    val isLoading: Boolean by _isLoading

    /**
     * Whether playback has finished.
     */
    val isFinished: Boolean by _isFinished

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
     * The currently loaded recording (full source, not a seek window).
     */
    val recording: AudioRecording? by _loadedRecording

    /**
     * Whether a recording is loaded.
     */
    val hasRecording: Boolean
        get() = _loadedRecording.value != null

    /**
     * Normalized playback position in `0f..1f` across the full [recording].
     */
    val progress: Float by _progress

    /**
     * Duration of the loaded recording from the PCM frame clock, or [Duration.ZERO]
     * if none / unknown. Uses frames (not container metadata) so [progress],
     * [position], and [seekTo] stay aligned.
     */
    val duration: Duration
        get() {
            val source = sourceRecording ?: return Duration.ZERO
            return pcmDuration(source.format, source.frameCount)
        }

    /**
     * Estimated playback position derived from [progress] and [duration].
     */
    val position: Duration
        get() {
            val totalMs = duration.inWholeMilliseconds
            if (totalMs <= 0L) return Duration.ZERO
            return (totalMs * _progress.floatValue.toDouble()).toLong().milliseconds
        }

    /**
     * Loads an [AudioRecording] for playback.
     */
    fun load(recording: AudioRecording) {
        scope.launch {
            loadAsync(recording)
        }
    }

    /**
     * Loads an [AudioRecording] for playback (suspend version).
     */
    suspend fun loadAsync(recording: AudioRecording) {
        stateMutex.withLock {
            _error.value = null
            _isLoading.value = true
            _isFinished.value = false
            _progress.floatValue = 0f
            stopProgressTicker()
        }

        try {
            // Create new player if needed, or reuse existing one after stopping
            val player = stateMutex.withLock {
                _player?.also { it.stop() } ?: createPlayer().also { _player = it }
            }

            sourceRecording = recording
            player.load(recording)

            stateMutex.withLock {
                _loadedRecording.value = recording
                _isReady.value = true
                _isPlaying.value = false
                _isPaused.value = false
                _isLoading.value = false
                _progress.floatValue = 0f
            }

            observePlayerState(player)
        } catch (e: Exception) {
            logger.error(e) { "Error loading recording: ${e.message}" }
            stateMutex.withLock {
                _error.value = AudioError.from(e)
                _isLoading.value = false
            }
        }
    }

    /**
     * Starts or resumes playback.
     */
    fun play() {
        scope.launch {
            playAsync()
        }
    }

    /**
     * Starts or resumes playback (suspend version).
     */
    suspend fun playAsync() {
        val player = _player ?: return

        try {
            if (_isPaused.value) {
                player.resume()
                return
            }
            if (_isFinished.value || _progress.floatValue >= 1f) {
                _progress.floatValue = 0f
                sourceRecording?.let { player.load(it) }
            } else if (_progress.floatValue > 0f) {
                // Ensure the session is loaded at the current scrub position.
                loadPlaybackWindow(_progress.floatValue)
            }
            _isFinished.value = false
            player.start()
        } catch (e: Exception) {
            logger.error(e) { "Error starting playback: ${e.message}" }
            _error.value = AudioError.from(e)
        }
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        val wasPlayingOrSeeking = isPlaying
        _resumeAfterSeek.value = false
        seekJob?.cancel()
        seekJob = null
        stopProgressTicker()
        // Optimistic UI update: mid-seek the session may already be Idle/Ready,
        // so [Player.pause] alone would not clear the derived [isPlaying] flag.
        _isPlaying.value = false
        if (wasPlayingOrSeeking) {
            _isPaused.value = true
        }
        _player?.pause()
    }

    /**
     * Stops playback and resets to the beginning of the full recording.
     */
    fun stop() {
        _resumeAfterSeek.value = false
        seekJob?.cancel()
        seekJob = null
        stopProgressTicker()
        _player?.stop()
        _isPlaying.value = false
        _isPaused.value = false
        _isFinished.value = false
        _progress.floatValue = 0f
        scope.launch {
            reloadSourceAsync()
        }
    }

    /**
     * Stops playback and resets to the beginning (suspend version).
     */
    suspend fun stopAsync() {
        _resumeAfterSeek.value = false
        seekJob?.cancel()
        seekJob = null
        stopProgressTicker()
        _player?.stop()
        _isPlaying.value = false
        _isPaused.value = false
        _isFinished.value = false
        _progress.floatValue = 0f
        reloadSourceAsync()
    }

    private suspend fun reloadSourceAsync() {
        val player = _player ?: return
        val source = sourceRecording ?: return
        try {
            player.load(source)
            _isReady.value = true
        } catch (e: Exception) {
            logger.error(e) { "Error reloading source recording: ${e.message}" }
            _error.value = AudioError.from(e)
        }
    }

    /**
     * Seeks within the loaded recording.
     *
     * [progress] is normalized (`0f..1f`) over the full source duration.
     * If currently playing, playback continues from the new position by loading
     * a PCM window via public [AudioRecording] APIs (no session seek required).
     * Rapid scrub updates cancel prior in-flight seeks but preserve the
     * play-through-seek intent from the first press.
     */
    fun seekTo(progress: Float) {
        if (isPlaying) {
            _resumeAfterSeek.value = true
        }
        seekJob?.cancel()
        seekJob = scope.launch {
            seekToAsync(progress)
        }
    }

    /**
     * Seeks within the loaded recording (suspend version).
     */
    suspend fun seekToAsync(progress: Float) {
        val source = sourceRecording ?: return
        val player = _player ?: return
        val clamped = progress.coerceIn(0f, 1f)
        if (isPlaying) {
            _resumeAfterSeek.value = true
        }
        val shouldResume = _resumeAfterSeek.value

        stopProgressTicker()

        try {
            stateMutex.withLock {
                player.stop()
                // Session leaves Playing; keep [isPlaying] true via [_resumeAfterSeek]
                // so transport UI does not flicker while the seek window reloads.
                _isPlaying.value = false
                _isPaused.value = false
                _isFinished.value = false
                _progress.floatValue = clamped
            }

            if (clamped >= 1f) {
                _resumeAfterSeek.value = false
                player.load(source)
                _isReady.value = true
                _isPlaying.value = false
                _isFinished.value = true
                _progress.floatValue = 1f
                return
            }

            loadPlaybackWindow(clamped)
            _isReady.value = true

            if (shouldResume) {
                player.start()
            } else {
                _resumeAfterSeek.value = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error seeking: ${e.message}" }
            _resumeAfterSeek.value = false
            _isPlaying.value = false
            _error.value = AudioError.from(e)
        }
    }

    /**
     * Toggles between playing and paused states.
     */
    fun toggle() {
        scope.launch {
            toggleAsync()
        }
    }

    /**
     * Toggles between playing and paused states (suspend version).
     * @return true if now playing, false if paused/stopped, null if no action taken
     */
    suspend fun toggleAsync(): Boolean? {
        val player = _player ?: return null

        return when {
            isPlaying -> {
                pause()
                false
            }
            _isPaused.value -> {
                player.resume()
                true
            }
            _isReady.value || _isFinished.value -> {
                playAsync()
                true
            }
            else -> null
        }
    }

    /**
     * Clears the current error.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Resets the player state and unloads any recording.
     */
    fun reset() {
        scope.launch {
            resetAsync()
        }
    }

    /**
     * Resets the player state and unloads any recording (suspend version).
     */
    suspend fun resetAsync() {
        stateMutex.withLock {
            _resumeAfterSeek.value = false
            seekJob?.cancel()
            seekJob = null
            stopProgressTicker()
            stateObserverJob?.cancel()
            stateObserverJob = null
            _player?.release()
            _player = null
            sourceRecording = null
            _loadedRecording.value = null
            _isReady.value = false
            _isPlaying.value = false
            _isPaused.value = false
            _isLoading.value = false
            _isFinished.value = false
            _error.value = null
            _progress.floatValue = 0f
        }
    }

    /**
     * Cleans up resources. Called automatically when the composable leaves composition.
     */
    internal fun release() {
        _resumeAfterSeek.value = false
        seekJob?.cancel()
        seekJob = null
        stopProgressTicker()
        stateObserverJob?.cancel()
        stateObserverJob = null
        _player?.release()
        _player = null
        sourceRecording = null
    }

    private suspend fun createPlayer(): Player {
        return playerFactory?.invoke() ?: Kodio.player(device)
    }

    private suspend fun loadPlaybackWindow(progress: Float) {
        val source = sourceRecording ?: return
        val player = _player ?: return
        val frames = source.frameCount
        val bytesPerFrame = source.format.bytesPerFrame
        if (frames <= 0L || bytesPerFrame <= 0) {
            player.load(source)
            return
        }

        val bytes = source.toByteArray()
        val offsetBytes = seekByteOffset(
            progress = progress,
            frameCount = frames,
            bytesPerFrame = bytesPerFrame,
            byteCount = bytes.size,
        )
        if (offsetBytes <= 0) {
            player.load(source)
            return
        }
        if (offsetBytes >= bytes.size) {
            player.load(source)
            return
        }

        val window = AudioRecording.fromPcmBytes(
            format = source.format,
            data = bytes.copyOfRange(offsetBytes, bytes.size),
        )
        player.load(window)
    }

    private fun observePlayerState(player: Player) {
        stateObserverJob?.cancel()
        stateObserverJob = scope.launch {
            player.stateFlow.collectLatest { state ->
                when (state) {
                    is AudioPlaybackSession.State.Playing -> {
                        _resumeAfterSeek.value = false
                        _isPlaying.value = true
                        _isPaused.value = false
                        _isFinished.value = false
                        startProgressTicker()
                    }
                    is AudioPlaybackSession.State.Paused -> {
                        if (_resumeAfterSeek.value) {
                            // Ignore transient non-playing states while a
                            // play-through seek reloads the PCM window.
                            return@collectLatest
                        }
                        _isPlaying.value = false
                        _isPaused.value = true
                        _isFinished.value = false
                        stopProgressTicker()
                    }
                    is AudioPlaybackSession.State.Finished -> {
                        _resumeAfterSeek.value = false
                        stopProgressTicker()
                        _isPlaying.value = false
                        _isPaused.value = false
                        _isFinished.value = true
                        _progress.floatValue = 1f
                        // Use the latest callback reference
                        getOnPlaybackComplete()?.invoke()
                    }
                    is AudioPlaybackSession.State.Error -> {
                        _resumeAfterSeek.value = false
                        stopProgressTicker()
                        _isPlaying.value = false
                        _isPaused.value = false
                        _error.value = AudioError.from(state.error)
                    }
                    is AudioPlaybackSession.State.Ready -> {
                        _isReady.value = true
                        if (!_resumeAfterSeek.value) {
                            _isPlaying.value = false
                            _isPaused.value = false
                        }
                    }
                    is AudioPlaybackSession.State.Idle -> {
                        stopProgressTicker()
                        if (_resumeAfterSeek.value) {
                            // Keep derived [isPlaying] / ready UI stable across
                            // the stop→reload gap of a play-through seek.
                            return@collectLatest
                        }
                        _isPlaying.value = false
                        _isPaused.value = false
                        _isReady.value = false
                        _isFinished.value = false
                    }
                }
            }
        }
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        val totalMs = duration.inWholeMilliseconds
        if (totalMs <= 0L) return

        val startProgress = _progress.floatValue
        val mark = TimeSource.Monotonic.markNow()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                val next = (startProgress + elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
                _progress.floatValue = next
                if (next >= 1f) break
                delay(50)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }
}

/**
 * Creates and remembers a [PlayerState] for audio playback.
 *
 * @param device Optional specific output device
 * @param onPlaybackComplete Callback when playback finishes
 * @return A remembered PlayerState
 */
@Composable
fun rememberPlayerState(
    device: AudioDevice.Output? = null,
    onPlaybackComplete: (() -> Unit)? = null
): PlayerState {
    val scope = rememberCoroutineScope()

    // Use rememberUpdatedState to always have the latest callback
    val currentCallback by rememberUpdatedState(onPlaybackComplete)

    val state = remember(device) {
        PlayerState(
            scope = scope,
            device = device,
            getOnPlaybackComplete = { currentCallback }
        )
    }

    // Cleanup on disposal
    DisposableEffect(state) {
        onDispose {
            state.release()
        }
    }

    return state
}

/**
 * Creates and remembers a [PlayerState] pre-loaded with an [AudioRecording].
 *
 * @param recording The recording to load
 * @param device Optional specific output device
 * @param onPlaybackComplete Callback when playback finishes
 * @return A remembered PlayerState with the recording loaded
 */
@Composable
fun rememberPlayerState(
    recording: AudioRecording,
    device: AudioDevice.Output? = null,
    onPlaybackComplete: (() -> Unit)? = null
): PlayerState {
    val state = rememberPlayerState(
        device = device,
        onPlaybackComplete = onPlaybackComplete
    )

    // Load the recording when it changes
    LaunchedEffect(recording) {
        state.loadAsync(recording)
    }

    return state
}
