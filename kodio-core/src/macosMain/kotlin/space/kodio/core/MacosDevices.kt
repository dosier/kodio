package space.kodio.core

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreAudio.AudioObjectGetPropertyData
import platform.CoreAudio.AudioObjectGetPropertyDataSize
import platform.CoreAudio.AudioObjectID
import platform.CoreAudio.AudioObjectPropertyAddress
import platform.CoreAudio.AudioObjectPropertyScope
import platform.CoreAudio.kAudioDevicePropertyStreamConfiguration
import platform.CoreAudio.kAudioObjectPropertyElementMain
import platform.CoreAudio.kAudioObjectPropertyScopeInput
import platform.CoreAudio.kAudioObjectPropertyScopeOutput
import platform.CoreAudioTypes.AudioBufferList
import platform.darwin.noErr

// ───────────────────────────────────────────────────────────────────────────────
// CoreAudio helpers
// ───────────────────────────────────────────────────────────────────────────────

internal data class CoreAudioDevice(
    val id: AudioObjectID,
    val uid: String,
    val name: String,
    val formatSupport: AudioFormatSupport
)


/**
 * Enumerate devices and filter by whether they provide channels in the requested scope.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun enumerateDevices(scope: AudioObjectPropertyScope): List<CoreAudioDevice> = memScoped {
    // 1) Fetch all device IDs
    val devices = MacosAudioObject.Global.enumerateDeviceIds()

    // 2) For each device, check if it has at least one channel in the requested scope
    val result = mutableListOf<CoreAudioDevice>()
    for (deviceId in devices) {
        val device = when (scope) {
            kAudioObjectPropertyScopeInput -> MacosAudioObject.Input(deviceId)
            kAudioObjectPropertyScopeOutput -> MacosAudioObject.Output(deviceId)
            else -> error("Unsupported scope $scope")
        }

        runCatching {
            val channelCount = deviceTotalChannels(deviceId, scope)
            if (channelCount <= 0)
                return@runCatching null
            val uid = device.uid ?: error("Could not get device UID")
            val name = device.name ?: "Device $deviceId"
            val audioFormatSupport = device.getDeviceStreams()
                .flatMap { stream ->
                    with(stream) {
                        getAvailableVirtualFormats()
                            .mapNotNull { toCommonAudioFormat(it.mFormat) }
                    }
                }
                .takeIf { it.isNotEmpty() }
                ?.let { AudioFormatSupport.Known(it, it.first()) }
                ?: AudioFormatSupport.Unknown
            CoreAudioDevice(deviceId, uid, name, audioFormatSupport)
        }.onFailure { error ->
            println("Failed to enumerate device $deviceId: $error")
            error.printStackTrace()
        }.onSuccess { coreAudioDevice ->
            if (coreAudioDevice != null) {
                println("Enumerated device $deviceId: $coreAudioDevice")
                result += coreAudioDevice
            }
        }
    }
    result
}

/**
 * Sum channel counts from the device's stream configuration (AudioBufferList) for the given scope.
 */
@OptIn(ExperimentalForeignApi::class)
private fun MemScope.deviceTotalChannels(deviceId: AudioObjectID, scope: AudioObjectPropertyScope): Int {
    val address = alloc<AudioObjectPropertyAddress>().apply {
        mSelector = kAudioDevicePropertyStreamConfiguration
        mScope = scope
        mElement = kAudioObjectPropertyElementMain
    }

    // First call: get size needed for the AudioBufferList
    val dataSizeVar = alloc<UIntVar>()
    val status1 = AudioObjectGetPropertyDataSize(deviceId, address.ptr, 0u, null, dataSizeVar.ptr)
    if (status1.toUInt() != noErr) {
        // Some devices may not report this; treat as 0 channels
        return 0
    }
    val ablSize = dataSizeVar.value.toInt()
    if (ablSize <= 0) return 0

    // Allocate a raw buffer to hold the AudioBufferList
    val raw = allocArray<ByteVar>(ablSize)
    val status2 = AudioObjectGetPropertyData(deviceId, address.ptr, 0u, null, dataSizeVar.ptr, raw)
    if (status2.toUInt() != noErr) return 0

    // Interpret as AudioBufferList
    val abl = raw.reinterpret<AudioBufferList>()
    val bufferCount = abl.pointed.mNumberBuffers.toInt()
    var channels = 0
    if (bufferCount > 0) {
        for (i in 0 until bufferCount) {
            val buf = abl.pointed.mBuffers[i]
            channels += buf.mNumberChannels.toInt()
        }
    }
    return channels
}

