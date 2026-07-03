package space.kodio.core

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * Native library bindings for macOS audio via Panama FFI.
 */
internal object NativeMacosLib {

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
    val macos_recording_session_pause: MethodHandle
    val macos_recording_session_resume: MethodHandle
    val macos_recording_session_reset: MethodHandle
    val macos_recording_session_release: MethodHandle

    // Playback functions
    val macos_create_playback_session_with_device: MethodHandle
    val macos_create_playback_session_with_default_device: MethodHandle
    val macos_playback_session_load: MethodHandle
    val macos_playback_session_play: MethodHandle
    val macos_playback_session_await_completion: MethodHandle
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
        macos_recording_session_pause = lookupMethodVoid(
            name = "macos_recording_session_pause",
            ValueLayout.ADDRESS
        )
        macos_recording_session_resume = lookupMethodVoid(
            name = "macos_recording_session_resume",
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
            ValueLayout.ADDRESS // session
        )

        macos_playback_session_await_completion = lookupMethod(
            name = "macos_playback_session_await_completion",
            res = ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS // session
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
