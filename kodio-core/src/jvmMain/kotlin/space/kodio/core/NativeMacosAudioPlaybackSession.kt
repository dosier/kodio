package space.kodio.core

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import space.kodio.core.io.convertAudio
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.util.namedLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

private val logger = namedLogger("NativeMacosPlayback")

/**
 * Panama FFI wrapper around the K/N [MacosAudioPlaybackSession] transport.
 *
 * Streams PCM via begin/append/end so long recordings are not fully retained on
 * the JVM heap. Seek delegates to native transport seek (AudioQueue quiesce+Stop);
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

    private var runtimeArena: Arena? = null
    private var streamPrepared = false
    private var completionJob: Job? = null
    private var ingestJob: Job? = null
    private var positionJob: Job? = null
    private val completionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val wantPlaying = MutableStateFlow(false)
    private val playEpoch = MutableStateFlow(0)

    override suspend fun load(audioFlow: AudioFlow) {
        _audioFlowHolder.value = audioFlow
        streamPrepared = false
        closePreviousArena()
        _position.value = Duration.ZERO
        _bufferedRange.value = BufferedRange.Empty
        _state.value = AudioPlaybackSession.State.Ready
        logger.info {
            "load(): deferred stream prepare format=${audioFlow.format} " +
                "knownByteCount=${audioFlow.knownByteCount}"
        }
    }

    private fun normalizeForPlayback(audioFlow: AudioFlow): AudioFlow {
        val fmt = audioFlow.format
        val enc = fmt.encoding
        val needsConversion = when (enc) {
            is SampleEncoding.PcmFloat -> false
            is SampleEncoding.PcmInt ->
                enc.bitDepth != IntBitDepth.Sixteen ||
                    !enc.signed ||
                    enc.endianness != Endianness.Little
        }
        if (!needsConversion) return audioFlow
        val targetFormat = AudioFormat(
            sampleRate = fmt.sampleRate,
            channels = fmt.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
        logger.debug { "Normalizing playback format: $fmt -> $targetFormat" }
        return audioFlow.convertAudio(targetFormat)
    }

    override suspend fun play() {
        val source = _audioFlowHolder.value ?: return

        val epoch = playEpoch.value + 1
        playEpoch.value = epoch
        wantPlaying.value = true

        completionJob?.cancel()
        completionJob = null
        ingestJob?.cancel()
        ingestJob = null

        if (!isCurrentPlay(epoch)) {
            ensurePausedIfStillDesired()
            return
        }

        val normalized = normalizeForPlayback(source)
        beginNativeStream(normalized)
        streamPrepared = true

        if (!isCurrentPlay(epoch)) {
            ensurePausedIfStillDesired()
            return
        }

        _state.value = AudioPlaybackSession.State.Playing
        startPositionPolling()

        // Ingest on IO: never materialize the whole flow on the JVM heap.
        ingestJob = completionScope.launch {
            runCatching {
                normalized.collect { chunk ->
                    ensureActive()
                    appendNative(chunk)
                }
                endNativeStream()
            }.onFailure {
                logger.error(it) { "native ingest failed: ${it.message}" }
            }
        }

        NativeMacosLib.macos_playback_session_play.invokeExact(nativeMemSeq)

        if (!isCurrentPlay(epoch)) {
            NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
            ensurePausedIfStillDesired()
            return
        }

        completionJob = completionScope.launch {
            val result = NativeMacosLib.macos_playback_session_await_completion
                .invokeExact(nativeMemSeq) as Int
            ensureActive()
            if (!isCurrentPlay(epoch)) return@launch
            _state.value = when (result) {
                0 -> AudioPlaybackSession.State.Finished
                1 -> AudioPlaybackSession.State.Error(RuntimeException("Native playback error"))
                else -> AudioPlaybackSession.State.Idle
            }
            if (result == 0) wantPlaying.value = false
            stopPositionPolling()
            pollTransportClocks()
        }
    }

    override fun pause() {
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Paused
        stopPositionPolling()
    }

    override fun resume() {
        wantPlaying.value = true
        NativeMacosLib.macos_playback_session_resume.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Playing
        startPositionPolling()
    }

    override fun stop() {
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        completionJob?.cancel()
        completionJob = null
        ingestJob?.cancel()
        ingestJob = null
        stopPositionPolling()
        NativeMacosLib.macos_playback_session_stop.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Idle
        streamPrepared = false
        _position.value = Duration.ZERO
        _bufferedRange.value = BufferedRange.Empty
        closePreviousArena()
    }

    override fun seekTo(position: Duration): SeekResult {
        val source = _audioFlowHolder.value ?: return SeekResult.Unsupported
        if (!streamPrepared) {
            // Pre-play seek: open the native stream so pending-cursor seeks work
            // for known-finite content (ingest starts on play()).
            runBlocking {
                val normalized = normalizeForPlayback(source)
                beginNativeStream(normalized)
                streamPrepared = true
            }
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
                    wantPlaying.value = false
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
        closePreviousArena()
        val arena = Arena.ofShared().also { runtimeArena = it }
        val formatData = audioFlow.format.encodeToByteArray()
        val formatDataSeq = arena.allocate(formatData.size.toLong())
        formatDataSeq.asSlice(0, formatData.size.toLong()).copyFrom(MemorySegment.ofArray(formatData))
        val known = audioFlow.knownByteCount ?: -1L
        NativeMacosLib.macos_playback_session_begin_stream.invokeExact(
            nativeMemSeq,
            formatData.size,
            formatDataSeq,
            known,
        )
    }

    private fun appendNative(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        val arena = runtimeArena ?: return
        // Short-lived segments for each chunk (arena grows; closed on stop/reload).
        val dataSeq = arena.allocate(chunk.size.toLong())
        dataSeq.asSlice(0, chunk.size.toLong()).copyFrom(MemorySegment.ofArray(chunk))
        var attempts = 0
        while (attempts++ < 64) {
            val ok = NativeMacosLib.macos_playback_session_append.invokeExact(
                nativeMemSeq,
                chunk.size,
                dataSeq,
            ) as Int
            if (ok == 1) return
            Thread.sleep(1) // back-pressure when native ingest channel is full
        }
        logger.warn { "appendNative dropped ${chunk.size} bytes after back-pressure retries" }
    }

    private fun endNativeStream() {
        NativeMacosLib.macos_playback_session_end_stream.invokeExact(nativeMemSeq)
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionJob = completionScope.launch {
            while (isActive && wantPlaying.value) {
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

            val arena = Arena.ofConfined()
            try {
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
            } finally {
                arena.close()
            }
        }.onFailure {
            logger.trace { "pollTransportClocks failed: ${it.message}" }
        }
    }

    private fun isCurrentPlay(epoch: Int): Boolean =
        wantPlaying.value && playEpoch.value == epoch

    private fun ensurePausedIfStillDesired() {
        if (wantPlaying.value) return
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

    private fun closePreviousArena() {
        runtimeArena?.close()
        runtimeArena = null
    }
}
