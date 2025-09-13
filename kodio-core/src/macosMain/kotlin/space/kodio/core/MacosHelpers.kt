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
    if (status.toUInt() != noErr) error("$op failed with OSStatus=$status")
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