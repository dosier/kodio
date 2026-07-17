package space.kodio.core

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import space.kodio.core.io.collectAsSource
import space.kodio.core.io.convertAudio
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.util.namedLogger
import kotlin.time.Duration

private val logger = namedLogger("NativeMacosPlayback")

/**
 * Native macOS playback session implementation using Panama FFI.
 *
 * Implements [AudioPlaybackSession] directly rather than extending [BaseAudioPlaybackSession]
 * because the native layer is callback/upcall-driven and owns state transitions via FFI.
 * That model is incompatible with the base class pull-loop contract and would create dual
 * state ownership, risking the SIGSEGV/lifecycle issues this code was written to avoid.
 *
 * State transitions are managed on the JVM side. A background coroutine on [Dispatchers.IO]
 * awaits the native terminal state via a blocking downcall, avoiding Panama upcalls from
 * Kotlin/Native GCD threads (which cause SIGSEGV due to JVM stack-walking failures).
 *
 * Seek reloads a frame-aligned PCM slice into the native buffer (full PCM stays cached
 * on the JVM side) so scrubbing does not go through Compose [AudioRecording.fromPcmBytes].
 */
internal class NativeMacosAudioPlaybackSession(
    private val nativeMemSeq: MemorySegment,
) : AudioPlaybackSession {

    private val _state = MutableStateFlow<AudioPlaybackSession.State>(AudioPlaybackSession.State.Idle)
    override val state: StateFlow<AudioPlaybackSession.State> = _state.asStateFlow()

    private val _audioFlowHolder = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlowHolder.asStateFlow()

    private var runtimeArena: Arena? = null
    private var loaded = false
    private var completionJob: Job? = null
    private val completionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Full normalized PCM retained for in-session seek slices. */
    private var fullPcm: ByteArray? = null
    private var playbackFormat: AudioFormat? = null
    private var encodedFormat: ByteArray? = null

    /** Mirrors [BaseAudioPlaybackSession] play/pause sequencing. */
    private val wantPlaying = MutableStateFlow(false)
    private val playEpoch = MutableStateFlow(0)

    /**
     * The native library only exposes a whole-buffer
     * [NativeMacosLib.macos_playback_session_load] entry point.
     *
     * Known-finite flows ([AudioFlow.isKnownFinite]) are collected once into
     * [fullPcm] for seek slices. Live/unbounded flows are never drained —
     * load stays Ready without native buffers; [play] no-ops and [seekTo]
     * returns [SeekResult.Unsupported] until a finite recording is loaded.
     */
    override suspend fun load(audioFlow: AudioFlow) {
        _audioFlowHolder.value = audioFlow

        if (!audioFlow.isKnownFinite) {
            logger.info {
                "load(): skipping full collect for non-finite AudioFlow " +
                    "(native playback requires knownByteCount)"
            }
            fullPcm = null
            playbackFormat = null
            encodedFormat = null
            loaded = false
            closePreviousArena()
            _state.value = AudioPlaybackSession.State.Ready
            return
        }

        val nativeFlow = normalizeForPlayback(audioFlow)
        val formatData = nativeFlow.format.encodeToByteArray()
        val audioSource = nativeFlow.collectAsSource()
        val audioData = audioSource.source.readByteArray()

        fullPcm = audioData
        playbackFormat = nativeFlow.format
        encodedFormat = formatData

        loadNativeBuffer(formatData, audioData)
        loaded = true
        _state.value = AudioPlaybackSession.State.Ready
    }

    private fun loadNativeBuffer(formatData: ByteArray, audioData: ByteArray) {
        closePreviousArena()
        val arena = Arena.ofShared().also { runtimeArena = it }

        val formatDataSeq = arena.allocate(formatData.size.toLong())
        formatDataSeq.asSlice(0, formatData.size.toLong()).copyFrom(MemorySegment.ofArray(formatData))

        val audioDataSeq = arena.allocate(audioData.size.toLong())
        audioDataSeq.asSlice(0, audioData.size.toLong()).copyFrom(MemorySegment.ofArray(audioData))

        NativeMacosLib.macos_playback_session_load.invokeExact(
            nativeMemSeq,
            formatData.size,
            formatDataSeq,
            audioData.size,
            audioDataSeq
        )
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
        if (!loaded) return

        val epoch = playEpoch.value + 1
        playEpoch.value = epoch
        wantPlaying.value = true

        completionJob?.cancel()
        completionJob = null

        if (!isCurrentPlay(epoch)) {
            ensurePausedIfStillDesired()
            return
        }

        _state.value = AudioPlaybackSession.State.Playing

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
        }
    }

    override fun pause() {
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Paused
    }

    override fun resume() {
        wantPlaying.value = true
        NativeMacosLib.macos_playback_session_resume.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun stop() {
        wantPlaying.value = false
        playEpoch.value = playEpoch.value + 1
        completionJob?.cancel()
        completionJob = null
        NativeMacosLib.macos_playback_session_stop.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Idle
        loaded = false
        fullPcm = null
        playbackFormat = null
        encodedFormat = null
        closePreviousArena()
    }

    /**
     * Reloads a frame-aligned tail of the cached PCM into the native session.
     * If transport was playing, restarts native play from the new slice.
     */
    override fun seekTo(position: Duration): SeekResult {
        val pcm = fullPcm ?: return SeekResult.Unsupported
        val format = playbackFormat ?: return SeekResult.Unsupported
        val formatData = encodedFormat ?: return SeekResult.Unsupported
        if (!loaded) return SeekResult.Unsupported

        val offset = pcmSeekByteOffset(position, format, pcm.size)
        logger.debug { "seekTo(position=$position) -> byteOffset=$offset / ${pcm.size}" }

        val wasPlaying = _state.value is AudioPlaybackSession.State.Playing
        val wasPaused = _state.value is AudioPlaybackSession.State.Paused

        completionJob?.cancel()
        completionJob = null
        playEpoch.value = playEpoch.value + 1
        NativeMacosLib.macos_playback_session_stop.invokeExact(nativeMemSeq)

        val slice = if (offset <= 0) pcm else if (offset >= pcm.size) ByteArray(0) else {
            pcm.copyOfRange(offset, pcm.size)
        }
        loadNativeBuffer(formatData, slice)
        loaded = true

        when {
            wasPlaying -> {
                wantPlaying.value = true
                val epoch = playEpoch.value + 1
                playEpoch.value = epoch
                _state.value = AudioPlaybackSession.State.Playing
                NativeMacosLib.macos_playback_session_play.invokeExact(nativeMemSeq)
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
                }
            }
            wasPaused -> {
                wantPlaying.value = false
                _state.value = AudioPlaybackSession.State.Paused
            }
            else -> {
                wantPlaying.value = false
                _state.value = AudioPlaybackSession.State.Ready
            }
        }
        return SeekResult.Applied
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
