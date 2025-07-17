package space.kodio.core

import space.kodio.core.security.AudioPermissionManager
import java.io.File

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
        nativeLibraryLoaded = loadNativeLibraryFromJar()
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

@Suppress("UnsafeDynamicallyLoadedCode")
private fun loadNativeLibraryFromJar(): Boolean {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()

    val (libPath, libName) = when {
        "mac" in osName -> {
            val arch = if (osArch == "aarch64") "macos-aarch64" else "macos-x86-64"
            "natives/$arch/libaudiopermissions.dylib" to "libaudiopermissions.dylib"
        }

        "windows" in osName && osArch == "amd64" -> {
            "natives/windows-x86-64/audiopermissions.dll" to "audiopermissions.dll"
        }

        else -> null to null
    }

    if (libPath == null || libName == null) {
        System.err.println("Native permissions library not supported on this platform: $osName ($osArch)")
        return false
    }

    try {
        // The resource path must be absolute from the JAR root
        val fullResourcePath = "/space/kodio/core/$libPath"
        val resourceStream = JvmAudioPermissionManager::class.java.getResourceAsStream(fullResourcePath)
            ?: throw UnsatisfiedLinkError("Cannot find native library in JAR at: $fullResourcePath")

        val tempFile = File.createTempFile("lib", libName)
        tempFile.deleteOnExit()

        resourceStream.use { input -> tempFile.outputStream().use { it.write(input.readBytes()) } }

        System.load(tempFile.absolutePath)
        return true
    } catch (e: Exception) {
        System.err.println("Failed to load native library: ${e.message}")
    }

    return false
}