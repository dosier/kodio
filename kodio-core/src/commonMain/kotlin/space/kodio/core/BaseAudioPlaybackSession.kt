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
    protected val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    abstract suspend fun preparePlayback(format: AudioFormat): AudioFormat

    abstract suspend fun playBlocking(audioFlow: AudioFlow)

    protected abstract fun onPause()
    protected abstract fun onResume()
    protected abstract fun onStop()

    final override suspend fun play(audioFlow: AudioFlow) {

        if (_state.value == State.Playing) return

        runCatching {
            val playbackFormat = preparePlayback(audioFlow.format)
            val playbackAudioFlow = audioFlow.convertAudio(playbackFormat)
            playbackJob = scope.launch {
                runCatching {
                    playBlocking(playbackAudioFlow)
                    _state.value = State.Finished
                }.onFailure {
                    _state.value = State.Error(it)
                }
            }
        }.onFailure {
            _state.value = State.Error(it)
            it.printStackTrace()
        }.onSuccess {
            _state.value = State.Playing
        }
    }

    final override fun pause() {
        onPause()
        _state.value = State.Paused
    }

    final override fun resume() {
        onResume()
        _state.value = State.Playing
    }

    final override fun stop() {
        onStop()
        playbackJob?.cancel()
        _state.value = State.Idle
    }
}