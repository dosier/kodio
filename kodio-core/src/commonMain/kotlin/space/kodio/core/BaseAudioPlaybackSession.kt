package space.kodio.core

import space.kodio.core.AudioPlaybackSession.State
import space.kodio.core.io.convertAudio
import space.kodio.core.transport.AudioSink
import space.kodio.core.transport.PlaybackBuffer
import space.kodio.core.transport.TransportController
import space.kodio.core.util.namedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val log = namedLogger("BasePlayback")

/**
 * Shared playback session: prepare → convert once → either legacy [playBlocking]
 * or ingest ⊥ transport ([useTransportPlayback]).
 *
 * [seekEpoch] (inside [TransportController]) is independent of [playEpoch]:
 * seeks abandon in-flight device writes without cancelling an in-flight play()
 * handshake. Stop/dispose still bumps [playEpoch] and tears transport down.
 */
abstract class BaseAudioPlaybackSession : AudioPlaybackSession {

    private val _state = MutableStateFlow<State>(State.Idle)
    override val state: StateFlow<State> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var ingestJob: Job? = null
    private var rangePublisherJob: Job? = null

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow.asStateFlow()

    private val _position = MutableStateFlow(Duration.ZERO)
    private val _bufferedRange = MutableStateFlow(BufferedRange.Empty)
    override val position: StateFlow<Duration> = _position.asStateFlow()
    override val bufferedRange: StateFlow<BufferedRange> = _bufferedRange.asStateFlow()

    /**
     * Desired transport intent. [pause] / [stop] clear this so an in-flight [play]
     * that finishes prepare/convert cannot overwrite a later pause with [State.Playing].
     */
    private val wantPlaying = MutableStateFlow(false)

    /** Bumped on pause/stop/new play to invalidate in-flight [play] generations. */
    private val playEpoch = MutableStateFlow(0)

    protected val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    /** Retention / priming knobs for the transport buffer. */
    internal open val bufferConfig: PlaybackBufferConfig = PlaybackBufferConfig()

    /**
     * When `true`, [play] uses ingest ⊥ [TransportController] with [createAudioSink]
     * instead of [playBlocking]. Unmigrated platforms keep the default `false`.
     *
     * `internal` (not protected): must not expose the internal [AudioSink] /
     * [TransportController] types on the public base class API surface.
     */
    internal open fun useTransportPlayback(): Boolean = false

    /**
     * Create the dumb device sink for transport feed. Required when
     * [useTransportPlayback] is `true`.
     */
    internal open fun createAudioSink(format: AudioFormat): AudioSink {
        error("createAudioSink() must be overridden when useTransportPlayback() is true")
    }

    /**
     * Best-effort audible position while the device is open.
     * Default uses the soft read cursor (slightly ahead of hardware).
     */
    internal open fun readDeviceMediaPosition(
        mediaBase: Duration,
        transport: TransportController,
    ): Duration = transport.buffer.format.durationOfByteOffset(transport.readCursor)

    /** Called after a transport seek is Applied (mediaBase / device baseline reset). */
    internal open fun onTransportSeekApplied(position: Duration) = Unit

    /** Called when transport feed should pause the device (session pause). */
    internal open fun onTransportPaused() = Unit

    /** Called when transport feed should resume the device (session resume). */
    internal open fun onTransportResumed() = Unit

    /** Tear down the device after transport stop / discard. */
    internal open fun onTransportStopped() = Unit

    /**
     * Re-open / re-prime the device after EOS close when seeking/playing again
     * from a retained buffer. Default no-op (device kept open).
     */
    internal open suspend fun ensureDeviceReadyForTransport(format: AudioFormat): AudioFormat =
        format

    /**
     * Optional pacing after a successful transport [TransportController.feed].
     * JVM sinks block inside [AudioSink.write]; Web uses this to bound look-ahead.
     */
    internal open suspend fun paceAfterTransportWrite(bytesWritten: Int, format: AudioFormat) = Unit

    // --- Transport runtime (retained across Finished until stop/load) ---

