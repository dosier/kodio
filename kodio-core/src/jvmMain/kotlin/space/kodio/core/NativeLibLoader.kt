package space.kodio.core

import java.io.File

@Suppress("UnsafeDynamicallyLoadedCode")
internal fun loadNativeLibraryFromJar(name: String): Boolean {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()

    val (libPath, libName) = when {
        "mac" in osName -> {
            val arch = if (osArch == "aarch64") "macos-aarch64" else "macos-x86-64"
            "natives/$arch/lib$name.dylib" to "lib$name.dylib"
        }

        "windows" in osName && osArch == "amd64" -> {
            "natives/windows-x86-64/$name.dll" to "$name.dll"
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