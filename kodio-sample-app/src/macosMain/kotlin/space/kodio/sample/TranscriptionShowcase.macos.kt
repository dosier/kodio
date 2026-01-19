package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// OpenAI Whisper pricing: $0.006 per minute
private const val WHISPER_COST_PER_MINUTE = 0.006

/**
 * Transcribes a file using OpenAI Whisper API on macOS.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun transcribeFile(
    file: PlatformFile,
    apiKey: String
): FileTranscriptionResult = suspendCancellableCoroutine { continuation ->
    val fileBytes = file.readBytes()
    val fileName = file.name
    
    println("[TranscriptionShowcase] Transcribing file: $fileName (${fileBytes.size} bytes)")
    
    val boundary = "----WebKitFormBoundary${NSDate().timeIntervalSince1970.toLong()}"
    val url = NSURL.URLWithString("https://api.openai.com/v1/audio/transcriptions")!!
    
    val request = NSMutableURLRequest.requestWithURL(url).apply {
        setHTTPMethod("POST")
        setValue("Bearer $apiKey", forHTTPHeaderField = "Authorization")
        setValue("multipart/form-data; boundary=$boundary", forHTTPHeaderField = "Content-Type")
    }
    
    // Build multipart body
    val body = NSMutableData()
    
    // File part
    body.appendString("--$boundary\r\n")
    body.appendString("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
    body.appendString("Content-Type: ${guessContentType(fileName)}\r\n")
    body.appendString("\r\n")
    fileBytes.usePinned { pinned ->
        body.appendBytes(pinned.addressOf(0), fileBytes.size.toULong())
    }
    body.appendString("\r\n")
    
    // Model part
    body.appendString("--$boundary\r\n")
    body.appendString("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
    body.appendString("whisper-1\r\n")
    
    // Response format part
    body.appendString("--$boundary\r\n")
    body.appendString("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
    body.appendString("verbose_json\r\n")
    
    // End boundary
    body.appendString("--$boundary--\r\n")
    
    request.setHTTPBody(body)
    
    val session = NSURLSession.sharedSession
    val task = session.dataTaskWithRequest(request) { data, response, error ->
        if (error != null) {
            continuation.resumeWithException(Exception("Network error: ${error.localizedDescription}"))
            return@dataTaskWithRequest
        }
        
        val httpResponse = response as? NSHTTPURLResponse
        val statusCode = httpResponse?.statusCode?.toInt() ?: 0
        
        if (statusCode !in 200..299) {
            val errorText = data?.let { NSString.create(it, NSUTF8StringEncoding) as? String } ?: "Unknown error"
            continuation.resumeWithException(Exception("API error $statusCode: $errorText"))
            return@dataTaskWithRequest
        }
        
        val responseText = data?.let { NSString.create(it, NSUTF8StringEncoding) as? String } ?: ""
        
        println("[TranscriptionShowcase] API response: ${responseText.take(200)}...")
        
        // Parse JSON manually (simple extraction)
        val text = extractJsonString(responseText, "text") ?: ""
        val language = extractJsonString(responseText, "language") ?: "unknown"
        val duration = extractJsonNumber(responseText, "duration") ?: 0.0
        val cost = (duration / 60.0) * WHISPER_COST_PER_MINUTE
        
        continuation.resume(
            FileTranscriptionResult(
                text = text,
                durationSeconds = duration,
                cost = cost,
                language = language
            )
        )
    }
    
    task.resume()
    
    continuation.invokeOnCancellation {
        task.cancel()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSMutableData.appendString(string: String) {
    val data = (string as NSString).dataUsingEncoding(NSUTF8StringEncoding)
    if (data != null) {
        appendData(data)
    }
}
