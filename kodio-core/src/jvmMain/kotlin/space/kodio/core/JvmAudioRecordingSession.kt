package space.kodio.core

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import space.kodio.core.util.namedLogger
import javax.sound.sampled.TargetDataLine

private val logger = namedLogger("JvmRecording")

/**
 * Default warmup window applied to JavaSound captures so the silent priming
 * frames a `TargetDataLine` produces on the first read are dropped before
 * we start emitting audio downstream.
 *
 * Empirically, macbook-class hosts under JavaSound need ~150–250 ms before
 * real audio starts arriving (see GitHub issue #5). 200 ms is conservative
 * enough to swallow that gap on every host we've measured without noticeably
 * trimming user audio.
 */
public const val DefaultJvmRecordingWarmupMillis: Int = 200

/**
 * Tunable warmup applied by [JvmAudioRecordingSession].
 *
 * Override via the system property `kodio.jvm.recording.warmupMillis` (any
 * non-negative integer; `0` disables the warmup drain entirely).
 *
 * See [DefaultJvmRecordingWarmupMillis] and GitHub issue #5.
 */
public val JvmRecordingWarmupMillis: Int by lazy {
    System.getProperty("kodio.jvm.recording.warmupMillis")?.toIntOrNull()?.coerceAtLeast(0)
        ?: DefaultJvmRecordingWarmupMillis
}

/**
 * JVM implementation for [AudioRecordingSession].
 *
 * @param device The input device to record from.
 * @param format The format of the audio data.
 * @param warmupMillis Number of milliseconds of audio to read-and-discard
 *   immediately after `TargetDataLine.start()` so the silent priming frames
 *   the JavaSound stack produces (GitHub issue #5) don't show up at the
 *   beginning of every recording. Set to `0` to disable.
 */
class JvmAudioRecordingSession(
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultJvmRecordingAudioFormat,
    private val warmupMillis: Int = JvmRecordingWarmupMillis,
) : BaseAudioRecordingSession() {

    private var dataLine: TargetDataLine? = null
    private var resolvedFormat: AudioFormat? = null

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
        this.resolvedFormat = audioFormat
        return audioFormat
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val line = dataLine ?: run {
            logger.warn { "startRecording called but dataLine is null!" }
            return
        }
        val recordingFormat = resolvedFormat ?: format
        logger.debug { "startRecording: line.isRunning=${line.isRunning}, line.isOpen=${line.isOpen}, warmupMillis=$warmupMillis" }
        if (!line.isRunning)
            line.start()

        // Drain warmup frames so the inevitable priming silence (GitHub
        // issue #5) doesn't get emitted as actual recording data.
        if (warmupMillis > 0) {
            drainWarmup(line, recordingFormat)
        }

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

    /**
     * Read-and-discard ~[warmupMillis] of audio from [line] before emitting
     * any chunks. This swallows the JavaSound priming silence at the start of
     * a capture without affecting downstream consumers.
     */
    private fun drainWarmup(line: TargetDataLine, fmt: AudioFormat) {
        val bytesPerSecond = fmt.sampleRate * fmt.bytesPerFrame
        if (bytesPerSecond <= 0) return
        val warmupBytes = (bytesPerSecond.toLong() * warmupMillis / 1000L).toInt().coerceAtLeast(0)
        if (warmupBytes == 0) return
        val buf = ByteArray(line.bufferSize.coerceAtLeast(1024))
        var drained = 0
        while (drained < warmupBytes && line.isOpen) {
            val want = (warmupBytes - drained).coerceAtMost(buf.size)
            val n = line.read(buf, 0, want)
            if (n <= 0) break
            drained += n
        }
        logger.debug { "Drained ${drained}B of warmup priming (${warmupMillis}ms target)" }
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

    override suspend fun pauseRecording() {
        // Native pause: TargetDataLine.stop() halts capture but keeps the line
        // open + buffered, so resumeRecording() can call start() to continue
        // from the same device without re-opening or re-acquiring permissions.
        dataLine?.takeIf { it.isOpen && it.isRunning }?.stop()
    }

    override suspend fun resumeRecording() {
        val line = dataLine
        if (line != null && line.isOpen) {
            if (!line.isRunning) line.start()
        } else {
            // Line was closed (e.g. external interruption); fall back to a full
            // re-prepare so resume still works.
            super.resumeRecording()
        }
    }
}
