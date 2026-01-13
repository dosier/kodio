package space.kodio.core

import space.kodio.core.security.AudioPermissionManager
import space.kodio.core.util.namedLogger

private val logger = namedLogger("JvmPermissions")

object JvmAudioPermissionManager : AudioPermissionManager() {

    private var nativeLibraryLoaded = false

    /**
     * Declares the native method implemented in the Kotlin/Native library.
     * The JVM will look for a function with a matching signature in the loaded library.
     * Return values: 0=Unknown/NotDetermined, 1=Denied, 2=Granted.
     */
    private external fun nativeCheckPermission(): Int
    private external fun nativeRequestRedirectToSettings()
    private external fun nativeRequestPermission(): Int

    init {
        // This block runs once when the object is first accessed.
        nativeLibraryLoaded = loadNativeLibraryFromJar("audiopermissions")
        logger.info { "Native permissions library loaded: $nativeLibraryLoaded" }
    }

    override suspend fun requestPermission() {
        logger.debug { "requestPermission() called, nativeLibraryLoaded=$nativeLibraryLoaded" }
        if (nativeLibraryLoaded) {
            val result = nativeRequestPermission()
            logger.debug { "nativeRequestPermission() returned: $result" }
        } else {
            error("Native permissions library not loaded")
        }
    }

    override fun requestRedirectToSettings() {
        if (nativeLibraryLoaded)
            nativeRequestRedirectToSettings()
        else
            throw Error.SettingsRedirectionNotSupported
    }

    override suspend fun checkState(): State {
        val state = when {
            nativeLibraryLoaded -> {
                val nativeResult = nativeCheckPermission()
                logger.debug { "nativeCheckPermission() returned: $nativeResult (0=Unknown, 1=Denied, 2=Granted)" }
                when (nativeResult) {
                    1 -> State.Denied
                    2 -> State.Granted
                    else -> State.Unknown
                }
            }
            else -> {
                logger.warn { "Native library not loaded, returning Unknown" }
                State.Unknown
            }
        }
        logger.info { "Permission state: $state" }
        return state
    }
}