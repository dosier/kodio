package space.kodio.core

import android.app.Application
import android.content.Context

/**
 * Android-specific Kodio initialization.
 * 
 * Call this in your Application class's onCreate():
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
 */
fun Kodio.initialize(application: Application) {
    AndroidAudioSystem.setApplicationContext(application.applicationContext)
}


