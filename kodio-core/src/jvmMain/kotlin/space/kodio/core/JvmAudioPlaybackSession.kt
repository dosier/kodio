package space.kodio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import space.kodio.core.io.collectToByteArray
import space.kodio.core.util.namedLogger
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration

private val logger = namedLogger("JvmPlayback")

/**
 * JVM implementation for [AudioPlaybackSession] using JavaSound [SourceDataLine].
 *
 * Finite PCM is materialized once per [playBlocking] so [onSeek] can reposition
 * a frame-aligned read cursor and flush the line without Compose-level window reloads.
 *
 * @param device The output device to play to.
 */
class JvmAudioPlaybackSession(private val device: AudioDevice.Output) : BaseAudioPlaybackSession() {

    private val isPaused = MutableStateFlow(false)

    private var dataLine: SourceDataLine? = null

    private var pcmData: ByteArray? = null
    private var pcmFormat: AudioFormat? = null

    /** Frame-aligned byte offset for the next write (and for pre-play seeks). */
    private val seekOffsetBytes = AtomicInteger(0)

    /** Bumped on each seek so the write loop restarts from [seekOffsetBytes]. */
    private val seekEpoch = AtomicInteger(0)

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        val mixer = getMixer(device)
        val playbackFormat = format
            .takeIf { mixer.isSupported<SourceDataLine>(it) }
            ?: device.formatSupport.defaultFormat
        val line = mixer.getLine<SourceDataLine>(playbackFormat)
        line.open(playbackFormat)
        line.start()
        this.dataLine = line
        return playbackFormat
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        val line = dataLine ?: return
        val data = audioFlow.collectToByteArray()
        pcmData = data
        pcmFormat = audioFlow.format

        var epoch = seekEpoch.get()
        var offset = seekOffsetBytes.get().coerceIn(0, data.size)

        while (offset < data.size) {
            isPaused.first { !it }
            val currentEpoch = seekEpoch.get()
            if (currentEpoch != epoch) {
                epoch = currentEpoch
                offset = seekOffsetBytes.get().coerceIn(0, data.size)
                runCatching { line.flush() }
                continue
            }

            val remaining = data.size - offset
            if (remaining <= 0) break

            // Write in modest chunks; keep frame alignment when possible.
            val bpf = audioFlow.format.bytesPerFrame.coerceAtLeast(1)
            var chunk = minOf(4096, remaining)
            chunk -= chunk % bpf
            if (chunk <= 0) chunk = remaining

            val written = line.write(data, offset, chunk)
            if (written <= 0) break
            offset += written
        }

        // Only drain when we naturally reached the end (not mid-seek stop).
        if (seekEpoch.get() == epoch && offset >= data.size) {
            line.drain()
        }
        line.stop()
        line.close()
        dataLine = null
    }

    override fun onSeek(position: Duration): SeekResult {
        val format = pcmFormat ?: audioFlow.value?.format
        if (format == null) return SeekResult.Unsupported

        val data = pcmData
        val byteCount = data?.size ?: 0
        val offset = if (data != null) {
            pcmSeekByteOffset(position, format, byteCount)
        } else {
            // Pre-buffer seek: convert duration using format; apply when playBlocking runs.
            val frames = position.toSampleFrame(format.sampleRate)
            val bpf = format.bytesPerFrame
            if (bpf <= 0) 0 else (frames * bpf).toInt().coerceAtLeast(0)
        }

        seekOffsetBytes.set(offset)
        seekEpoch.incrementAndGet()
        logger.debug { "onSeek(position=$position) -> byteOffset=$offset (buffered=${data != null})" }
        runCatching { dataLine?.flush() }
        return SeekResult.Applied
    }

    override fun onPause() {
        dataLine?.stop()
        isPaused.value = true
    }

    override fun onResume() {
        isPaused.value = false
        dataLine?.start()
    }

    override fun onStop() {
        isPaused.value = false
        seekEpoch.incrementAndGet()
        seekOffsetBytes.set(0)
        pcmData = null
        pcmFormat = null
        dataLine?.stop()
        dataLine?.flush()
        dataLine?.close()
        dataLine = null
    }
}
