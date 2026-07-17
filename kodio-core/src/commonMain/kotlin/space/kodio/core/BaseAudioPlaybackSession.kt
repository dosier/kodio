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
import kotlin.time.Duration

private val log = namedLogger("BasePlayback")

abstract class BaseAudioPlaybackSession : AudioPlaybackSession {

    private val _state = MutableStateFlow<State>(State.Idle)
    override val state: StateFlow<State> = _state.asStateFlow()

    private var playbackJob: Job? = null

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow.asStateFlow()

    /**
     * Desired transport intent. [pause] / [stop] clear this so an in-flight [play]
     * that finishes prepare/convert cannot overwrite a later pause with [State.Playing].
     */
    private val wantPlaying = MutableStateFlow(false)

    /** Bumped on pause/stop/new play to invalidate in-flight [play] generations. */
    private val playEpoch = MutableStateFlow(0)

    protected val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    abstract suspend fun preparePlayback(format: AudioFormat): AudioFormat

    abstract suspend fun playBlocking(audioFlow: AudioFlow)

    protected abstract fun onPause()
    protected abstract fun onResume()
    protected abstract fun onStop()

    /**
     * Platform hook for native seek. Return [SeekResult.Applied] if repositioned.
     * Default is [SeekResult.Unsupported]; [AudioPlaybackSession.seekTo] documents
     * the Compose/Player fallback.
     */
    protected open fun onSeek(position: Duration): SeekResult = SeekResult.Unsupported

    final override fun seekTo(position: Duration): SeekResult {
        if (audioFlow.value == null) return SeekResult.Unsupported
        return onSeek(position)
    }

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

        val epoch = playEpoch.value + 1
        playEpoch.value = epoch
        wantPlaying.value = true

        // Replace any prior drain job so overlapping plays cannot race pause.
        playbackJob?.cancel()
        playbackJob = null

        log.info { "play(): audioFlow.format=${audioFlow.format}, epoch=$epoch" }
        try {
            log.info { "Calling preparePlayback()" }
            val playbackFormat = preparePlayback(audioFlow.format)
            log.info { "preparePlayback() returned format: $playbackFormat" }
            if (!isCurrentPlay(epoch)) {
                log.info { "play() aborted after prepare: pause/stop won (epoch=$epoch)" }
                ensurePausedIfStillDesired()
                return
            }

            val playbackAudioFlow = audioFlow.convertAudio(playbackFormat)
            log.info { "Audio conversion applied, starting playback" }
            if (!isCurrentPlay(epoch)) {
                log.info { "play() aborted after convert: pause/stop won (epoch=$epoch)" }
                ensurePausedIfStillDesired()
                return
            }

            _state.value = State.Playing
            playbackJob = scope.launch {
                runCatching {
                    playBlocking(playbackAudioFlow)
                    if (isCurrentPlay(epoch)) {
                        _state.value = State.Finished
                        wantPlaying.value = false
                        log.info { "Playback finished" }
                    }
                }.onFailure {
                    if (isCurrentPlay(epoch)) {
                        log.error(it) { "Playback failed: ${it.message}" }
                        _state.value = State.Error(it)
                        wantPlaying.value = false
                    }
                }
            }
        } catch (e: Exception) {
            if (isCurrentPlay(epoch)) {
                log.error(e) { "play() failed during preparation: ${e.message}" }
                _state.value = State.Error(e)
                wantPlaying.value = false
            }
        }
    }

    final override fun pause() {
        log.info { "pause()" }
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        runAndUpdateState(State.Paused, ::onPause)
    }

    final override fun resume() {
        log.info { "resume()" }
        wantPlaying.value = true
        runAndUpdateState(State.Playing, ::onResume)
    }

    final override fun stop() {
        log.info { "stop()" }
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        runAndUpdateState(State.Idle) {
            onStop()
            playbackJob?.cancel()
            playbackJob = null
        }
    }

    private fun isCurrentPlay(epoch: Int): Boolean =
        wantPlaying.value && playEpoch.value == epoch

    private fun ensurePausedIfStillDesired() {
        if (wantPlaying.value) return
        when (_state.value) {
            is State.Idle, is State.Finished, is State.Error -> return
            else -> {
                // Re-apply pause so [onPause] runs after prepare created the device
                // (pause may have landed while prepare was still in flight).
                runAndUpdateState(State.Paused, ::onPause)
            }
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
