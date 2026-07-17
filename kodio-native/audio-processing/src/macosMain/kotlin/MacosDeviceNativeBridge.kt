@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@file:Suppress("FunctionName", "unused")

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioDevice
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat
import space.kodio.core.AudioPlaybackSession
import space.kodio.core.AudioRecordingSession
import space.kodio.core.SeekResult
import space.kodio.core.SystemAudioSystem
import space.kodio.core.io.decodeAsAudioFormat
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.io.readAudioDevice
import space.kodio.core.io.writeAudioDevice
import kotlin.experimental.ExperimentalNativeApi
import kotlin.time.Duration.Companion.nanoseconds

@CName("macos_free")
fun macos_free(ptr: NativePtr): Unit =
    nativeHeap.free(ptr)

// --- Recording ----------------------------------------------------------------

@CName("macos_create_recording_session_with_device")
fun macos_create_recording_session_with_device(
    deviceDataSize: Int,
    deviceDataPtr: CArrayPointer<ByteVar>
): COpaquePointer {
    val device = decodeInputDevice(deviceDataSize, deviceDataPtr)
    return createRecordingSession(device)
}

@CName("macos_create_recording_session_with_default_device")
fun macos_create_recording_session_with_default_device(): COpaquePointer =
    createRecordingSession(null)

@CName("macos_create_recording_session_with_format")
fun macos_create_recording_session_with_format(
    formatDataSize: Int,
    formatDataPtr: CArrayPointer<ByteVar>
): COpaquePointer {
    val format = formatDataPtr.readBytes(formatDataSize).decodeAsAudioFormat()
    return createRecordingSession(device = null, format = format)
}

@CName("macos_create_recording_session_with_device_and_format")
fun macos_create_recording_session_with_device_and_format(
    deviceDataSize: Int,
    deviceDataPtr: CArrayPointer<ByteVar>,
    formatDataSize: Int,
    formatDataPtr: CArrayPointer<ByteVar>
): COpaquePointer {
    val device = decodeInputDevice(deviceDataSize, deviceDataPtr)
    val format = formatDataPtr.readBytes(formatDataSize).decodeAsAudioFormat()
    return createRecordingSession(device, format)
}

private fun decodeInputDevice(
    dataSize: Int,
    dataPtr: CArrayPointer<ByteVar>
): AudioDevice.Input {
    val buf = Buffer().apply { write(dataPtr.readBytes(dataSize)) }
    val device = buf.readAudioDevice()
    if (device !is AudioDevice.Input)
        error("Can only create a recording session from an input device, got $device.")
    return device
}

