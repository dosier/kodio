@file:Suppress("unused")

import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import kotlin.experimental.ExperimentalNativeApi

/**
 * Checks the system's microphone permission status on macOS.
 * @return Int representing the status: 0 (NotDetermined), 1 (Denied/Restricted), 2 (Granted)
 */
@OptIn(ExperimentalNativeApi::class)
@CName("Java_space_kodio_core_JvmAudioPermissionManager_nativeCheckPermission")
fun nativeCheckPermission(): Int {
    val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
    return when (status) {
        AVAuthorizationStatusNotDetermined -> 0
        AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> 1
        AVAuthorizationStatusAuthorized -> 2
        else -> 1 // Default to denied
    }
}

/**
 * Requests microphone access. This will trigger the native OS prompt ONLY if
 * the permission status is "Not Determined". It then waits for the user to respond.
 * @return The new permission status after the request: 1 (Denied), 2 (Granted).
 */
@OptIn(ExperimentalNativeApi::class)
@CName("Java_space_kodio_core_JvmAudioPermissionManager_nativeRequestPermission")
fun nativeRequestPermission(): Int {
    // If permission is already decided, do nothing and return the current status.
    val initialStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
    if (initialStatus != AVAuthorizationStatusNotDetermined)
        return if (initialStatus == AVAuthorizationStatusAuthorized) 2 else 1

    // Use a semaphore to wait for the async callback from a sync JNI function.
    val semaphore = dispatch_semaphore_create(value = 0)
    var grantedState = false

    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
        grantedState = granted
        dispatch_semaphore_signal(semaphore)
    }

    dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER) // Block here until user responds.

    return if (grantedState) 2 else 1
}

/**
 * Opens the Microphone Privacy section in System Settings.
 */
@OptIn(ExperimentalNativeApi::class)
@CName("Java_space_kodio_core_JvmAudioPermissionManager_nativeRequestRedirectToSettings")
fun nativeRequestRedirectToSettings() {
    val url = NSURL.URLWithString("x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone")
    if (url != null)
        NSWorkspace.sharedWorkspace.openURL(url)
}