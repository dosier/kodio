@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write
import space.kodio.core.io.collectAsSource
import space.kodio.core.io.convertAudio
import space.kodio.core.io.decodeAsAudioPlaybackState
import space.kodio.core.io.decodeAsAudioRecordingState
import space.kodio.core.io.decodeAsAudioFormat
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.io.readAudioDevice
import space.kodio.core.security.AudioPermissionManager
import space.kodio.core.util.namedLogger
import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = namedLogger("NativeMacosAudio")

/**
 * Native macOS implementation for [AudioSystem] using Panama FFI to call
 * Kotlin/Native code that wraps CoreAudio.
 *
 * This provides better audio quality and device support than the pure JVM
 * implementation using javax.sound.sampled.
 *
 * Use [isAvailable] to check if the native library loaded successfully before using.
 */
internal object NativeMacosAudioSystem : SystemAudioSystemImpl() {

    /**
     * Whether the native macOS audio library is available.
     * Returns true only on macOS when the native library loaded successfully.
     */
    val isAvailable: Boolean by lazy {
        try {
            // Accessing NativeMacosLib will trigger the init block which loads the library
            NativeMacosLib.linker
            true
        } catch (e: Throwable) {
            logger.warn { "Native macOS audio library not available: ${e.message}" }
            false
        }
    }

    override val permissionManager: AudioPermissionManager
        get() = JvmAudioPermissionManager

    override suspend fun listInputDevices(): List<AudioDevice.Input> {
        check(isAvailable) { "Native macOS audio library not available" }
        return listAudioDevice<AudioDevice.Input>(NativeMacosLib.macos_list_input_devices)
    }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> {
        check(isAvailable) { "Native macOS audio library not available" }
        return listAudioDevice<AudioDevice.Output>(NativeMacosLib.macos_list_output_devices)
    }

    override suspend fun createRecordingSession(
        requestedDevice: AudioDevice.Input?,
        requestedFormat: AudioFormat?,
    ): AudioRecordingSession {
        check(isAvailable) { "Native macOS audio library not available" }
        return Arena.ofShared().use { arena ->
            val sessionSeq = when {
                requestedDevice != null && requestedFormat != null -> {
                    val deviceData = requestedDevice.encodeToByteArray()
                    val deviceDataSeq = arena.allocate(deviceData.size.toLong())
                    sessionSeqWrite(deviceDataSeq, deviceData)
                    val formatData = requestedFormat.encodeToByteArray()
                    val formatDataSeq = arena.allocate(formatData.size.toLong())
                    sessionSeqWrite(formatDataSeq, formatData)
                    NativeMacosLib.macos_create_recording_session_with_device_and_format
                        .invokeExact(deviceData.size, deviceDataSeq, formatData.size, formatDataSeq) as MemorySegment
                }
                requestedDevice != null -> {
                    val deviceData = requestedDevice.encodeToByteArray()
                    val deviceDataSeq = arena.allocate(deviceData.size.toLong())
                    sessionSeqWrite(deviceDataSeq, deviceData)
                    NativeMacosLib.macos_create_recording_session_with_device
                        .invokeExact(deviceData.size, deviceDataSeq) as MemorySegment
                }
                requestedFormat != null -> {
                    val formatData = requestedFormat.encodeToByteArray()
                    val formatDataSeq = arena.allocate(formatData.size.toLong())
                    sessionSeqWrite(formatDataSeq, formatData)
                    NativeMacosLib.macos_create_recording_session_with_format
                        .invokeExact(formatData.size, formatDataSeq) as MemorySegment
                }
                else -> {
                    NativeMacosLib.macos_create_recording_session_with_default_device
                        .invokeExact() as MemorySegment
                }
            }
            NativeMacosAudioRecordingSession(
                nativeMemSeq = sessionSeq,
            )
        }
    }

