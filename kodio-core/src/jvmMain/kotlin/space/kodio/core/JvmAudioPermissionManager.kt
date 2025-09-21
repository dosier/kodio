package space.kodio.core

import space.kodio.core.security.AudioPermissionManager

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
    }

    override suspend fun requestPermission() {
        if (nativeLibraryLoaded)
            nativeRequestPermission()
        else
            error("Native permissions library not loaded")
    }

    override fun requestRedirectToSettings() {
        if (nativeLibraryLoaded)
            nativeRequestRedirectToSettings()
        else
            throw Error.SettingsRedirectionNotSupported
    }

    override suspend fun checkState(): State = when {
        nativeLibraryLoaded -> when (nativeCheckPermission()) {
            1 -> State.Denied
            2 -> State.Granted
            else -> State.Unknown
        }
        else -> State.Unknown
    }
}