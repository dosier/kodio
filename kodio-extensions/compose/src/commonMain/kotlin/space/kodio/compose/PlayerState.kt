package space.kodio.compose

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kodio.core.*

/**
 * State holder for audio playback in Compose.
 * 
 * Provides a simplified, reactive API for playing audio that integrates
 * seamlessly with Compose's state management.
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
    private val getOnPlaybackComplete: () -> (() -> Unit)?
) {
    private var _player: Player? = null
    private var _isPlaying = mutableStateOf(false)
    private var _isPaused = mutableStateOf(false)
    private var _isReady = mutableStateOf(false)
    private var _isLoading = mutableStateOf(false)
    private var _isFinished = mutableStateOf(false)
    private var _error = mutableStateOf<AudioError?>(null)
    private var _loadedRecording = mutableStateOf<AudioRecording?>(null)
    private var stateObserverJob: Job? = null
    
    // Mutex for thread-safe state transitions
    private val stateMutex = Mutex()

    /**
     * Whether the player is currently playing.
     */
    val isPlaying: Boolean by _isPlaying

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
     * The currently loaded recording.
     */
    val recording: AudioRecording? by _loadedRecording

    /**
     * Whether a recording is loaded.
     */
    val hasRecording: Boolean
        get() = _loadedRecording.value != null

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
        }
        
        try {
            // Create new player if needed, or reuse existing one after stopping
            val player = stateMutex.withLock {
                _player?.also { it.stop() } ?: createPlayer().also { _player = it }
            }
            
            player.load(recording)
            
            stateMutex.withLock {
                _loadedRecording.value = recording
                _isReady.value = true
                _isPlaying.value = false
                _isPaused.value = false
                _isLoading.value = false
            }
            
            observePlayerState(player)
        } catch (e: Exception) {
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
            } else {
                _isFinished.value = false
                player.start()
            }
        } catch (e: Exception) {
            _error.value = AudioError.from(e)
        }
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        _player?.pause()
    }

    /**
     * Stops playback and resets to the beginning.
     */
    fun stop() {
        _player?.stop()
        _isPlaying.value = false
        _isPaused.value = false
        _isFinished.value = false
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
            _isPlaying.value -> {
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
            stateObserverJob?.cancel()
            stateObserverJob = null
            _player?.release()
            _player = null
            _loadedRecording.value = null
            _isReady.value = false
            _isPlaying.value = false
            _isPaused.value = false
            _isLoading.value = false
            _isFinished.value = false
            _error.value = null
        }
    }

    /**
     * Cleans up resources. Called automatically when the composable leaves composition.
     */
    internal fun release() {
        stateObserverJob?.cancel()
        stateObserverJob = null
        _player?.release()
        _player = null
    }

    private suspend fun createPlayer(): Player {
        return Kodio.player(device)
    }

    private fun observePlayerState(player: Player) {
        stateObserverJob?.cancel()
        stateObserverJob = scope.launch {
            player.stateFlow.collectLatest { state ->
                when (state) {
                    is AudioPlaybackSession.State.Playing -> {
                        _isPlaying.value = true
                        _isPaused.value = false
                        _isFinished.value = false
                    }
                    is AudioPlaybackSession.State.Paused -> {
                        _isPlaying.value = false
                        _isPaused.value = true
                        _isFinished.value = false
                    }
                    is AudioPlaybackSession.State.Finished -> {
                        _isPlaying.value = false
                        _isPaused.value = false
                        _isFinished.value = true
                        // Use the latest callback reference
                        getOnPlaybackComplete()?.invoke()
                    }
                    is AudioPlaybackSession.State.Error -> {
                        _isPlaying.value = false
                        _isPaused.value = false
                        _error.value = AudioError.from(state.error)
                    }
                    is AudioPlaybackSession.State.Ready -> {
                        _isReady.value = true
                        _isPlaying.value = false
                        _isPaused.value = false
                    }
                    is AudioPlaybackSession.State.Idle -> {
                        _isPlaying.value = false
                        _isPaused.value = false
                        _isReady.value = false
                        _isFinished.value = false
                    }
                }
            }
        }
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