    private var transport: TransportController? = null
    private var transportFormat: AudioFormat? = null
    private var transportSink: AudioSink? = null
    private var mediaBase: Duration = Duration.ZERO
    private var convertedKnownByteCount: Long? = null

    private val _transportPaused = MutableStateFlow(false)

    /** Suspend until the session is not paused (for blocking device write loops). */
    internal suspend fun awaitTransportUnpaused() {
        _transportPaused.first { !it }
    }

    internal fun currentTransport(): TransportController? = transport

    internal fun currentMediaBase(): Duration = mediaBase

    abstract suspend fun preparePlayback(format: AudioFormat): AudioFormat

    /**
     * Legacy pull-loop path. All in-tree platforms use transport playback
     * ([useTransportPlayback]); this remains only for external subclassers.
     */
    @Deprecated(
        message = "Override useTransportPlayback()/createAudioSink() instead of playBlocking",
        level = DeprecationLevel.WARNING,
    )
    open suspend fun playBlocking(audioFlow: AudioFlow) {
        error(
            "${this::class.simpleName}: playBlocking is deprecated; " +
                "enable useTransportPlayback() and implement createAudioSink()",
        )
    }

    protected abstract fun onPause()
    protected abstract fun onResume()
    protected abstract fun onStop()

    /**
     * Legacy seek hook when [useTransportPlayback] is false.
     */
    protected open fun onSeek(position: Duration): SeekResult = SeekResult.Unsupported

    final override fun seekTo(position: Duration): SeekResult {
        if (audioFlow.value == null) return SeekResult.Unsupported
        if (useTransportPlayback()) {
            val t = transport ?: return SeekResult.Unsupported
            val result = t.seekTo(position)
            if (result.isApplied) {
                mediaBase = position
                _position.value = position
                onTransportSeekApplied(position)
                // Seek after finish: buffer retained → Paused (do not auto-resume).
                when (_state.value) {
                    is State.Finished -> {
                        wantPlaying.value = false
                        _transportPaused.value = true
                        _state.value = State.Paused
                    }
                    is State.Playing -> {
                        // Device flush/re-prime is async via sink; stay Playing.
                    }
                    else -> Unit
                }
                publishBufferedRange(t)
            }
            return result
        }
        return onSeek(position)
    }

    final override suspend fun load(audioFlow: AudioFlow) {
        log.info { "load(): format=${audioFlow.format}" }
        discardTransport()
        _audioFlow.value = audioFlow
        _position.value = Duration.ZERO
        _bufferedRange.value = BufferedRange.Empty
        _state.value = State.Ready
    }

    final override suspend fun play() {
        val audioFlow = audioFlow.value
        if (audioFlow == null) {
            log.warn { "play() called but audioFlow is null, returning" }
            return
        }

        if (useTransportPlayback()) {
            playViaTransport(audioFlow)
            return
        }

        playViaLegacy(audioFlow)
    }

