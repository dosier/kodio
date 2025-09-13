package space.kodio.core

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL
import space.kodio.core.security.AudioPermissionManager

/**
 * IMPORTANT: Add `NSMicrophoneUsageDescription` to your Info.plist (macOS).
 *
 * Uses AVFoundation (AVCaptureDevice) to request/check microphone permission on macOS.
 */
object MacosAudioPermissionManager : AudioPermissionManager() {

    /**
     * Suspends until microphone permission is granted or denied.
     */
    override suspend fun requestPermission() {
        // If status is already determined, short-circuit without showing a prompt.
        when (currentAuthorizationStatus()) {
            AVAuthorizationStatusAuthorized -> {
                setState(State.Granted)
                return
            }
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                setState(State.Denied)
                return
            }
            AVAuthorizationStatusNotDetermined -> {
                // Fall through and actually request below
            }
            else -> {
                // Future-proof: treat unknown as undetermined
            }
        }

        val granted = suspendCoroutine { cont ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { ok ->
                cont.resume(ok)
            }
        }

        if (granted)
            setState(State.Granted)
        else
            setState(State.Denied)
    }

    /**
     * Opens System Settings to the Privacy → Microphone pane (best-effort).
     * Note: Apple doesn't provide a fully stable public deep link; these are commonly used schemes.
     */
    override fun requestRedirectToSettings() {
        val workspace = NSWorkspace.sharedWorkspace()

        // Try direct Microphone privacy pane first (works on modern macOS).
        val primaryUrl = NSURL.URLWithString("x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone")
        val opened = if (primaryUrl != null) workspace.openURL(primaryUrl) else false

        if (!opened) {
            // Fallback: open the general Privacy pane.
            val fallbackUrl = NSURL.URLWithString("x-apple.systempreferences:com.apple.preference.security")
            if (fallbackUrl != null) workspace.openURL(fallbackUrl)
        }
    }

    /**
     * Returns the cached/queried permission state without prompting.
     */
    override suspend fun checkState(): State = when (currentAuthorizationStatus()) {
        AVAuthorizationStatusAuthorized -> State.Granted
        AVAuthorizationStatusDenied,
        AVAuthorizationStatusRestricted -> State.Denied
        AVAuthorizationStatusNotDetermined -> State.Unknown
        else -> State.Unknown
    }

    private fun currentAuthorizationStatus(): Long {
        // Obj-C enum bridges to Kotlin/Native as a platform integer (Long)
        return AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
    }
}