    override suspend fun createPlaybackSession(requestedDevice: AudioDevice.Output?): AudioPlaybackSession {
        check(isAvailable) { "Native macOS audio library not available" }
        return Arena.ofShared().use { arena ->
            val sessionSeq = if (requestedDevice != null) {
                val deviceData = requestedDevice.encodeToByteArray()
                val deviceDataLen = deviceData.size
                val deviceDataSeq = arena.allocate(deviceDataLen.toLong())
                sessionSeqWrite(deviceDataSeq, deviceData)
                NativeMacosLib.macos_create_playback_session_with_device
                    .invokeExact(deviceDataLen, deviceDataSeq) as MemorySegment
            } else {
                NativeMacosLib.macos_create_playback_session_with_default_device
                    .invokeExact() as MemorySegment
            }
            NativeMacosAudioPlaybackSession(
                nativeMemSeq = sessionSeq,
            )
        }
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

/**
 * Native macOS recording session implementation using Panama FFI.
 */
private class NativeMacosAudioRecordingSession(
    private val nativeMemSeq: MemorySegment,
) : AudioRecordingSession {

    private val _state = MutableStateFlow<AudioRecordingSession.State>(AudioRecordingSession.State.Idle)
    override val state: StateFlow<AudioRecordingSession.State> = _state.asStateFlow()

    private var _audioShared = MutableSharedFlow<ByteArray>(replay = Int.MAX_VALUE)
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
            val coldFlow = AudioFlow(format, _audioShared.replayCache.asFlow())
            _audioFlowHolder.value = coldFlow
            logger.debug { "Created cold flow with ${_audioShared.replayCache.size} chunks" }
        }
        _state.value = AudioRecordingSession.State.Stopped
        started = false
        logger.debug { "stop() completed, state=${_state.value}" }
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
        _audioShared = MutableSharedFlow(replay = Int.MAX_VALUE)
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

/**
 * Native macOS playback session implementation using Panama FFI.
 */
private class NativeMacosAudioPlaybackSession(
    private val nativeMemSeq: MemorySegment,
) : AudioPlaybackSession {

    private val _state = MutableStateFlow<AudioPlaybackSession.State>(AudioPlaybackSession.State.Idle)
    override val state: StateFlow<AudioPlaybackSession.State> = _state.asStateFlow()

    private val _audioFlowHolder = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlowHolder.asStateFlow()

    companion object {
        private val IDX = AtomicLong(1L)
        private val SESSIONS = ConcurrentHashMap<Long, NativeMacosAudioPlaybackSession>()

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

            sess._state.value = bytes.decodeAsAudioPlaybackState()
        }
    }

    private var runtimeArena: Arena? = null
    private var ctxSeg: MemorySegment? = null
    private var stateCbStub: MemorySegment? = null
    private var id: Long = 0L
    private var loaded = false

    override suspend fun load(audioFlow: AudioFlow) {
        _audioFlowHolder.value = audioFlow

        closePreviousArena()
        val arena = Arena.ofShared().also { runtimeArena = it }
        id = IDX.getAndIncrement()
        SESSIONS[id] = this

        // Normalize to a device-friendly format on the JVM side before
        // sending across FFI, so the native side never has to convert
        // exotic bit depths (24-bit, 32-bit int, unsigned, big-endian).
        val nativeFlow = normalizeForPlayback(audioFlow)

        // Encode format
        val formatData = nativeFlow.format.encodeToByteArray()
        val formatDataSeq = arena.allocate(formatData.size.toLong())
        formatDataSeq.asSlice(0, formatData.size.toLong()).copyFrom(MemorySegment.ofArray(formatData))

        // Collect all audio data into a single byte array
        val audioSource = runBlocking { nativeFlow.collectAsSource() }
        val audioData = audioSource.source.readByteArray()
        val audioDataSeq = arena.allocate(audioData.size.toLong())
        audioDataSeq.asSlice(0, audioData.size.toLong()).copyFrom(MemorySegment.ofArray(audioData))

        // Call native load
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

        ctxSeg = runtimeArena!!.allocate(ValueLayout.JAVA_LONG).also { it.set(ValueLayout.JAVA_LONG, 0, id) }

        val stateMh = MethodHandles.lookup().findStatic(
            javaClass, "onState",
            MethodType.methodType(
                Void.TYPE,
                MemorySegment::class.java,
                MemorySegment::class.java,
                Int::class.javaPrimitiveType
            )
        )

        stateCbStub = NativeMacosLib.linker.upcallStub(stateMh, NativeMacosLib.CB_DESC, runtimeArena!!)

        // Kick off native play; state callbacks will update the state
        NativeMacosLib.macos_playback_session_play.invokeExact(
            nativeMemSeq, ctxSeg!!, stateCbStub!!
        )
    }

    override fun pause() {
        NativeMacosLib.macos_playback_session_pause.invokeExact(nativeMemSeq)
    }

    override fun resume() {
        NativeMacosLib.macos_playback_session_resume.invokeExact(nativeMemSeq)
    }

    override fun stop() {
        // Remove from SESSIONS first so any late native callbacks become no-ops.
        // The arena must NOT be closed here — the native stateJob (running in GlobalScope)
        // may still invoke the upcall stub after stop() returns. Closing the arena would
        // free that stub and cause a SIGSEGV.
        val old = id
        id = 0L
        if (old != 0L) SESSIONS.remove(old)
        NativeMacosLib.macos_playback_session_stop.invokeExact(nativeMemSeq)
        loaded = false
    }

    private fun closePreviousArena() {
        runtimeArena?.close()
        runtimeArena = null
        ctxSeg = null
        stateCbStub = null
    }
}

/**
 * Native library bindings for macOS audio via Panama FFI.
 */
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

