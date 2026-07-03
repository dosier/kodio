package space.kodio.core

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import space.kodio.core.io.decodeAsAudioFormat
import space.kodio.core.io.decodeAsAudioRecordingState
import space.kodio.core.util.namedLogger

private val logger = namedLogger("NativeMacosRecording")

/**
 * Native macOS recording session implementation using Panama FFI.
 *
 * Implements [AudioRecordingSession] directly rather than extending [BaseAudioRecordingSession]
 * because the native layer is callback/upcall-driven and owns state transitions via the onState
 * FFI callback. That model is incompatible with the base class pull-loop startRecording(channel)
 * contract and would create dual state ownership, risking the SIGSEGV/lifecycle issues this code
 * was written to avoid.
 */
internal class NativeMacosAudioRecordingSession(
    private val nativeMemSeq: MemorySegment,
) : AudioRecordingSession {

    private val _state = MutableStateFlow<AudioRecordingSession.State>(AudioRecordingSession.State.Idle)
    override val state: StateFlow<AudioRecordingSession.State> = _state.asStateFlow()

    private var _audioShared = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    private val recordedChunks = CopyOnWriteArrayList<ByteArray>()
    private val _audioFlowHolder = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlowHolder.asStateFlow()

    // Mutable so it can be recreated after reset
    private var formatDeferred = CompletableDeferred<AudioFormat>()

    companion object {
        private val IDX = AtomicLong(1L)
        private val SESSIONS = ConcurrentHashMap<Long, NativeMacosAudioRecordingSession>()

        @JvmStatic
        fun onState(ctx: MemorySegment?, data: MemorySegment?, len: Int) {
            if (ctx == null || ctx.address() == 0L) return
            val ctx8 = ctx.reinterpret(java.lang.Long.BYTES.toLong())
            val id = ctx8.get(ValueLayout.JAVA_LONG, 0)

            val sess = SESSIONS[id] ?: return
            if (data == null || len <= 0) return

            val dseg = data.reinterpret(len.toLong())
            val bytes = ByteArray(len)
            dseg.asByteBuffer().get(bytes)

            sess._state.value = bytes.decodeAsAudioRecordingState()
        }

        @JvmStatic
        fun onFormat(ctx: MemorySegment?, data: MemorySegment?, len: Int) {
            if (ctx == null || ctx.address() == 0L) return
            val id = ctx.reinterpret(8).get(ValueLayout.JAVA_LONG, 0)

            val sess = SESSIONS[id] ?: return
            if (data == null || len <= 0) return

            val dseg = data.reinterpret(len.toLong())
            val bytes = ByteArray(len)
            dseg.asByteBuffer().get(bytes)

            val fmt = bytes.decodeAsAudioFormat()
            if (!sess.formatDeferred.isCompleted) sess.formatDeferred.complete(fmt)
        }

        @JvmStatic
        fun onAudio(ctx: MemorySegment?, data: MemorySegment?, len: Int) {
            if (ctx == null || ctx.address() == 0L) return
            val id = ctx.reinterpret(8).get(ValueLayout.JAVA_LONG, 0)

            val sess = SESSIONS[id] ?: return
            if (data == null || len <= 0) return

            val dseg = data.reinterpret(len.toLong())
            val bytes = ByteArray(len)
            dseg.asByteBuffer().get(bytes)

            sess.recordedChunks.add(bytes)
            // recordedChunks is the source of truth for the finished recording; tryEmit is for live consumers only.
            if (!sess._audioShared.tryEmit(bytes))
                logger.warn { "Failed to emit ${bytes.size} bytes" }
        }
    }

    private var runtimeArena: Arena? = null
    private var ctxSeg: MemorySegment? = null
    private var stateCbStub: MemorySegment? = null
    private var formatCbStub: MemorySegment? = null
    private var audioCbStub: MemorySegment? = null
    private var id: Long = 0L
    private var started = false

    override suspend fun start() {
        logger.debug { "start() called, started=$started" }
        if (started) {
            logger.debug { "Already started, returning" }
            return
        }
        started = true

        closePreviousArena()
        val arena = Arena.ofShared().also { runtimeArena = it }
        id = IDX.getAndIncrement()
        SESSIONS[id] = this
        logger.debug { "Created session id=$id" }

        ctxSeg = arena.allocate(ValueLayout.JAVA_LONG).also { it.set(ValueLayout.JAVA_LONG, 0, id) }

        val stateMh = MethodHandles.lookup().findStatic(
            javaClass, "onState",
            MethodType.methodType(
                Void.TYPE,
                MemorySegment::class.java,
                MemorySegment::class.java,
                Int::class.javaPrimitiveType
            )
        )
        val formatMh = MethodHandles.lookup().findStatic(
            javaClass, "onFormat",
            MethodType.methodType(
                Void.TYPE,
                MemorySegment::class.java,
                MemorySegment::class.java,
                Int::class.javaPrimitiveType
            )
        )
        val audioMh = MethodHandles.lookup().findStatic(
            javaClass, "onAudio",
            MethodType.methodType(
                Void.TYPE,
                MemorySegment::class.java,
                MemorySegment::class.java,
                Int::class.javaPrimitiveType
            )
        )

        stateCbStub = NativeMacosLib.linker.upcallStub(stateMh, NativeMacosLib.CB_DESC, arena)
        formatCbStub = NativeMacosLib.linker.upcallStub(formatMh, NativeMacosLib.CB_DESC, arena)
        audioCbStub = NativeMacosLib.linker.upcallStub(audioMh, NativeMacosLib.CB_DESC, arena)

        // Kick off native start; callbacks begin immediately.
        NativeMacosLib.macos_recording_session_start.invokeExact(
            nativeMemSeq, ctxSeg!!, stateCbStub!!, formatCbStub!!, audioCbStub!!
        )

        // Wait briefly for format so we can expose AudioFlow with it
        val fmt: AudioFormat = withTimeout(3_000) { formatDeferred.await() }
        if (_audioFlowHolder.value == null) {
            _audioFlowHolder.value = AudioFlow(
                format = fmt,
                data = _audioShared.asSharedFlow()
            )
        }
    }

    override fun stop() {
        logger.debug { "stop() called, started=$started" }
        if (!started) {
            logger.debug { "Not started, returning" }
            return
        }
        // Remove from SESSIONS first so late native callbacks become no-ops.
        // Do NOT close the arena here — native coroutines may still invoke upcall stubs.
        val old = id
        id = 0L
        if (old != 0L) SESSIONS.remove(old)
        NativeMacosLib.macos_recording_session_stop.invokeExact(nativeMemSeq)
        val format = audioFlow.value?.format
        if (format != null) {
            val coldFlow = AudioFlow(format, recordedChunks.toList().asFlow())
            _audioFlowHolder.value = coldFlow
            logger.debug { "Created cold flow with ${recordedChunks.size} chunks" }
        }
        _state.value = AudioRecordingSession.State.Stopped
        started = false
        logger.debug { "stop() completed, state=${_state.value}" }
    }

    override suspend fun pause() {
        logger.debug { "pause() called, started=$started, state=${_state.value}" }
        if (!started || _state.value !is AudioRecordingSession.State.Recording) {
            logger.debug { "Not in Recording state, ignoring pause()" }
            return
        }
        // Native side: BaseAudioRecordingSession.pause() halts the AudioQueue
        // via AudioQueuePause and flips its own state to Paused; the K/N->JVM
        // state callback also propagates that transition, but we update locally
        // up-front to avoid a UI race window.
        NativeMacosLib.macos_recording_session_pause.invokeExact(nativeMemSeq)
        _state.value = AudioRecordingSession.State.Paused
        logger.debug { "pause() completed, state=${_state.value}" }
    }

    override suspend fun resume() {
        logger.debug { "resume() called, started=$started, state=${_state.value}" }
        if (!started || _state.value !is AudioRecordingSession.State.Paused) {
            logger.debug { "Not in Paused state, ignoring resume()" }
            return
        }
        NativeMacosLib.macos_recording_session_resume.invokeExact(nativeMemSeq)
        _state.value = AudioRecordingSession.State.Recording
        logger.debug { "resume() completed, state=${_state.value}" }
    }

    override fun reset() {
        logger.debug { "reset() called, started=$started, state=${_state.value}" }
        if (started) {
            logger.debug { "Calling native reset" }
            val old = id
            id = 0L
            if (old != 0L) SESSIONS.remove(old)
            NativeMacosLib.macos_recording_session_reset.invokeExact(nativeMemSeq)
            started = false
        }

        // Reset all JVM-side state for next recording
        _state.value = AudioRecordingSession.State.Idle
        _audioFlowHolder.value = null
        recordedChunks.clear()
        _audioShared = MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        formatDeferred = CompletableDeferred()
        logger.debug { "reset() completed, state=${_state.value}" }
    }

    private fun closePreviousArena() {
        runtimeArena?.close()
        runtimeArena = null
        ctxSeg = null
        stateCbStub = null; formatCbStub = null; audioCbStub = null
    }
}
