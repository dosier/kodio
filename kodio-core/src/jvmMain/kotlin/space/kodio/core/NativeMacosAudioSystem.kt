package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.write
import space.kodio.core.io.collectAsSource
import space.kodio.core.io.decodeAsAudioRecordingState
import space.kodio.core.io.decodeAsAudioFormat
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.writeToFile
import space.kodio.core.io.readAudioDevice
import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

fun main() {

    runBlocking {
        val microphone = NativeMacosAudioSystem.listInputDevices()
            .find { it.name.contains("AirPods Max") }
            ?: error("Could not find microphone")

        val recording = NativeMacosAudioSystem.createRecordingSession(microphone)
        recording.start()
        delay(5.seconds)
        recording.stop()

        val flow = recording.audioFlow.value
            ?: error("Audio flow should not be null")

        println(flow.format)
        println(flow.map { it.size }.toList().sum())
        println(flow.collectAsSource().byteCount)

        flow.writeToFile(
            format = AudioFileFormat.Wav,
            path = Path("test_native.wav")
        )
    }
}

private object NativeMacosAudioSystem : SystemAudioSystemImpl() {

    override suspend fun listInputDevices(): List<AudioDevice.Input> =
        listAudioDevice<AudioDevice.Input>(NativeMacosLib.macos_list_input_devices)

    override suspend fun listOutputDevices(): List<AudioDevice.Output> =
        listAudioDevice<AudioDevice.Output>(NativeMacosLib.macos_list_output_devices)

    override suspend fun createRecordingSession(requestedDevice: AudioDevice.Input?): AudioRecordingSession =
        Arena.ofShared().use { arena ->
            val sessionSeq = if (requestedDevice != null) {
                val deviceData = requestedDevice.encodeToByteArray()
                val deviceDataLen = deviceData.size
                val deviceDataSeq = arena.allocate(deviceDataLen.toLong())
                sessionSeqWrite(deviceDataSeq, deviceData)
                NativeMacosLib.macos_create_recording_session_with_device
                    .invokeExact(deviceDataLen, deviceDataSeq) as MemorySegment
            } else {
                NativeMacosLib.macos_create_recording_session_with_default_device
                    .invokeExact() as MemorySegment
            }
            NativeMacosAudioRecordingSession(
                nativeMemSeq = sessionSeq,
            )
        }

    override suspend fun createPlaybackSession(requestedDevice: AudioDevice.Output?): AudioPlaybackSession {
        TODO("Not yet implemented")
    }

    private inline fun <reified T : AudioDevice> listAudioDevice(listDevicesMethod: MethodHandle) =
        Arena.ofConfined().use { arena ->
            val sizeSeq = arena.allocate(ValueLayout.JAVA_LONG)
            val deviceDataSeq = listDevicesMethod.invokeExact(sizeSeq) as MemorySegment
            try {
                val size = sizeSeq.get(ValueLayout.JAVA_LONG, 0)
                val deviceData = deviceDataSeq.reinterpret(size).asByteBuffer()
                val buffer = Buffer().apply { write(deviceData) }
                val count = buffer.readInt()
                List(count) { buffer.readAudioDevice() }
            } finally {
                NativeMacosLib.macos_free.invokeExact(deviceDataSeq)
            }
        }.filterIsInstance<T>()

    private fun sessionSeqWrite(dst: MemorySegment, bytes: ByteArray) {
        dst.asSlice(0, bytes.size.toLong()).copyFrom(
            MemorySegment.ofArray(bytes)
        )
    }
}

