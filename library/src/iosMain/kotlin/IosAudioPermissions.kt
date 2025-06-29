import platform.AVFAudio.AVAudioSession
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/**
 * Suspends until microphone permission is granted or denied.
 * IMPORTANT: You must add the `NSMicrophoneUsageDescription` key to your Info.plist.
 */
suspend fun requestMicrophonePermission(): Boolean = suspendCoroutine { continuation ->
    AVAudioSession.sharedInstance().requestRecordPermission { granted ->
        println("Permission granted: $granted")
        continuation.resume(granted)
    }
}