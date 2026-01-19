package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile
import platform.UIKit.UIDevice

actual fun getOpenAIApiKey(): String {
    // On iOS, API key must be entered manually
    return ""
}

actual fun getPlatformName(): String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

actual fun getFileName(file: PlatformFile): String = file.name
