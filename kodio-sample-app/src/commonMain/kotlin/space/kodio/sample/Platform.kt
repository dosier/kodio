package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile

/**
 * Get the OpenAI API key from platform-specific storage.
 * On JVM/Desktop, this reads from system properties (set via local.properties).
 * On other platforms, returns empty string (user must enter manually).
 */
expect fun getOpenAIApiKey(): String

/**
 * Get the current platform name for display purposes.
 */
expect fun getPlatformName(): String

/**
 * Get the file name from a PlatformFile.
 * This is needed because PlatformFile properties may be accessed differently on some platforms.
 */
expect fun getFileName(file: PlatformFile): String

/**
 * KMP-compatible formatting for decimals.
 */
fun formatDecimal(value: Double, decimals: Int): String {
    // Calculate 10^decimals without using pow
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    
    val rounded = kotlin.math.round(value * factor) / factor
    val str = rounded.toString()
    
    // Handle the decimal part
    val parts = str.split(".")
    return if (parts.size == 1) {
        str + "." + "0".repeat(decimals)
    } else {
        val decimalPart = parts[1]
        if (decimalPart.length >= decimals) {
            parts[0] + "." + decimalPart.take(decimals)
        } else {
            parts[0] + "." + decimalPart + "0".repeat(decimals - decimalPart.length)
        }
    }
}
