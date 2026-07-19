package space.kodio.core

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFStringEncodingMacRoman
import platform.darwin.OSStatus
import platform.darwin.noErr

internal fun runAndCheckOsStatus(op: () -> OSStatus): Unit =
    checkOsStatus(op(), op.toString())

@OptIn(ExperimentalForeignApi::class)
internal fun checkOsStatus(status: OSStatus, op: String) {
    if (status.toUInt() != noErr) {
        error("$op failed with OSStatus=$status (${describeOsStatus(status)})")
    }
}

/** Human-readable hint for common AudioToolbox / CoreAudio OSStatus values. */
internal fun describeOsStatus(status: OSStatus): String = when (status) {
    -50 -> "paramErr / kAudio_ParamError (invalid ASBD or argument)"
    -10863 -> "kAudioQueueErr_InvalidBuffer"
    -10864 -> "kAudioQueueErr_BufferEmpty"
    -10865 -> "kAudioQueueErr_DisposalPending"
    -10866 -> "kAudioQueueErr_InvalidDevice"
    -10867 -> "kAudioQueueErr_InvalidRunState"
    -10868 -> "kAudioQueueErr_InvalidQueueType"
    -10869 -> "kAudioQueueErr_Permissions"
    -10870 -> "kAudioQueueErr_InvalidParameter"
    -10871 -> "kAudioQueueErr_CannotStart"
    -10872 -> "kAudioQueueErr_InvalidProperty"
    -10873 -> "kAudioQueueErr_InvalidPropertySize"
    -10874 -> "kAudioQueueErr_AlreadyRunning"
    -10875 -> "kAudioQueueErr_NotRunning"
    -10876 -> "kAudioQueueErr_BufferEnqueuedTwice"
    -10877 -> "kAudioQueueErr_EnqueueDuringReset"
    -10878 -> "kAudioQueueErr_InvalidOfflineMode"
    -10879 -> "kAudioQueueErr_QueueInvalidated"
    else -> "see AudioToolbox/CoreAudio OSStatus"
}

/**
 * Convert CFString to Kotlin String (UTF-8).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun MemScope.cfStringToKString(cf: CFStringRef): String {
    val length = CFStringGetLength(cf)
    val maxSize = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingMacRoman) + 1
    val buffer = allocArray<ByteVar>(maxSize.convert())
    val ok = CFStringGetCString(cf, buffer, maxSize.convert(), kCFStringEncodingMacRoman)
    return if (ok) buffer.toKString() else ""
}

@OptIn(ExperimentalForeignApi::class)
internal fun String.toCFString(): CFStringRef {
    val cf = CFStringCreateWithCString(null, this, kCFStringEncodingMacRoman)
    return cf ?: throw IllegalStateException("Could not convert to CFString")
}