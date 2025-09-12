package space.kodio.core

import space.kodio.core.AudioPlaybackSession.State
import space.kodio.core.io.convertAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

abstract class BaseAudioPlaybackSession : AudioPlaybackSession {

    private val _state = MutableStateFlow<State>(State.Idle)
    override val state: StateFlow<State> = _state.asStateFlow()

    private var playbackJob: Job? = null

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow.asStateFlow()

    protected val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    abstract suspend fun preparePlayback(format: AudioFormat): AudioFormat

    abstract suspend fun playBlocking(audioFlow: AudioFlow)

    protected abstract fun onPause()
    protected abstract fun onResume()
    protected abstract fun onStop()

    final override suspend fun load(audioFlow: AudioFlow) {
        _audioFlow.value = audioFlow
        _state.value = State.Ready
    }

    final override suspend fun play() {
        val audioFlow = audioFlow.value ?: return
        try {
            val playbackFormat = preparePlayback(audioFlow.format)
            val playbackAudioFlow = audioFlow.convertAudio(playbackFormat)
            _state.value = State.Playing
            playbackJob = scope.launch {
                runCatching {
                    playBlocking(playbackAudioFlow)
                    _state.value = State.Finished
                }.onFailure {
                    _state.value = State.Error(it)
                }
            }
        } catch (e: Exception) {
            _state.value = State.Error(e)
        }
    }

    final override fun pause() {
        runAndUpdateState(State.Paused, ::onPause)
    }

    final override fun resume() {
        runAndUpdateState(State.Playing, ::onResume)
    }

    final override fun stop() {
        runAndUpdateState(State.Idle) {
            onStop()
            playbackJob?.cancel()
        }
    }

    protected fun runAndUpdateState(newState: State, block: () -> Unit) {
        _state.value = runCatching {
            block()
            newState
        }.getOrElse {
            State.Error(it)
        }
    }

}