private class NativeMacosAudioRecordingSession(
    private val nativeMemSeq: MemorySegment,
) : AudioRecordingSession {

    private val _state = MutableStateFlow<AudioRecordingSession.State>(AudioRecordingSession.State.Idle)
    override val state: StateFlow<AudioRecordingSession.State> = _state.asStateFlow()

    private val _audioShared = MutableSharedFlow<ByteArray>(replay = Int.MAX_VALUE)
    private val _audioFlowHolder = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlowHolder.asStateFlow()

    private val formatDeferred = CompletableDeferred<AudioFormat>()

    companion object {
        private val IDX = AtomicLong(1L)
        private val SESSIONS = ConcurrentHashMap<Long, NativeMacosAudioRecordingSession>()

        @JvmStatic
        fun onState(ctx: MemorySegment?, data: MemorySegment?, len: Int) {
            if (ctx == null || ctx.address() == 0L) return
            val ctx8 = ctx.reinterpret(java.lang.Long.BYTES.toLong())    // <<< important
            val id = ctx8.get(ValueLayout.JAVA_LONG, 0)

            val sess = SESSIONS[id] ?: return
            if (data == null || len <= 0) return

            val dseg = data.reinterpret(len.toLong())                     // <<< important
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

            if (!sess._audioShared.tryEmit(bytes))
                println("failed to emit ${bytes.size} bytes")
            else
                println("emitted ${bytes.size} bytes")
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
        if (started) return
        started = true

        val arena = Arena.ofShared().also { runtimeArena = it }
        id = IDX.getAndIncrement()
        SESSIONS[id] = this

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
        val fmt: AudioFormat = withTimeout(3_000) { formatDeferred.await() } // tune as you like
        if (_audioFlowHolder.value == null) {
            _audioFlowHolder.value = AudioFlow(
                format = fmt,
                data = _audioShared.asSharedFlow()
            )
        }
    }

    override fun stop() {
        if (!started) return
        NativeMacosLib.macos_recording_session_stop.invokeExact(nativeMemSeq)
        val format = audioFlow.value?.format
        if (format != null) {
            val coldFlow = AudioFlow(format,  _audioShared.replayCache.asFlow())
            _audioFlowHolder.value = coldFlow
        }
        cleanup()
        started = false
    }

    override fun reset() {
        NativeMacosLib.macos_recording_session_reset.invokeExact(nativeMemSeq)
        _audioShared.resetReplayCache()
    }

    private fun cleanup() {
        val old = id
        id = 0L
        if (old != 0L) SESSIONS.remove(old)
        runtimeArena?.close()
        runtimeArena = null
        ctxSeg = null
        stateCbStub = null; formatCbStub = null; audioCbStub = null
    }


    // TODO: finalize is deprecated, maybe use `closable`?
//    @Throws(Throwable::class)
//    protected fun finalize() {
//        try {
//            NativeMacosLib.macos_recording_session_release.invokeExact(nativeMemSeq)
//        } catch (_: Throwable) { /* ignore */ }
//        cleanup()
//    }
}

private object NativeMacosLib {

    val linker: Linker = Linker.nativeLinker()
    private val arena = Arena.ofShared()
    private lateinit var lookup: SymbolLookup

    // Exposed descriptors for upcalls
    // void (*cb)(void* ctx, void* data, int len)
    val CB_DESC: FunctionDescriptor = FunctionDescriptor.ofVoid(
        ValueLayout.ADDRESS, // ctx
        ValueLayout.ADDRESS, // data
        ValueLayout.JAVA_INT // len
    )

    // functions
    val macos_free: MethodHandle

    val macos_create_recording_session_with_device: MethodHandle
    val macos_create_recording_session_with_default_device: MethodHandle

    val macos_recording_session_start: MethodHandle
    val macos_recording_session_stop: MethodHandle
    val macos_recording_session_reset: MethodHandle
    val macos_recording_session_release: MethodHandle

    val macos_list_input_devices: MethodHandle
    val macos_list_output_devices: MethodHandle

    init {
        loadNativeLibraryFromJar("audioprocessing")
        lookup = SymbolLookup.loaderLookup()

        macos_free = lookupMethodVoid(
            name = "macos_free",
            ValueLayout.ADDRESS
        )

        macos_create_recording_session_with_device = lookupMethod(
            name = "macos_create_recording_session_with_device",
            res = ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS
        )
        macos_create_recording_session_with_default_device = lookupMethod(
            name = "macos_create_recording_session_with_default_device",
            res = ValueLayout.ADDRESS
        )

        // start(sessionPtr, ctx, on_state, on_audio)
        macos_recording_session_start = lookupMethodVoid(
            name = "macos_recording_session_start",
            ValueLayout.ADDRESS, // session
            ValueLayout.ADDRESS, // ctx
            ValueLayout.ADDRESS, // on_state
            ValueLayout.ADDRESS, // on_format (NEW)
            ValueLayout.ADDRESS  // on_audio
        )

        macos_recording_session_stop = lookupMethodVoid(
            name = "macos_recording_session_stop",
            ValueLayout.ADDRESS
        )
        macos_recording_session_reset = lookupMethodVoid(
            name = "macos_recording_session_reset",
            ValueLayout.ADDRESS
        )
        macos_recording_session_release = lookupMethodVoid(
            name = "macos_recording_session_release",
            ValueLayout.ADDRESS
        )

        macos_list_input_devices = lookupMethod(
            name = "macos_list_input_devices",
            res = ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        )
        macos_list_output_devices = lookupMethod(
            name = "macos_list_output_devices",
            res = ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        )
    }

    private fun lookupMethodVoid(
        name: String,
        vararg params: MemoryLayout
    ): MethodHandle {
        val address = lookup.find(name).get()
        val desc = FunctionDescriptor.ofVoid(*params)
        return linker.downcallHandle(address, desc)
    }

    private fun lookupMethod(
        name: String,
        res: MemoryLayout,
        vararg params: MemoryLayout
    ): MethodHandle {
        val address = lookup.find(name).get()
        val desc = FunctionDescriptor.of(res, *params)
        return linker.downcallHandle(address, desc)
    }
}