private fun createRecordingSession(
    device: AudioDevice.Input?,
    format: AudioFormat? = null,
): COpaquePointer {
    val session = runBlocking {
        SystemAudioSystem.createRecordingSession(device, format)
    }
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

/**
 * Pause an active recording. Capture halts but the AudioQueue + buffers stay
 * allocated so [macos_recording_session_resume] is cheap and the previously
 * captured chunks remain in the session's hot flow.
 */
@CName("macos_recording_session_pause")
fun macos_recording_session_pause(ptr: COpaquePointer) {
    val session = ptr.asStableRef<AudioRecordingSession>().get()
    runBlocking { session.pause() }
}

/** Resume a previously paused recording. */
@CName("macos_recording_session_resume")
fun macos_recording_session_resume(ptr: COpaquePointer) {
    val session = ptr.asStableRef<AudioRecordingSession>().get()
    runBlocking { session.resume() }
}

@CName("macos_recording_session_reset")
fun macos_recording_session_reset(ptr: COpaquePointer) {
    val session = ptr.asStableRef<AudioRecordingSession>().get()
    session.reset()
}

/** Dispose StableRef when you're fully done with the session (after stop/reset). */
@CName("macos_recording_session_release")
fun macos_recording_session_release(ptr: COpaquePointer) =
    ptr.asStableRef<AudioRecordingSession>().dispose()

// --- Playback -----------------------------------------------------------------

/**
 * Panama playback handle: wraps the K/N [AudioPlaybackSession] (transport-backed
 * AudioQueue) plus an optional ingest [Channel] for streaming append without
 * materializing the whole PCM on the JVM.
 */
private class PlaybackNativeHandle(
    val session: AudioPlaybackSession,
    var ingestChannel: Channel<ByteArray>? = null,
)

private fun COpaquePointer.playbackHandle(): PlaybackNativeHandle =
    asStableRef<PlaybackNativeHandle>().get()

@CName("macos_create_playback_session_with_device")
fun macos_create_playback_session_with_device(
    deviceDataSize: Int,
    deviceDataPtr: CArrayPointer<ByteVar>
): COpaquePointer {
    val deviceData = deviceDataPtr.readBytes(deviceDataSize)
    val buf = Buffer().apply { write(deviceData) }
    val device = buf.readAudioDevice()
    if (device !is AudioDevice.Output)
        error("Can only create a playback session from an output device, got $device.")
    return createPlaybackSession(device)
}

@CName("macos_create_playback_session_with_default_device")
fun macos_create_playback_session_with_default_device(): COpaquePointer =
    createPlaybackSession(null)

private fun createPlaybackSession(device: AudioDevice.Output?): COpaquePointer {
    val session = runBlocking { SystemAudioSystem.createPlaybackSession(device) }
    return StableRef.create(PlaybackNativeHandle(session)).asCPointer()
}

/**
 * Begin a streaming load: [macos_playback_session_append] feeds PCM chunks;
 * [macos_playback_session_end_stream] closes ingest. [knownByteCount] may be -1
 * for unbounded/live content.
 */
@CName("macos_playback_session_begin_stream")
fun macos_playback_session_begin_stream(
    sessionPtr: COpaquePointer,
    formatDataSize: Int,
    formatDataPtr: CArrayPointer<ByteVar>,
    knownByteCount: Long,
) {
    val handle = sessionPtr.playbackHandle()
    handle.ingestChannel?.close()
    val format = formatDataPtr.readBytes(formatDataSize).decodeAsAudioFormat()
    val channel = Channel<ByteArray>(capacity = 64)
    handle.ingestChannel = channel
    val known = knownByteCount.takeIf { it >= 0L }
    val audioFlow = AudioFlow(
        format = format,
        data = channel.receiveAsFlow(),
        knownByteCount = known,
    )
    runBlocking { handle.session.load(audioFlow) }
}

/** Append one PCM chunk into the open ingest stream (copies bytes). */
@CName("macos_playback_session_append")
fun macos_playback_session_append(
    sessionPtr: COpaquePointer,
    audioDataSize: Int,
    audioDataPtr: CArrayPointer<ByteVar>,
): Int {
    val handle = sessionPtr.playbackHandle()
    val channel = handle.ingestChannel ?: return 0
    if (audioDataSize <= 0) return 1
    val audioData = audioDataPtr.readBytes(audioDataSize)
    return if (channel.trySend(audioData).isSuccess) 1 else 0
}

/** Close the ingest stream so the transport can finish after draining. */
@CName("macos_playback_session_end_stream")
fun macos_playback_session_end_stream(sessionPtr: COpaquePointer) {
    val handle = sessionPtr.playbackHandle()
    handle.ingestChannel?.close()
    handle.ingestChannel = null
}

/**
 * Legacy whole-buffer load (still used by older callers). Prefer begin/append/end
 * for long content so the JVM need not retain the full PCM.
 */
@CName("macos_playback_session_load")
fun macos_playback_session_load(
    sessionPtr: COpaquePointer,
    formatDataSize: Int,
    formatDataPtr: CArrayPointer<ByteVar>,
    audioDataSize: Int,
    audioDataPtr: CArrayPointer<ByteVar>
) {
    val handle = sessionPtr.playbackHandle()
    handle.ingestChannel?.close()
    handle.ingestChannel = null
    val format = formatDataPtr.readBytes(formatDataSize).decodeAsAudioFormat()
    val audioData = audioDataPtr.readBytes(audioDataSize)
    val audioFlow = AudioFlow(
        format = format,
        data = kotlinx.coroutines.flow.flowOf(audioData),
        knownByteCount = audioData.size.toLong(),
    )
    runBlocking { handle.session.load(audioFlow) }
}

/**
 * Start playback. State transitions are managed by the JVM caller;
 * use [macos_playback_session_await_completion] to block until a terminal state.
 */
@CName("macos_playback_session_play")
fun macos_playback_session_play(sessionPtr: COpaquePointer) {
    val session = sessionPtr.playbackHandle().session
    runBlocking { session.play() }
}

private const val COMPLETION_FINISHED = 0
private const val COMPLETION_ERROR = 1
private const val COMPLETION_IDLE = 2

/**
 * Blocks until the session reaches a terminal state (Finished, Error, or Idle).
 * Returns 0 for Finished, 1 for Error, 2 for Idle (stopped externally).
 */
@CName("macos_playback_session_await_completion")
fun macos_playback_session_await_completion(sessionPtr: COpaquePointer): Int {
    val session = sessionPtr.playbackHandle().session
    val terminal = runBlocking {
        session.state.first {
            it is AudioPlaybackSession.State.Finished ||
                it is AudioPlaybackSession.State.Error ||
                it is AudioPlaybackSession.State.Idle
        }
    }
    return when (terminal) {
        is AudioPlaybackSession.State.Finished -> COMPLETION_FINISHED
        is AudioPlaybackSession.State.Error -> COMPLETION_ERROR
        else -> COMPLETION_IDLE
    }
}

@CName("macos_playback_session_pause")
fun macos_playback_session_pause(ptr: COpaquePointer) {
    ptr.playbackHandle().session.pause()
}

@CName("macos_playback_session_resume")
fun macos_playback_session_resume(ptr: COpaquePointer) {
    ptr.playbackHandle().session.resume()
}

@CName("macos_playback_session_stop")
fun macos_playback_session_stop(ptr: COpaquePointer) {
    val handle = ptr.playbackHandle()
    handle.ingestChannel?.close()
    handle.ingestChannel = null
    handle.session.stop()
}

/**
 * Transport seek on the native AudioQueue session.
 * @return 1 if [SeekResult.Applied], 0 if [SeekResult.Unsupported].
 */
@CName("macos_playback_session_seek")
fun macos_playback_session_seek(sessionPtr: COpaquePointer, positionNanos: Long): Int {
    val result = sessionPtr.playbackHandle().session.seekTo(positionNanos.nanoseconds)
    return if (result is SeekResult.Applied) 1 else 0
}

/** Current transport position in nanoseconds. */
@CName("macos_playback_session_position_ns")
fun macos_playback_session_position_ns(sessionPtr: COpaquePointer): Long =
    sessionPtr.playbackHandle().session.position.value.inWholeNanoseconds

/**
 * Writes buffered-range start/end (nanoseconds, end-exclusive) into out pointers.
 * @return 1 if a non-empty range is published, else 0.
 */
@CName("macos_playback_session_buffered_range_ns")
fun macos_playback_session_buffered_range_ns(
    sessionPtr: COpaquePointer,
    startOut: CPointer<LongVar>,
    endOut: CPointer<LongVar>,
): Int {
    val range = sessionPtr.playbackHandle().session.bufferedRange.value
    startOut[0] = range.start.inWholeNanoseconds
    endOut[0] = range.endExclusive.inWholeNanoseconds
    return if (range.isEmpty) 0 else 1
}

/** Dispose StableRef when you're fully done with the session. */
@CName("macos_playback_session_release")
fun macos_playback_session_release(ptr: COpaquePointer) {
    val handle = ptr.playbackHandle()
    handle.ingestChannel?.close()
    ptr.asStableRef<PlaybackNativeHandle>().dispose()
}

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