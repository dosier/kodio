package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

// OpenAI Whisper pricing: $0.006 per minute
private const val WHISPER_COST_PER_MINUTE = 0.006

/**
 * Transcribes a file using OpenAI Whisper API on Android.
 */
actual suspend fun transcribeFile(
    file: PlatformFile,
    apiKey: String
): FileTranscriptionResult = withContext(Dispatchers.IO) {
    val fileBytes = file.readBytes()
    val fileName = file.name
    
    println("[TranscriptionShowcase] Transcribing file: $fileName (${fileBytes.size} bytes)")
    
    val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
    val url = URL("https://api.openai.com/v1/audio/transcriptions")
    val connection = url.openConnection() as HttpURLConnection
    
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        
        val outputStream = connection.outputStream
        val baos = ByteArrayOutputStream()
        
        // File part
        baos.write("--$boundary\r\n".toByteArray())
        baos.write("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n".toByteArray())
        baos.write("Content-Type: ${guessContentType(fileName)}\r\n".toByteArray())
        baos.write("\r\n".toByteArray())
        baos.write(fileBytes)
        baos.write("\r\n".toByteArray())
        
        // Model part
        baos.write("--$boundary\r\n".toByteArray())
        baos.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".toByteArray())
        baos.write("whisper-1\r\n".toByteArray())
        
        // Response format part
        baos.write("--$boundary\r\n".toByteArray())
        baos.write("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n".toByteArray())
        baos.write("verbose_json\r\n".toByteArray())
        
        // End boundary
        baos.write("--$boundary--\r\n".toByteArray())
        
        outputStream.write(baos.toByteArray())
        outputStream.flush()
        outputStream.close()
        
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("API error $responseCode: $errorText")
        }
        
        println("[TranscriptionShowcase] API response: ${responseText.take(200)}...")
        
        // Parse JSON manually (simple extraction)
        val text = extractJsonString(responseText, "text") ?: ""
        val language = extractJsonString(responseText, "language") ?: "unknown"
        val duration = extractJsonNumber(responseText, "duration") ?: 0.0
        val cost = (duration / 60.0) * WHISPER_COST_PER_MINUTE
        
        FileTranscriptionResult(
            text = text,
            durationSeconds = duration,
            cost = cost,
            language = language
        )
    } finally {
        connection.disconnect()
    }
}
