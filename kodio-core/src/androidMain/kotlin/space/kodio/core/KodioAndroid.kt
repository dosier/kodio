package space.kodio.core

import android.app.Activity
import android.app.Application
import android.content.Context

/**
 * Android-specific Kodio initialization.
 *
 * ## From an Activity (recommended)
 *
 * Calling from an Activity sets both the application context and the
 * permission-request activity in one step:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         Kodio.initialize(this)
 *     }
 *
 *     override fun onRequestPermissionsResult(
 *         requestCode: Int, permissions: Array<out String?>,
 *         grantResults: IntArray, deviceId: Int
 *     ) {
 *         Kodio.onRequestPermissionsResult(requestCode, grantResults)
 *     }
 * }
 * ```
 *
 * ## From an Application
 *
 * Sets the application context only. Permission handling must be wired
 * separately via [AndroidAudioPermissionManager.setMicrophonePermissionRequestActivity].
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Kodio.initialize(this)
 *     }
 * }
 * ```
 */
fun Kodio.initialize(context: Context) {
    AndroidAudioSystem.setApplicationContext(context.applicationContext)
}

/**
 * Initialize Kodio with an Application instance.
 *
 * Sets the application context only. For full initialization including
 * permission handling, use the [Activity] overload instead.
 */
fun Kodio.initialize(application: Application) {
    AndroidAudioSystem.setApplicationContext(application.applicationContext)
}

/**
 * Initialize Kodio with an Activity.
 *
 * This sets both the application context and the permission-request activity,
 * so a single call is all that's needed from your Activity's `onCreate`.
 */
fun Kodio.initialize(activity: Activity) {
    AndroidAudioSystem.setApplicationContext(activity.applicationContext)
    AndroidAudioPermissionManager.setMicrophonePermissionRequestActivity(activity)
}

/**
 * Forward the permission callback to Kodio.
 *
 * Call this from your Activity's `onRequestPermissionsResult` so Kodio can
 * track the microphone-permission state:
 * ```kotlin
 * override fun onRequestPermissionsResult(
 *     requestCode: Int, permissions: Array<out String?>,
 *     grantResults: IntArray, deviceId: Int
 * ) {
 *     Kodio.onRequestPermissionsResult(requestCode, grantResults)
 * }
 * ```
 */
fun Kodio.onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
    AndroidAudioPermissionManager.onRequestPermissionsResult(requestCode, grantResults)
}

