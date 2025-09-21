@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@file:Suppress("FunctionName", "unused")

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioDevice
import space.kodio.core.AudioRecordingSession
import space.kodio.core.SystemAudioSystem
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.io.readAudioDevice
import space.kodio.core.io.writeAudioDevice
import kotlin.experimental.ExperimentalNativeApi

@CName("macos_free")
fun macos_free(ptr: NativePtr): Unit =
    nativeHeap.free(ptr)

// --- Recording ----------------------------------------------------------------

@CName("macos_create_recording_session_with_device")
fun macos_create_recording_session_with_device(
    deviceDataSize: Int,
    deviceDataPtr: CArrayPointer<ByteVar>
): COpaquePointer {
    val deviceData = deviceDataPtr.readBytes(deviceDataSize)
    val buf = Buffer().apply { write(deviceData) }
    val device = buf.readAudioDevice()
    if (device !is AudioDevice.Input)
        error("Can only create a recording session from an input device, got $device.")
    return createRecordingSession(device)
}

@CName("macos_create_recording_session_with_default_device")
fun macos_create_recording_session_with_default_device(): COpaquePointer =
    createRecordingSession(null)

private fun createRecordingSession(device: AudioDevice.Input?): COpaquePointer {
    val session = runBlocking { SystemAudioSystem.createRecordingSession(device) }
    return StableRef.create(session).asCPointer()
}

/**
 * Start recording and stream updates to the provided callbacks.
 *
 * on_state(ctx, bytes, len) – called whenever the session State changes (bytes = state.encodeToByteArray()).
 * on_audio(ctx, bytes, len) – called per audio frame chunk (bytes = raw audio frame ByteArray from flow).
 *
 * The callee owns the buffers only during the call; the caller must copy if it needs to retain data.
 */
@CName("macos_recording_session_start")
fun macos_recording_session_start(
    sessionPtr: COpaquePointer,
    ctx: COpaquePointer?,
    on_state: CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Int) -> Unit>>?,
    on_format: CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Int) -> Unit>>?, // NEW
    on_audio: CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Int) -> Unit>>?
) {
    val session = sessionPtr.asStableRef<AudioRecordingSession>().get()

    GlobalScope.launch(Dispatchers.Default) {
        // Push initial state immediately
        on_state?.let { cb ->
            val b = session.state.value.encodeToByteArray()
            memScoped {
                val p = allocArray<ByteVar>(b.size)
                b.usePinned { src -> platform.posix.memcpy(p, src.addressOf(0), b.size.convert()) }
                cb(ctx, p, b.size)
            }
        }

        // State stream
        val stateJob = launch {
            session.state.collect { st ->
                val b = st.encodeToByteArray()
                memScoped {
                    val p = allocArray<ByteVar>(b.size)
                    b.usePinned { src -> platform.posix.memcpy(p, src.addressOf(0), b.size.convert()) }
                    on_state?.invoke(ctx, p, b.size)
                }
            }
        }

        // Start native recording
        session.start()

        // Once started, obtain format and send it ONCE
        val flow = session.audioFlow.value ?: return@launch
        on_format?.let { cb ->
            val fmtBytes = flow.format.encodeToByteArray() // you already have encode
            memScoped {
                val p = allocArray<ByteVar>(fmtBytes.size)
                fmtBytes.usePinned { src -> platform.posix.memcpy(p, src.addressOf(0), fmtBytes.size.convert()) }
                cb(ctx, p, fmtBytes.size)
            }
        }

        // Stream audio frames
        val audioJob = launch {
            flow.collect { frame: ByteArray ->
                memScoped {
                    val p = allocArray<ByteVar>(frame.size)
                    frame.usePinned { src -> platform.posix.memcpy(p, src.addressOf(0), frame.size.convert()) }
                    on_audio?.invoke(ctx, p, frame.size)
                }
            }
        }

        joinAll(stateJob, audioJob)
    }
}

@CName("macos_recording_session_stop")
fun macos_recording_session_stop(ptr: COpaquePointer) {
    val session = ptr.asStableRef<AudioRecordingSession>().get()
    session.stop()
}

@CName("macos_recording_session_reset")
fun macos_recording_session_reset(ptr: COpaquePointer) {
    val session = ptr.asStableRef<AudioRecordingSession>().get()
    session.reset()
}

/** Dispose StableRef when you’re fully done with the session (after stop/reset). */
@CName("macos_recording_session_release")
fun macos_recording_session_release(ptr: COpaquePointer) =
    ptr.asStableRef<AudioRecordingSession>().dispose()

// --- Devices ------------------------------------------------------------------

@CName("macos_list_input_devices")
fun macos_list_input_devices(sizePtr: CPointer<LongVar>): CArrayPointer<ByteVar> =
    listDevices(sizePtr, SystemAudioSystem::listInputDevices)

@CName("macos_list_output_devices")
fun macos_list_output_devices(sizePtr: CPointer<LongVar>): CArrayPointer<ByteVar> =
    listDevices(sizePtr, SystemAudioSystem::listOutputDevices)

private fun listDevices(
    sizePtr: CPointer<LongVar>,
    listDevices: suspend () -> List<AudioDevice>
): CArrayPointer<ByteVar> {
    val devices = runBlocking { listDevices() }
    val buf = Buffer().apply {
        writeInt(devices.size)
        devices.forEach { writeAudioDevice(it) }
    }
    sizePtr[0] = buf.size
    val bytes = buf.readByteArray()
    return nativeHeap.allocArrayOf(bytes)
}