package space.kodio.core

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write
import space.kodio.core.io.encodeToByteArray
import space.kodio.core.io.readAudioDevice
import space.kodio.core.security.AudioPermissionManager
import space.kodio.core.util.namedLogger

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
                initialFormat = requestedFormat ?: DefaultJvmRecordingAudioFormat,
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
