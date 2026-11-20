package space.kodio.core

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kodio.core.io.convertAudio
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.util.namedLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

private val logger = namedLogger("NativeMacosPlayback")

/**
 * Panama FFI wrapper around the K/N [MacosAudioPlaybackSession] transport.
 *
 * Streams PCM via begin/append/end so long recordings are not fully retained on
 * the JVM heap (native append copies bytes; JVM arenas are confined per call).
 * Seek delegates to native transport seek (AudioQueue quiesce+Stop);
 * no Kotlin upcalls from GCD threads.
 */
internal class NativeMacosAudioPlaybackSession(
    private val nativeMemSeq: MemorySegment,
) : AudioPlaybackSession {

    private val _state = MutableStateFlow<AudioPlaybackSession.State>(AudioPlaybackSession.State.Idle)
    override val state: StateFlow<AudioPlaybackSession.State> = _state.asStateFlow()

    private val _audioFlowHolder = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlowHolder.asStateFlow()

    private val _position = MutableStateFlow(Duration.ZERO)
    override val position: StateFlow<Duration> = _position.asStateFlow()

    private val _bufferedRange = MutableStateFlow(BufferedRange.Empty)
    override val bufferedRange: StateFlow<BufferedRange> = _bufferedRange.asStateFlow()

    override val supportsTransportSeek: Boolean get() = true

    private var streamPrepared = false
    private var ingestComplete = false

    /** Set when ingest ended with an error; the native stream was ended early. */
    @Volatile
    private var ingestFailure: Throwable? = null
    private var completionJob: Job? = null
    private var ingestJob: Job? = null
    private var positionJob: Job? = null
    private val completionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Play generation + user intent in one atomic value (same model as
     * [BaseAudioPlaybackSession]). Pause/resume only flip [PlayIntent.wantPlaying];
     * the epoch changes on play/stop so a completion watcher stays valid across
     * pause/resume and can never clear the intent of a newer play().
     */
    private data class PlayIntent(val epoch: Int, val wantPlaying: Boolean)

    private val intent = MutableStateFlow(PlayIntent(epoch = 0, wantPlaying = false))

    private fun beginPlay(): Int =
        intent.updateAndGet { PlayIntent(epoch = it.epoch + 1, wantPlaying = true) }.epoch

    private fun abandonPlay() {
        intent.update { PlayIntent(epoch = it.epoch + 1, wantPlaying = false) }
    }

    private fun setWantPlaying(want: Boolean) {
        intent.update { it.copy(wantPlaying = want) }
    }

    /** Atomically ends generation [epoch] if it is still current. */
    private fun endPlay(epoch: Int): Boolean {
        while (true) {
            val current = intent.value
            if (current.epoch != epoch) return false
            if (intent.compareAndSet(current, current.copy(wantPlaying = false))) return true
        }
    }

    override suspend fun load(audioFlow: AudioFlow) {
        _audioFlowHolder.value = audioFlow
        streamPrepared = false
        ingestComplete = false
        ingestFailure = null
        _position.value = Duration.ZERO
        _bufferedRange.value = BufferedRange.Empty
        _state.value = AudioPlaybackSession.State.Ready
        logger.info {
            "load(): deferred stream prepare format=${audioFlow.format} " +
                "knownByteCount=${audioFlow.knownByteCount}"
        }
    }

    /**
     * Match [MacosAudioPlaybackSession] device-friendly formats so Panama does not
     * force PCM24 → float (native AudioQueue accepts packed LE PCM24/32 and float).
     */
    private fun normalizeForPlayback(audioFlow: AudioFlow): AudioFlow {
        val fmt = audioFlow.format
        val enc = fmt.encoding
        val needsConversion = when (enc) {
            is SampleEncoding.PcmFloat ->
                enc.layout != SampleLayout.Interleaved
            is SampleEncoding.PcmInt ->
                !enc.signed ||
                    !enc.packed ||
                    enc.endianness != Endianness.Little ||
                    enc.layout != SampleLayout.Interleaved ||
                    (enc.bitDepth != IntBitDepth.Sixteen &&
                        enc.bitDepth != IntBitDepth.TwentyFour &&
                        enc.bitDepth != IntBitDepth.ThirtyTwo)
        }
        if (!needsConversion) {
            logger.debug { "normalizeForPlayback: passthrough $fmt" }
            return audioFlow
        }
        val targetFormat = AudioFormat(
            sampleRate = fmt.sampleRate,
            channels = fmt.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
        logger.info { "Normalizing playback format: $fmt -> $targetFormat" }
        return audioFlow.convertAudio(targetFormat)
    }

    override suspend fun play() {
        val source = _audioFlowHolder.value ?: return

        val epoch = beginPlay()

        completionJob?.cancel()
        completionJob = null

        if (!isCurrentPlay(epoch)) {
            ensurePausedIfStillDesired()
            return
        }

        // Retained native transport buffer: do not re-begin / re-ingest a drained cold flow.
        if (streamPrepared && ingestComplete) {
            replayFromRetainedBuffer(epoch)
            return
        }

        ingestJob?.cancel()
        ingestJob = null

        val normalized = normalizeForPlayback(source)
        // Pre-play seek may already have opened the native stream; do not re-begin
        // (that would wipe a pending cursor / reload a fresh AudioFlow).
        if (!streamPrepared) {
            beginNativeStream(normalized)
            streamPrepared = true
            ingestComplete = false
        }

        if (!isCurrentPlay(epoch)) {
            ensurePausedIfStillDesired()
            return
        }

        _state.value = AudioPlaybackSession.State.Playing
        startPositionPolling()

        // Ingest on IO: never materialize the whole flow on the JVM heap.
        ingestFailure = null
        ingestJob = completionScope.launch {
            var chunks = 0
            var bytesFed = 0L
            runCatching {
                normalized.collect { chunk ->
                    ensureActive()
                    appendNative(chunk)
                    chunks++
                    bytesFed += chunk.size
                    if (chunks == 1 || chunks % 32 == 0) {
                        logger.debug {
                            "ingest progress chunks=$chunks bytesFed=$bytesFed " +
                                "lastChunk=${chunk.size}"
                        }
                    }
                }
                endNativeStream()
                ingestComplete = true
                val known = normalized.knownByteCount
                logger.info {
                    "native ingest complete chunks=$chunks bytesFed=$bytesFed " +
                        "knownByteCount=$known format=${normalized.format}"
                }
                if (known != null && bytesFed != known) {
                    logger.warn {
                        "ingest bytesFed=$bytesFed diverges from knownByteCount=$known " +
                            "(delta=${bytesFed - known})"
                    }
                }
            }.onFailure {
                if (it is CancellationException) return@onFailure
                logger.error(it) { "native ingest failed after chunks=$chunks bytesFed=$bytesFed: ${it.message}" }
                // End the native stream so the already-fed PCM plays out and
                // await_completion returns; otherwise playback waits forever.
                ingestFailure = it
                runCatching { endNativeStream() }
                    .onFailure { e -> logger.warn(e) { "endNativeStream after ingest failure failed" } }
            }
        }

        logger.debug { "calling native play() epoch=$epoch" }
        NativeMacosLib.macos_playback_session_play.invokeExact(nativeMemSeq)

        if (!isCurrentPlay(epoch)) {
            NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
            ensurePausedIfStillDesired()
            return
        }

        watchCompletion(epoch)
    }

    private fun replayFromRetainedBuffer(epoch: Int) {
        _state.value = AudioPlaybackSession.State.Playing
        startPositionPolling()
        NativeMacosLib.macos_playback_session_play.invokeExact(nativeMemSeq)
        if (!isCurrentPlay(epoch)) {
            NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
            ensurePausedIfStillDesired()
            return
        }
        watchCompletion(epoch)
        logger.debug { "replayFromRetainedBuffer epoch=$epoch" }
    }

    private fun watchCompletion(epoch: Int) {
        completionJob = completionScope.launch {
            logger.debug { "await_completion started epoch=$epoch" }
            val result = NativeMacosLib.macos_playback_session_await_completion
                .invokeExact(nativeMemSeq) as Int
            ensureActive()
            // Generation check only: a pause/resume in between keeps the epoch.
            if (!endPlay(epoch)) {
                logger.debug { "await_completion ignored stale epoch=$epoch result=$result" }
                return@launch
            }
            val resultMeaning = when (result) {
                0 -> "Finished"
                1 -> "Error"
                2 -> "Idle"
                else -> "Unknown($result)"
            }
            val failure = ingestFailure
            _state.value = when (result) {
                0 if failure != null -> {
                    // Truncated stream: not replayable, re-begin on next play.
                    streamPrepared = false
                    ingestComplete = false
                    AudioPlaybackSession.State.Error(failure)
                }
                0 -> AudioPlaybackSession.State.Finished
                1 -> {
                    // Force re-begin on next play so a closed ingest channel is replaced.
                    streamPrepared = false
                    ingestComplete = false
                    val detail = readNativeLastError()
                    AudioPlaybackSession.State.Error(
                        RuntimeException(
                            if (detail != null) "Native playback error: $detail"
                            else "Native playback error",
                        ),
                    )
                }
                else -> AudioPlaybackSession.State.Idle
            }
            logger.info {
                "native playback terminal result=$result ($resultMeaning) state=${_state.value} " +
                    "ingestComplete=$ingestComplete position=${_position.value} " +
                    "knownByteCount=${_audioFlowHolder.value?.knownByteCount}"
            }
            stopPositionPolling()
            pollTransportClocks()
        }
    }

    private fun readNativeLastError(): String? =
        Arena.ofConfined().use { arena ->
            val sizeSeg = arena.allocate(ValueLayout.JAVA_LONG)
            val ptr = NativeMacosLib.macos_playback_session_last_error
                .invokeExact(nativeMemSeq, sizeSeg) as MemorySegment
            val size = sizeSeg.get(ValueLayout.JAVA_LONG, 0)
            if (ptr.address() == 0L || size <= 0L) return@use null
            try {
                val bytes = ByteArray(size.toInt())
                MemorySegment.ofArray(bytes)
                    .copyFrom(ptr.reinterpret(size).asSlice(0, size))
                bytes.decodeToString()
            } finally {
                run { NativeMacosLib.macos_free.invokeExact(ptr) }
            }
        }

    override fun pause() {
        // Same generation: the completion watcher must survive pause/resume.
        setWantPlaying(false)
        NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Paused
        stopPositionPolling()
    }

    override fun resume() {
        // After Finished (+ seek → Paused) no completion watcher is running:
        // restart from the retained native buffer so Finished is reported again.
        if (completionJob?.isActive != true && streamPrepared && ingestComplete) {
            replayFromRetainedBuffer(beginPlay())
            return
        }
        setWantPlaying(true)
        NativeMacosLib.macos_playback_session_resume.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Playing
        startPositionPolling()
    }

    override fun stop() {
        abandonPlay()
        completionJob?.cancel()
        completionJob = null
        ingestJob?.cancel()
        ingestJob = null
        stopPositionPolling()
        NativeMacosLib.macos_playback_session_stop.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Idle
        streamPrepared = false
        ingestComplete = false
        ingestFailure = null
        _position.value = Duration.ZERO
        _bufferedRange.value = BufferedRange.Empty
    }

    override fun release() {
        stop()
        completionScope.cancel()
    }

    override fun seekTo(position: Duration): SeekResult {
        val source = _audioFlowHolder.value ?: return SeekResult.Unsupported
        if (!streamPrepared) {
            // Pre-play seek: open the native stream so pending-cursor seeks work
            // for known-finite content (ingest starts on play()).
            beginNativeStream(normalizeForPlayback(source))
            streamPrepared = true
            ingestComplete = false
        }
        val code = NativeMacosLib.macos_playback_session_seek.invokeExact(
            nativeMemSeq,
            position.inWholeNanoseconds,
        ) as Int
        val result = if (code == 1) SeekResult.Applied else SeekResult.Unsupported
        if (result.isApplied) {
            _position.value = position
            when (_state.value) {
                is AudioPlaybackSession.State.Finished -> {
                    setWantPlaying(false)
                    _state.value = AudioPlaybackSession.State.Paused
                }
                else -> Unit
            }
            pollTransportClocks()
        }
        logger.debug { "seekTo($position) -> $result" }
        return result
    }

    private fun beginNativeStream(audioFlow: AudioFlow) {
        val fmt = audioFlow.format
        logger.info {
            "begin_stream format=$fmt sampleRate=${fmt.sampleRate} channels=${fmt.channels} " +
                "encoding=${fmt.encoding} bpf=${fmt.bytesPerFrame} " +
                "knownByteCount=${audioFlow.knownByteCount}"
        }
        // Native bridge copies format bytes synchronously; confine arena to this call.
        Arena.ofConfined().use { arena ->
            val formatData = audioFlow.format.encodeToByteArray()
            val formatDataSeq = arena.allocate(formatData.size.toLong())
            formatDataSeq.asSlice(0, formatData.size.toLong()).copyFrom(MemorySegment.ofArray(formatData))
            val known = audioFlow.knownByteCount ?: -1L
            // invokeExact must be a statement (void call site). As the last
            // expression of this lambda it would be typed as returning Object and
            // throw WrongMethodTypeException against the void downcall handle.
            NativeMacosLib.macos_playback_session_begin_stream.invokeExact(
                nativeMemSeq,
                formatData.size,
                formatDataSeq,
                known,
            )
            Unit
        }
    }

    private fun appendNative(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        // Native append copies via readBytes and blocks until the ingest channel
        // accepts the chunk (no drop under back-pressure).
        Arena.ofConfined().use { arena ->
            val dataSeq = arena.allocate(chunk.size.toLong())
            dataSeq.asSlice(0, chunk.size.toLong()).copyFrom(MemorySegment.ofArray(chunk))
            val ok = NativeMacosLib.macos_playback_session_append.invokeExact(
                nativeMemSeq,
                chunk.size,
                dataSeq,
            ) as Int
            if (ok != 1) {
                logger.warn { "appendNative rejected ${chunk.size} bytes (ingest channel closed)" }
            }
        }
    }

    private fun endNativeStream() {
        logger.debug { "endNativeStream()" }
        NativeMacosLib.macos_playback_session_end_stream.invokeExact(nativeMemSeq)
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionJob = completionScope.launch {
            while (isActive && intent.value.wantPlaying) {
                pollTransportClocks()
                delay(50.milliseconds)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun pollTransportClocks() {
        runCatching {
            val posNs = NativeMacosLib.macos_playback_session_position_ns
                .invokeExact(nativeMemSeq) as Long
            _position.value = posNs.nanoseconds

            Arena.ofConfined().use { arena ->
                val startSeg = arena.allocate(ValueLayout.JAVA_LONG)
                val endSeg = arena.allocate(ValueLayout.JAVA_LONG)
                val nonempty = NativeMacosLib.macos_playback_session_buffered_range_ns.invokeExact(
                    nativeMemSeq,
                    startSeg,
                    endSeg,
                ) as Int
                if (nonempty == 1) {
                    val start = startSeg.get(ValueLayout.JAVA_LONG, 0).nanoseconds
                    val end = endSeg.get(ValueLayout.JAVA_LONG, 0).nanoseconds
                    _bufferedRange.value = BufferedRange(start, end)
                }
            }
        }.onFailure {
            logger.trace { "pollTransportClocks failed: ${it.message}" }
        }
    }

    private fun isCurrentPlay(epoch: Int): Boolean =
        intent.value == PlayIntent(epoch, wantPlaying = true)

    private fun ensurePausedIfStillDesired() {
        if (intent.value.wantPlaying) return
        when (_state.value) {
            is AudioPlaybackSession.State.Idle,
            is AudioPlaybackSession.State.Finished,
            is AudioPlaybackSession.State.Error -> return
            else -> {
                NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
                _state.value = AudioPlaybackSession.State.Paused
            }
        }
    }
}
