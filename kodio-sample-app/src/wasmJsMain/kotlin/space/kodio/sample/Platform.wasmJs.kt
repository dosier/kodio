package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile

actual fun getOpenAIApiKey(): String {
    // On WASM, API key must be entered manually
    return ""
}

actual fun getPlatformName(): String = "WebAssembly"

actual fun getFileName(file: PlatformFile): String {
    // FileKit's PlatformFile.name is not directly available in wasmJs
    // Return a placeholder since file upload doesn't work in browser anyway
    return "audio_file"
}