    private suspend fun playViaLegacy(audioFlow: AudioFlow) {
        val epoch = playEpoch.value + 1
        playEpoch.value = epoch
        wantPlaying.value = true

        playbackJob?.cancel()
        playbackJob = null

        log.info { "play(): audioFlow.format=${audioFlow.format}, epoch=$epoch (legacy)" }
        try {
            val playbackFormat = preparePlayback(audioFlow.format)
            if (!isCurrentPlay(epoch)) {
                log.info { "play() aborted after prepare: pause/stop won (epoch=$epoch)" }
                ensurePausedIfStillDesired()
                return
            }

            val playbackAudioFlow = audioFlow.convertAudio(playbackFormat)
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
                    if (it is CancellationException) throw it
                    if (isCurrentPlay(epoch)) {
                        log.error(it) { "Playback failed: ${it.message}" }
                        _state.value = State.Error(it)
                        wantPlaying.value = false
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (isCurrentPlay(epoch)) {
                log.error(e) { "play() failed during preparation: ${e.message}" }
                _state.value = State.Error(e)
                wantPlaying.value = false
            }
        }
    }

    private suspend fun playViaTransport(audioFlow: AudioFlow) {
        val existing = transport
        // Seek-after-finish / replay from retained buffer: do not re-ingest a cold flow.
        if (existing != null && existing.isIngestComplete && transportFormat != null) {
            val epoch = playEpoch.value + 1
            playEpoch.value = epoch
            wantPlaying.value = true
            _transportPaused.value = false

            playbackJob?.cancel()
            playbackJob = null

            try {
                val format = transportFormat!!
                ensureDeviceReadyForTransport(format)
                if (!isCurrentPlay(epoch)) {
                    ensurePausedIfStillDesired()
                    return
                }
                existing.prime()
                onTransportResumed()
                _state.value = State.Playing
                playbackJob = scope.launch { runTransportFeedLoop(epoch, existing) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isCurrentPlay(epoch)) {
                    log.error(e) { "play() restart from retained buffer failed: ${e.message}" }
                    _state.value = State.Error(e)
                    wantPlaying.value = false
                }
            }
            return
        }

        val epoch = playEpoch.value + 1
        playEpoch.value = epoch
        wantPlaying.value = true
        _transportPaused.value = false

        playbackJob?.cancel()
        playbackJob = null
        ingestJob?.cancel()
        ingestJob = null

        log.info { "play(): audioFlow.format=${audioFlow.format}, epoch=$epoch (transport)" }
        try {
            val playbackFormat = preparePlayback(audioFlow.format)
            if (!isCurrentPlay(epoch)) {
                ensurePausedIfStillDesired()
                return
            }

            val playbackAudioFlow = audioFlow.convertAudio(playbackFormat)
            if (!isCurrentPlay(epoch)) {
                ensurePausedIfStillDesired()
                return
            }

            // Fresh transport for this load/play generation.
            discardTransportBuffersOnly()
            val known = playbackAudioFlow.knownByteCount
            convertedKnownByteCount = known
            val buffer = PlaybackBuffer(
                format = playbackAudioFlow.format,
                knownByteCount = known,
                config = bufferConfig,
            )
            val sink = createAudioSink(playbackAudioFlow.format)
            val controller = TransportController(
                buffer = buffer,
                sink = sink,
                config = bufferConfig,
                knownByteCount = known,
            )
            transport = controller
            transportFormat = playbackAudioFlow.format
            transportSink = sink
            mediaBase = Duration.ZERO
            _position.value = Duration.ZERO
            publishBufferedRange(controller)
            startBufferedRangePublisher(controller)

            _state.value = State.Playing

            ingestJob = scope.launch {
                runCatching {
                    playbackAudioFlow.collect { chunk ->
                        controller.append(chunk)
                    }
                    controller.markIngestComplete()
                    log.debug { "ingest complete writePos=${controller.buffer.writePosition}" }
                }.onFailure {
                    if (it is CancellationException) throw it
                    log.error(it) { "ingest failed: ${it.message}" }
                }
            }

            playbackJob = scope.launch {
                runCatching {
                    controller.prime()
                    runTransportFeedLoop(epoch, controller)
                }.onFailure {
                    if (it is CancellationException) throw it
                    if (isCurrentPlay(epoch)) {
                        log.error(it) { "transport feed failed: ${it.message}" }
                        _state.value = State.Error(it)
                        wantPlaying.value = false
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (isCurrentPlay(epoch)) {
                log.error(e) { "play() failed during preparation: ${e.message}" }
                _state.value = State.Error(e)
                wantPlaying.value = false
            }
        }
    }

    private suspend fun runTransportFeedLoop(epoch: Int, controller: TransportController) {
        while (scope.isActive && isCurrentPlay(epoch)) {
            awaitTransportUnpaused()
            if (!isCurrentPlay(epoch)) break

            val n = controller.feed()
            when {
                n < 0 -> {
                    // EOS: ingest complete AND cursor at end.
                    if (isCurrentPlay(epoch)) {
                        _state.value = State.Finished
                        wantPlaying.value = false
                        _transportPaused.value = true
                        _position.value = mediaBase.coerceAtLeast(
                            controller.buffer.format.durationOfByteOffset(controller.readCursor)
                        )
                        publishBufferedRange(controller)
                        log.info { "Transport playback finished" }
                    }
                    return
                }
                n == 0 -> {
                    // Underrun / pending cursor — brief yield without sleeping the device forever.
                    delay(2.milliseconds)
                }
                else -> {
                    paceAfterTransportWrite(n, controller.buffer.format)
                    _position.value = readDeviceMediaPosition(mediaBase, controller)
                    publishBufferedRange(controller)
                }
            }
        }
    }

    private fun startBufferedRangePublisher(controller: TransportController) {
        rangePublisherJob?.cancel()
        rangePublisherJob = scope.launch {
            while (isActive && transport === controller) {
                publishBufferedRange(controller)
                delay(50.milliseconds)
            }
        }
    }

    private fun publishBufferedRange(controller: TransportController) {
        val format = controller.buffer.format
        val range = controller.buffer.bufferedRange
        _bufferedRange.value = if (range.isEmpty()) {
            BufferedRange.Empty
        } else {
            BufferedRange(
                start = format.durationOfByteOffset(range.first),
                endExclusive = format.durationOfByteOffset(range.last + 1),
            )
        }
    }

    final override fun pause() {
        log.info { "pause()" }
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        _transportPaused.value = true
        runAndUpdateState(State.Paused) {
            if (useTransportPlayback() && transport != null) {
                onTransportPaused()
            } else {
                onPause()
            }
        }
    }

    final override fun resume() {
        log.info { "resume()" }
        wantPlaying.value = true
        _transportPaused.value = false
        // New play epoch so a finished feed loop can be restarted via play(),
        // but resume while Playing/Paused reuses the existing feed if still alive.
        runAndUpdateState(State.Playing) {
            if (useTransportPlayback() && transport != null) {
                onTransportResumed()
                val t = transport
                val format = transportFormat
                if (t != null && playbackJob?.isActive != true && format != null) {
                    val epoch = playEpoch.value + 1
                    playEpoch.value = epoch
                    playbackJob = scope.launch {
                        runCatching {
                            // Device may have been closed at EOS.
                            ensureDeviceReadyForTransport(format)
                            t.prime()
                            runTransportFeedLoop(epoch, t)
                        }.onFailure {
                            if (it is CancellationException) throw it
                            if (isCurrentPlay(epoch)) {
                                log.error(it) { "resume feed failed: ${it.message}" }
                                _state.value = State.Error(it)
                                wantPlaying.value = false
                            }
                        }
                    }
                }
            } else {
                onResume()
            }
        }
    }

    final override fun stop() {
        log.info { "stop()" }
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        _transportPaused.value = true
        runAndUpdateState(State.Idle) {
            if (useTransportPlayback()) {
                onTransportStopped()
            } else {
                onStop()
            }
            playbackJob?.cancel()
            playbackJob = null
            ingestJob?.cancel()
            ingestJob = null
            discardTransport()
        }
    }

    private fun discardTransport() {
        discardTransportBuffersOnly()
        _position.value = Duration.ZERO
        _bufferedRange.value = BufferedRange.Empty
        mediaBase = Duration.ZERO
        convertedKnownByteCount = null
    }

    private fun discardTransportBuffersOnly() {
        rangePublisherJob?.cancel()
        rangePublisherJob = null
        transport = null
        transportFormat = null
        transportSink = null
    }

    private fun isCurrentPlay(epoch: Int): Boolean =
        wantPlaying.value && playEpoch.value == epoch

    private fun ensurePausedIfStillDesired() {
        if (wantPlaying.value) return
        when (_state.value) {
            is State.Idle, is State.Finished, is State.Error -> return
            else -> {
                _transportPaused.value = true
                runAndUpdateState(State.Paused) {
                    if (useTransportPlayback() && transport != null) {
                        onTransportPaused()
                    } else {
                        onPause()
                    }
                }
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
