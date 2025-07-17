package space.kodio.core

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaStreamConstraints

object JsAudioPermissionManager : WebAudioPermissionManager() {

    // Ensures we only request permission once.
    private var permissionGranted: Boolean? = null

    override suspend fun ensurePermissions(): Boolean {
        if (permissionGranted == true) return true
        // Calling getUserMedia is how you trigger the permission prompt.
        try {
            window.navigator.mediaDevices.getUserMedia(MediaStreamConstraints(audio = true)).await()
            permissionGranted = true
            return true
        } catch (e: Exception) {
            console.error("Audio permission denied.", e)
            permissionGranted = false
            return false
        }
    }
}