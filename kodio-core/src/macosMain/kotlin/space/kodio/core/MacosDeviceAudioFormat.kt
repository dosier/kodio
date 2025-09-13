package space.kodio.core

import kotlinx.cinterop.*
import platform.CoreAudio.*
import platform.darwin.noErr

// ───────────────────────────────────────────────────────────────────────────────
// Public API
// ───────────────────────────────────────────────────────────────────────────────

//@OptIn(ExperimentalForeignApi::class)
//fun printDevices() = memScoped{
//    enumerateDevices(kAudioObjectPropertyScopeInput).forEach {
//        println(it)
//        printAudioFormats(it.id, kAudioObjectPropertyScopeInput)
//    }
//    enumerateDevices(kAudioObjectPropertyScopeOutput).forEach {
//        println(it)
//        printAudioFormats(it.id, kAudioObjectPropertyScopeOutput)
//    }
//}
//
//@OptIn(ExperimentalForeignApi::class)
//fun printAudioFormats(deviceId: AudioObjectID, scope: AudioObjectPropertyScope) = memScoped {
//
//    val streamIds = getDeviceStreams(deviceId, scope)
//    if (streamIds.isEmpty()) return@memScoped
//
//    for (streamId in streamIds) {
//        val ranged = getStreamAvailableVirtualFormats(streamId)
//        println("Stream ID: $streamId")
//        for (r in ranged) {
//            val asbd = r.mFormat
//            val common = toCommonAudioFormat(asbd) ?: continue
//            val commonToAsbd = allocASBD(common)
//            println("\tSample Rate:         ${asbd.mSampleRate} -> ${commonToAsbd.mSampleRate}")
//            println("\tChannel per Frame:   ${asbd.mChannelsPerFrame} -> ${commonToAsbd.mChannelsPerFrame}")
//            println("\tBytes per Frame:     ${asbd.mBytesPerFrame} -> ${commonToAsbd.mBytesPerFrame}")
//            println("\tFrames per Packet:   ${asbd.mFramesPerPacket} -> ${commonToAsbd.mFramesPerPacket}")
//            println("\tBits Per Channel:    ${asbd.mBitsPerChannel} -> ${commonToAsbd.mBitsPerChannel}")
//            println("\tFormat ID:           ${asbd.mFormatID} -> ${commonToAsbd.mFormatID}")
//            println("\tFormat Flags:        ${asbd.mFormatFlags} -> ${commonToAsbd.mFormatFlags}")
//            println("\t${common.encoding}")
//            println("…")
//        }
//        println("------------------------")
//    }
//}
//
//// ───────────────────────────────────────────────────────────────────────────────
//// Mapping & CoreAudio glue
//// ───────────────────────────────────────────────────────────────────────────────
//
//// The ranged-format struct from CoreAudio
//private typealias Ranged = AudioStreamRangedDescription
//
//@OptIn(ExperimentalForeignApi::class)
//private fun MemScope.getStreamAvailableVirtualFormats(streamId: AudioObjectID): List<Ranged> {
//    val address = alloc<AudioObjectPropertyAddress>().apply {
//        mSelector = kAudioStreamPropertyAvailableVirtualFormats
//        mScope = kAudioObjectPropertyScopeGlobal
//        mElement = kAudioObjectPropertyElementMain
//    }
//    val sizeVar = alloc<UIntVar>()
//    val s1 = AudioObjectGetPropertyDataSize(streamId, address.ptr, 0u, null, sizeVar.ptr)
//    if (s1.toUInt() != noErr) return emptyList()
//    val bytes = sizeVar.value.toInt()
//    if (bytes <= 0) return emptyList()
//
//    val count = bytes / sizeOf<Ranged>()
//    val arr = allocArray<Ranged>(count)
//    val s2 = AudioObjectGetPropertyData(streamId, address.ptr, 0u, null, sizeVar.ptr, arr)
//    if (s2.toUInt() != noErr) return emptyList()
//
//    return (0 until count).map { arr[it] }
//}
//
//@OptIn(ExperimentalForeignApi::class)
//private fun MemScope.getDeviceStreams(
//    deviceId: AudioObjectID,
//    scope: AudioObjectPropertyScope
//): List<AudioObjectID> {
//    val address = alloc<AudioObjectPropertyAddress>().apply {
//        mSelector = kAudioDevicePropertyStreams
//        mScope = scope
//        mElement = kAudioObjectPropertyElementMain
//    }
//    val sizeVar = alloc<UIntVar>()
//    val s1 = AudioObjectGetPropertyDataSize(deviceId, address.ptr, 0u, null, sizeVar.ptr)
//    if (s1.toUInt() != noErr) return emptyList()
//
//    val count = sizeVar.value.toInt() / sizeOf<AudioObjectIDVar>()
//    if (count <= 0) return emptyList()
//
//    val ids = allocArray<AudioObjectIDVar>(count)
//    val s2 = AudioObjectGetPropertyData(deviceId, address.ptr, 0u, null, sizeVar.ptr, ids)
//    if (s2.toUInt() != noErr) return emptyList()
//
//    return (0 until count).map { ids[it] }
//}