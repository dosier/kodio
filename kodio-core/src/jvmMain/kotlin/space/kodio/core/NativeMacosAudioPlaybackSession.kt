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

    /**
     * Streaming playback (feeding audio incrementally) is deferred. The native
     * library only exposes a whole-buffer [NativeMacosLib.macos_playback_session_load]
     * entry point, so streaming would require a new native enqueue export and
     * rebuilding kodio-native, which is out of scope. This method therefore
     * collects the full recording into a single buffer by design.
     */
    override suspend fun load(audioFlow: AudioFlow) {
        _audioFlowHolder.value = audioFlow

        closePreviousArena()
        val arena = Arena.ofShared().also { runtimeArena = it }

        val nativeFlow = normalizeForPlayback(audioFlow)

        val formatData = nativeFlow.format.encodeToByteArray()
        val formatDataSeq = arena.allocate(formatData.size.toLong())
        formatDataSeq.asSlice(0, formatData.size.toLong()).copyFrom(MemorySegment.ofArray(formatData))

        val audioSource = nativeFlow.collectAsSource()
        val audioData = audioSource.source.readByteArray()
        val audioDataSeq = arena.allocate(audioData.size.toLong())
        audioDataSeq.asSlice(0, audioData.size.toLong()).copyFrom(MemorySegment.ofArray(audioData))

        NativeMacosLib.macos_playback_session_load.invokeExact(
            nativeMemSeq,
            formatData.size,
            formatDataSeq,
            audioData.size,
            audioDataSeq
        )

        loaded = true
        _state.value = AudioPlaybackSession.State.Ready
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

        _state.value = AudioPlaybackSession.State.Playing

        NativeMacosLib.macos_playback_session_play.invokeExact(nativeMemSeq)

        completionJob = completionScope.launch {
            val result = NativeMacosLib.macos_playback_session_await_completion
                .invokeExact(nativeMemSeq) as Int
            ensureActive()
            _state.value = when (result) {
                0 -> AudioPlaybackSession.State.Finished
                1 -> AudioPlaybackSession.State.Error(RuntimeException("Native playback error"))
                else -> AudioPlaybackSession.State.Idle
            }
        }
    }

    override fun pause() {
        NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Paused
    }

    override fun resume() {
        NativeMacosLib.macos_playback_session_resume.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun stop() {
        completionJob?.cancel()
        NativeMacosLib.macos_playback_session_stop.invokeExact(nativeMemSeq)
        _state.value = AudioPlaybackSession.State.Idle
        loaded = false
    }

    private fun closePreviousArena() {
        runtimeArena?.close()
        runtimeArena = null
    }
}
