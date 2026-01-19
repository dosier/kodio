package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile

actual fun getOpenAIApiKey(): String {
    // On JS, API key must be entered manually
    return ""
}

actual fun getPlatformName(): String = "JavaScript"

actual fun getFileName(file: PlatformFile): String {
    // FileKit's PlatformFile.name is not directly available in JS
    // Return a placeholder since file upload doesn't work in browser anyway
    return "audio_file"
}
