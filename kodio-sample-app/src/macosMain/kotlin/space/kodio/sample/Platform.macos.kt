package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile
import platform.Foundation.NSProcessInfo

actual fun getOpenAIApiKey(): String {
    // On macOS native, API key must be entered manually
    return ""
}

actual fun getPlatformName(): String = "macOS " + NSProcessInfo.processInfo.operatingSystemVersionString

actual fun getFileName(file: PlatformFile): String = file.name