    // Recording functions
    val macos_free: MethodHandle
    val macos_create_recording_session_with_device: MethodHandle
    val macos_create_recording_session_with_default_device: MethodHandle
    val macos_create_recording_session_with_format: MethodHandle
    val macos_create_recording_session_with_device_and_format: MethodHandle
    val macos_recording_session_start: MethodHandle
    val macos_recording_session_stop: MethodHandle
    val macos_recording_session_reset: MethodHandle
    val macos_recording_session_release: MethodHandle

    // Playback functions
    val macos_create_playback_session_with_device: MethodHandle
    val macos_create_playback_session_with_default_device: MethodHandle
    val macos_playback_session_load: MethodHandle
    val macos_playback_session_play: MethodHandle
    val macos_playback_session_pause: MethodHandle
    val macos_playback_session_resume: MethodHandle
    val macos_playback_session_stop: MethodHandle
    val macos_playback_session_release: MethodHandle

    // Device functions
    val macos_list_input_devices: MethodHandle
    val macos_list_output_devices: MethodHandle

    init {
        if (!loadNativeLibraryFromJar("audioprocessing")) {
            throw UnsatisfiedLinkError("Failed to load native audioprocessing library")
        }
        lookup = SymbolLookup.loaderLookup()

        macos_free = lookupMethodVoid(
            name = "macos_free",
            ValueLayout.ADDRESS
        )

        // Recording session methods
        macos_create_recording_session_with_device = lookupMethod(
            name = "macos_create_recording_session_with_device",
            res = ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS
        )
        macos_create_recording_session_with_default_device = lookupMethod(
            name = "macos_create_recording_session_with_default_device",
            res = ValueLayout.ADDRESS
        )
        macos_create_recording_session_with_format = lookupMethod(
            name = "macos_create_recording_session_with_format",
            res = ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS
        )
        macos_create_recording_session_with_device_and_format = lookupMethod(
            name = "macos_create_recording_session_with_device_and_format",
            res = ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS
        )

        macos_recording_session_start = lookupMethodVoid(
            name = "macos_recording_session_start",
            ValueLayout.ADDRESS, // session
            ValueLayout.ADDRESS, // ctx
            ValueLayout.ADDRESS, // on_state
            ValueLayout.ADDRESS, // on_format
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

        // Playback session methods
        macos_create_playback_session_with_device = lookupMethod(
            name = "macos_create_playback_session_with_device",
            res = ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS
        )
        macos_create_playback_session_with_default_device = lookupMethod(
            name = "macos_create_playback_session_with_default_device",
            res = ValueLayout.ADDRESS
        )

        macos_playback_session_load = lookupMethodVoid(
            name = "macos_playback_session_load",
            ValueLayout.ADDRESS, // session
            ValueLayout.JAVA_INT, // formatDataSize
            ValueLayout.ADDRESS, // formatDataPtr
            ValueLayout.JAVA_INT, // audioDataSize
            ValueLayout.ADDRESS  // audioDataPtr
        )

        macos_playback_session_play = lookupMethodVoid(
            name = "macos_playback_session_play",
            ValueLayout.ADDRESS, // session
            ValueLayout.ADDRESS, // ctx
            ValueLayout.ADDRESS  // on_state
        )

        macos_playback_session_pause = lookupMethodVoid(
            name = "macos_playback_session_pause",
            ValueLayout.ADDRESS
        )
        macos_playback_session_resume = lookupMethodVoid(
            name = "macos_playback_session_resume",
            ValueLayout.ADDRESS
        )
        macos_playback_session_stop = lookupMethodVoid(
            name = "macos_playback_session_stop",
            ValueLayout.ADDRESS
        )
        macos_playback_session_release = lookupMethodVoid(
            name = "macos_playback_session_release",
            ValueLayout.ADDRESS
        )

        // Device methods
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
