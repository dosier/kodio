@file:Suppress("unused")

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.windows.MMRESULT
import platform.windows.MMSYSERR_NOERROR
import platform.windows.SW_SHOWNORMAL
import platform.windows.ShellExecuteW
import platform.windows.WAVEFORMATEX
import platform.windows.WAVE_FORMAT_QUERY
import platform.windows.WAVE_MAPPER
import platform.windows.waveInOpen
import kotlin.experimental.ExperimentalNativeApi

/**
 * Checks the system's microphone permission status on Windows.
 *
 * This function is exported for JNI consumption. It attempts a "query" open
 * of a waveform audio input device. If this fails, we infer that permission is denied.
 *
 * @return Int representing the status:
 * - 1: Denied (or no driver/device, which is indistinguishable)
 * - 2: Granted
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("Java_space_kodio_core_JvmAudioPermissionManager_nativeCheckPermission")
fun nativeCheckPermission(): Int = memScoped {
    val waveFormat = alloc<WAVEFORMATEX>()
    // A standard CD-quality format for the check
    with(waveFormat) {
        wFormatTag = 1.toUShort() // WAVE_FORMAT_PCM
        nChannels = 1.toUShort()
        nSamplesPerSec = 44100u
        nAvgBytesPerSec = 88200u
        nBlockAlign = 2.toUShort()
        wBitsPerSample = 16.toUShort()
        cbSize = 0.toUShort()
    }

    // WAVE_FORMAT_QUERY just checks if the operation would be possible,
    // without actually opening the device.
    val result: MMRESULT = waveInOpen(
        phwi = null,
        uDeviceID = WAVE_MAPPER,
        pwfx = waveFormat.ptr,
        dwCallback = 0uL,
        dwInstance = 0uL,
        fdwOpen = WAVE_FORMAT_QUERY.toUInt()
    )

    // If there is no error, we have access.
    // Any error (e.g., access denied, no device) is treated as denied.
    return if (result == MMSYSERR_NOERROR.toUInt()) 2 else 1
}

/**
 * Requests microphone access on Windows.
 *
 * NOTE: Unlike macOS, there is no API for a desktop application to programmatically
 * trigger the system microphone permission prompt on Windows. This function, therefore,
 * does not show a prompt. It simply checks the current permission status, behaving
 * identically to `nativeCheckPermission`.
 *
 * If permission is denied, the application should guide the user to the system's
 * privacy settings, for which `nativeRequestRedirectToSettings` can be used.
 *
 * @return The current permission status: 1 (Denied) or 2 (Granted).
 */
@OptIn(ExperimentalNativeApi::class)
@CName("Java_space_kodio_core_JvmAudioPermissionManager_nativeRequestPermission")
fun nativeRequestPermission(): Int {
    // This function simply checks and returns the current state, as Windows desktop
    // apps cannot trigger the permission prompt programmatically.
    return nativeCheckPermission()
}

/**
 * Opens the Microphone Privacy section in the Windows Settings app.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("Java_space_kodio_core_JvmAudioPermissionManager_nativeRequestRedirectToSettings")
fun nativeRequestRedirectToSettings() {
    ShellExecuteW(
        hwnd = null,
        lpOperation = "open",
        lpFile = "ms-settings:privacy-microphone",
        lpParameters = null,
        lpDirectory = null,
        nShowCmd = SW_SHOWNORMAL
    )
}
