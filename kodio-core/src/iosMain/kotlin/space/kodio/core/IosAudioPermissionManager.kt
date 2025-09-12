package space.kodio.core

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import space.kodio.core.security.AudioPermissionManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * IMPORTANT: You must add the `NSMicrophoneUsageDescription` key to your Info.plist.
 */
object IosAudioPermissionManager : AudioPermissionManager() {

    private val session get() = AVAudioSession.Companion.sharedInstance()

    /**
     * Suspends until microphone permission is granted or denied.
     */
    override suspend fun requestPermission() {
        val granted = suspendCoroutine { continuation ->
            session.requestRecordPermission { granted ->
                continuation.resume(granted)
            }
        }
        if (granted)
            setState(State.Granted)
        else
            setState(State.Denied)
    }

    override fun requestRedirectToSettings() {
        val openSettingsUrl = NSURL.Companion.URLWithString(UIApplicationOpenSettingsURLString)
            ?: error("Could not create NSURL for ${UIApplicationOpenSettingsURLString}")
        UIApplication.Companion.sharedApplication.openURL(openSettingsUrl)
    }

    override suspend fun checkState(): State = when (session.recordPermission) {
        AVAudioSessionRecordPermissionDenied -> State.Denied
        AVAudioSessionRecordPermissionGranted -> State.Granted
        AVAudioSessionRecordPermissionUndetermined -> State.Unknown
        else -> error("Unknown AVAudioSessionRecordPermission")
    }
}