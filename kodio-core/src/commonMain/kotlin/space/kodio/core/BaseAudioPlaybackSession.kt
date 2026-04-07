package space.kodio.core

import space.kodio.core.AudioPlaybackSession.State
import space.kodio.core.io.convertAudio
import space.kodio.core.util.namedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

private val log = namedLogger("BasePlayback")

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
        log.info { "load(): format=${audioFlow.format}" }
        _audioFlow.value = audioFlow
        _state.value = State.Ready
    }

    final override suspend fun play() {
        val audioFlow = audioFlow.value
        if (audioFlow == null) {
            log.warn { "play() called but audioFlow is null, returning" }
            return
        }
        log.info { "play(): audioFlow.format=${audioFlow.format}" }
        try {
            log.info { "Calling preparePlayback()" }
            val playbackFormat = preparePlayback(audioFlow.format)
            log.info { "preparePlayback() returned format: $playbackFormat" }
            val playbackAudioFlow = audioFlow.convertAudio(playbackFormat)
            log.info { "Audio conversion applied, starting playback" }
            _state.value = State.Playing
            playbackJob = scope.launch {
                runCatching {
                    playBlocking(playbackAudioFlow)
                    _state.value = State.Finished
                    log.info { "Playback finished" }
                }.onFailure {
                    log.error(it) { "Playback failed: ${it.message}" }
                    _state.value = State.Error(it)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "play() failed during preparation: ${e.message}" }
            _state.value = State.Error(e)
        }
    }

    final override fun pause() {
        log.info { "pause()" }
        runAndUpdateState(State.Paused, ::onPause)
    }

    final override fun resume() {
        log.info { "resume()" }
        runAndUpdateState(State.Playing, ::onResume)
    }

    final override fun stop() {
        log.info { "stop()" }
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
            log.error(it) { "State transition to $newState failed: ${it.message}" }
            State.Error(it)
        }
    }

}