package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name

actual fun getOpenAIApiKey(): String {
    // On Android, API key must be entered manually or read from BuildConfig
    return ""
}

actual fun getPlatformName(): String = "Android"

actual fun getFileName(file: PlatformFile): String = file.name
