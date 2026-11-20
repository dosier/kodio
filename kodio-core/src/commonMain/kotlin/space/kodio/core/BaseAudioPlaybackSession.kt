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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
 * [seekEpoch] (inside [TransportController]) is independent of the play epoch:
 * seeks abandon in-flight device writes without cancelling an in-flight play()
 * handshake. Stop/dispose still bumps the play epoch and tears transport down.
 *
 * Play intent is one atomic [PlayIntent] so "is this generation still current"
 * and "does the user want playback" can never be observed or changed apart.
 * The epoch identifies a play generation (bumped by play/stop/feed restart);
 * pause/resume only flip [PlayIntent.wantPlaying], so a paused feed loop parks
 * instead of exiting.
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
     * @property epoch current play generation; work stamped with an older epoch is stale.
     * @property wantPlaying user intent. [pause] / [stop] clear it so an in-flight [play]
     * that finishes prepare/convert cannot overwrite a later pause with [State.Playing].
     */
    private data class PlayIntent(val epoch: Int, val wantPlaying: Boolean)

    private val intent = MutableStateFlow(PlayIntent(epoch = 0, wantPlaying = false))

    /** Starts a new play generation that wants playback; returns its epoch. */
    private fun beginPlay(): Int =
        intent.updateAndGet { PlayIntent(epoch = it.epoch + 1, wantPlaying = true) }.epoch

    /** Invalidates every in-flight generation (stop/dispose). */
    private fun abandonPlay() {
        intent.update { PlayIntent(epoch = it.epoch + 1, wantPlaying = false) }
    }

    private fun setWantPlaying(want: Boolean) {
        intent.update { it.copy(wantPlaying = want) }
    }

    /**
     * Atomically ends generation [epoch] if it is still current (paused or not).
     * Returns `false` when a newer play()/stop() already replaced it; the caller
     * must then leave [state] alone. This is what keeps a finishing playback from
     * clearing the intent of a play() that started a moment later.
     */
    private fun endPlay(epoch: Int): Boolean {
        while (true) {
            val current = intent.value
            if (current.epoch != epoch) return false
            if (intent.compareAndSet(current, current.copy(wantPlaying = false))) return true
        }
    }

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

    /**
     * Frames already handed to the device that it has not played yet. At end of
     * stream the session waits for this to reach 0 before reporting
     * [State.Finished]; otherwise a stop()/release() right after
     * [Player.awaitComplete] cuts off the tail. Default `0` (no device queue).
     */
    internal open fun deviceQueuedFrames(): Long = 0L

    // --- Transport runtime (retained across Finished until stop/load) ---

    private var transport: TransportController? = null
    private var transportFormat: AudioFormat? = null
    private var transportSink: AudioSink? = null
    private var mediaBase: Duration = Duration.ZERO
    /**
     * Converted-flow [AudioFlow.knownByteCount] for the active transport generation.
     * Set before [createAudioSink] so platforms can choose growable vs ring / finite paths.
     */
    protected var convertedKnownByteCount: Long? = null
        private set

    /** Pre-play seek accepted before the transport controller exists. */
    private var pendingSeekPosition: Duration? = null

    private val _transportPaused = MutableStateFlow(false)

    /** Suspend until the session is not paused (for blocking device write loops). */
    internal suspend fun awaitTransportUnpaused() {
        _transportPaused.first { !it }
    }

    internal fun currentTransport(): TransportController? = transport

    /**
     * In-tree platforms that return `true` from [useTransportPlayback] publish
     * real [bufferedRange] / transport seek. External subclassers keep the default.
     */
    override val supportsTransportSeek: Boolean
        get() = useTransportPlayback()

    abstract suspend fun preparePlayback(format: AudioFormat): AudioFormat

    /**
     * Legacy pull-loop path. All in-tree platforms use transport playback
     * ([useTransportPlayback]); this remains only for external subclassers
     * until removal in a future minor (cannot override the internal transport hooks).
     */
    @Deprecated(
        message = "Migrate to a transport-capable session; playBlocking will be removed in a future minor",
        level = DeprecationLevel.WARNING,
    )
    open suspend fun playBlocking(audioFlow: AudioFlow) {
        error(
            "${this::class.simpleName}: playBlocking is deprecated; " +
                "use a transport-capable AudioPlaybackSession implementation",
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
        val loaded = audioFlow.value ?: return SeekResult.Unsupported
        if (useTransportPlayback()) {
            val t = transport
            if (t == null) {
                // Pre-play seek: stash a pending cursor for known-finite content
                // (matches JVM/Web historical behavior and pending-cursor semantics).
                // Live/unbounded cannot wait for future data before ingest starts.
                if (loaded.knownByteCount == null && position > Duration.ZERO) {
                    return SeekResult.Unsupported
                }
                pendingSeekPosition = position
                mediaBase = position
                _position.value = position
                log.debug { "seekTo($position) -> Applied (pending until transport starts)" }
                return SeekResult.Applied
            }
            val result = t.seekTo(position)
            if (result.isApplied) {
                pendingSeekPosition = null
                mediaBase = position
                _position.value = position
                onTransportSeekApplied(position)
                // Seek after finish: buffer retained → Paused (do not auto-resume).
                when (_state.value) {
                    is State.Finished -> {
                        setWantPlaying(false)
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
        val epoch = beginPlay()

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
                    @Suppress("DEPRECATION")
                    playBlocking(playbackAudioFlow)
                    if (endPlay(epoch)) {
                        _state.value = State.Finished
                        log.info { "Playback finished" }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    if (endPlay(epoch)) {
                        log.error(it) { "Playback failed: ${it.message}" }
                        _state.value = State.Error(it)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (endPlay(epoch)) {
                log.error(e) { "play() failed during preparation: ${e.message}" }
                _state.value = State.Error(e)
            }
        }
    }

    private suspend fun playViaTransport(audioFlow: AudioFlow) {
        val existing = transport
        // Seek-after-finish / replay from retained buffer: do not re-ingest a cold flow.
        val retainedFormat = transportFormat
        // A buffer truncated by an ingest failure is not replayable: re-ingest instead.
        if (existing != null && existing.isIngestComplete && existing.ingestFailure == null &&
            retainedFormat != null
        ) {
            val epoch = beginPlay()
            _transportPaused.value = false

            playbackJob?.cancel()
            playbackJob = null

            try {
                ensureDeviceReadyForTransport(retainedFormat)
                if (!isCurrentPlay(epoch)) {
                    ensurePausedIfStillDesired()
                    return
                }
                // Play again after Finished (no seek since): restart from the top,
                // as before transport playback, instead of instantly re-finishing at EOS.
                if (pendingSeekPosition == null && existing.isFinished) {
                    pendingSeekPosition = Duration.ZERO
                }
                applyPendingSeek(existing)
                existing.prime()
                onTransportResumed()
                _state.value = State.Playing
                playbackJob = scope.launch { runTransportFeedLoop(epoch, existing) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (endPlay(epoch)) {
                    log.error(e) { "play() restart from retained buffer failed: ${e.message}" }
                    _state.value = State.Error(e)
                }
            }
            return
        }

        val epoch = beginPlay()
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
            if (pendingSeekPosition == null) {
                mediaBase = Duration.ZERO
                _position.value = Duration.ZERO
            }
            applyPendingSeek(controller)
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
                    // Without this the feed loop waits forever for data that never
                    // arrives (state stuck at Playing, awaitComplete() never returns).
                    controller.markIngestFailed(it)
                }
            }

            playbackJob = scope.launch {
                runCatching {
                    controller.prime()
                    runTransportFeedLoop(epoch, controller)
                }.onFailure {
                    if (it is CancellationException) throw it
                    if (endPlay(epoch)) {
                        log.error(it) { "transport feed failed: ${it.message}" }
                        _state.value = State.Error(it)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (endPlay(epoch)) {
                log.error(e) { "play() failed during preparation: ${e.message}" }
                _state.value = State.Error(e)
            }
        }
    }

    private suspend fun runTransportFeedLoop(epoch: Int, controller: TransportController) {
        // Runs for the whole generation; parks (not exits) while paused.
        while (scope.isActive && isCurrentGeneration(epoch)) {
            awaitTransportUnpaused()
            if (!isCurrentGeneration(epoch)) break

            val n = controller.feed()
            when {
                n < 0 -> {
                    // EOS: ingest complete AND cursor at end. Let the device play out
                    // what it holds first; a seek meanwhile reopens the stream.
                    if (!awaitDeviceDrained(epoch, controller)) continue
                    if (endPlay(epoch)) {
                        val failure = controller.ingestFailure
                        _state.value = if (failure != null) State.Error(failure) else State.Finished
                        _transportPaused.value = true
                        _position.value = mediaBase.coerceAtLeast(
                            controller.buffer.format.durationOfByteOffset(controller.readCursor)
                        )
                        publishBufferedRange(controller)
                        if (failure != null) {
                            log.error(failure) { "Transport playback ended early: ingest failed" }
                        } else {
                            log.info { "Transport playback finished" }
                        }
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

    /**
     * Waits until the device has played everything written so far.
     * Returns `false` when a seek reopened the stream (keep feeding), `true` when
     * drained or the generation was replaced (caller's [endPlay] sorts that out).
     */
    private suspend fun awaitDeviceDrained(epoch: Int, controller: TransportController): Boolean {
        var lastQueued = Long.MAX_VALUE
        var stalledMs = 0L
        while (true) {
            if (!isCurrentGeneration(epoch)) return true
            if (!controller.isFinished) return false
            awaitTransportUnpaused()
            if (!isCurrentGeneration(epoch)) return true
            if (!controller.isFinished) return false
            val queued = deviceQueuedFrames()
            if (queued <= 0L) return true
            _position.value = readDeviceMediaPosition(mediaBase, controller)
            // Guard against a device that never reports progress (e.g. stalled route).
            if (queued < lastQueued) {
                lastQueued = queued
                stalledMs = 0L
            } else {
                stalledMs += DRAIN_POLL_MS
                if (stalledMs >= DRAIN_STALL_TIMEOUT_MS) {
                    log.warn { "device drain stalled with $queued frames queued; finishing anyway" }
                    return true
                }
            }
            delay(DRAIN_POLL_MS.milliseconds)
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
        // Same generation: the feed loop parks on _transportPaused and resume()
        // wakes it. An in-flight play() sees wantPlaying=false and stays paused.
        setWantPlaying(false)
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
        setWantPlaying(true)
        _transportPaused.value = false
        // A parked feed loop simply wakes up. Only start a new generation when the
        // loop has ended (e.g. EOS closed it) so two loops never feed at once.
        runAndUpdateState(State.Playing) {
            if (useTransportPlayback() && transport != null) {
                onTransportResumed()
                val t = transport
                val format = transportFormat
                if (t != null && playbackJob?.isActive != true && format != null) {
                    val epoch = beginPlay()
                    playbackJob = scope.launch {
                        runCatching {
                            // Device may have been closed at EOS.
                            ensureDeviceReadyForTransport(format)
                            t.prime()
                            runTransportFeedLoop(epoch, t)
                        }.onFailure {
                            if (it is CancellationException) throw it
                            if (endPlay(epoch)) {
                                log.error(it) { "resume feed failed: ${it.message}" }
                                _state.value = State.Error(it)
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
        abandonPlay()
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

    /** Stops playback and cancels [scope]; the session is unusable afterwards. */
    override fun release() {
        stop()
        scope.cancel()
    }

    private fun applyPendingSeek(controller: TransportController) {
        val pending = pendingSeekPosition ?: return
        pendingSeekPosition = null
        val result = controller.seekTo(pending)
        if (result.isApplied) {
            mediaBase = pending
            _position.value = pending
            onTransportSeekApplied(pending)
            log.debug { "applied pending seek $pending" }
        } else {
            log.debug { "pending seek $pending rejected after transport start" }
        }
    }

    private fun discardTransport() {
        discardTransportBuffersOnly()
        _position.value = Duration.ZERO
        _bufferedRange.value = BufferedRange.Empty
        mediaBase = Duration.ZERO
        convertedKnownByteCount = null
        pendingSeekPosition = null
    }

    private fun discardTransportBuffersOnly() {
        rangePublisherJob?.cancel()
        rangePublisherJob = null
        transport = null
        transportFormat = null
        transportSink = null
    }

    /** Generation [epoch] is current and the user still wants playback. */
    private fun isCurrentPlay(epoch: Int): Boolean =
        intent.value == PlayIntent(epoch, wantPlaying = true)

    /** Generation [epoch] has not been replaced by a newer play()/stop(). */
    private fun isCurrentGeneration(epoch: Int): Boolean = intent.value.epoch == epoch

    private fun ensurePausedIfStillDesired() {
        if (intent.value.wantPlaying) return
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

    private companion object {
        const val DRAIN_POLL_MS = 10L
        const val DRAIN_STALL_TIMEOUT_MS = 3_000L
    }
}
