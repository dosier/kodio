package space.kodio.core

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import space.kodio.core.util.namedLogger
import javax.sound.sampled.TargetDataLine

private val logger = namedLogger("JvmRecording")

/**
 * JVM implementation for [AudioRecordingSession].
 *
 * @param device The input device to record from.
 * @param format The format of the audio data.
 */
class JvmAudioRecordingSession(
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultJvmRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private var dataLine: TargetDataLine? = null

    override suspend fun prepareRecording(): AudioFormat {
        logger.debug { "prepareRecording: device=${device.name}, requestedFormat=$format" }
        val mixer = getMixer(device)
        val audioFormat = format
            .takeIf { mixer.isSupported<TargetDataLine>(it) }
            ?: device.formatSupport.defaultFormat
        logger.debug { "Using format: $audioFormat" }
        val line = mixer.getLine<TargetDataLine>(audioFormat)
        logger.debug { "Got line: ${line.lineInfo}, isOpen=${line.isOpen}" }
        if (!line.isOpen)
            line.open(audioFormat)
        logger.debug { "Line opened, bufferSize=${line.bufferSize}" }
        this.dataLine = line
        return audioFormat
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val line = dataLine ?: run {
            logger.warn { "startRecording called but dataLine is null!" }
            return
        }
        logger.debug { "startRecording: line.isRunning=${line.isRunning}, line.isOpen=${line.isOpen}" }
        if (!line.isRunning)
            line.start()
        logger.debug { "Line started, reading audio..." }
        
        val buffer = ByteArray(line.bufferSize / 5)
        var chunkCount = 0
        var totalNonZeroBytes = 0L
        
        while (currentCoroutineContext().isActive && line.isOpen) {
            val bytesRead = line.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                val chunk = buffer.copyOf(bytesRead)
                val nonZeroCount = chunk.count { it != 0.toByte() }
                totalNonZeroBytes += nonZeroCount
                chunkCount++
                
                if (chunkCount <= 3 || chunkCount % 50 == 0) {
                    logger.debug { "Chunk #$chunkCount: bytesRead=$bytesRead, nonZeroBytes=$nonZeroCount, first16=${chunk.take(16).map { it.toInt() and 0xFF }}" }
                }
                
                channel.send(chunk)
            }
        }
        logger.debug { "Recording loop ended: $chunkCount chunks, $totalNonZeroBytes total non-zero bytes" }
    }

    override fun cleanup() {
        logger.debug { "cleanup called" }
        dataLine?.run {
            if (isOpen) {
                stop()
                close()
            }
        }
        dataLine = null
    }
}
