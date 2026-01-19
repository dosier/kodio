package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile

actual fun getOpenAIApiKey(): String {
    return try {
        System.getProperty("openai.api.key", "") ?: ""
    } catch (e: Exception) {
        ""
    }
}

actual fun getPlatformName(): String {
    return try {
        System.getProperty("os.name", "Desktop") ?: "Desktop"
    } catch (e: Exception) {
        "Desktop"
    }
}

actual fun getFileName(file: PlatformFile): String = file.name
