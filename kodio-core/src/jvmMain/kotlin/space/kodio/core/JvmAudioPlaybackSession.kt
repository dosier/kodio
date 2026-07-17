package space.kodio.core

import space.kodio.core.transport.AudioSink
import space.kodio.core.transport.TransportController
import space.kodio.core.util.namedLogger
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = namedLogger("JvmPlayback")

/**
 * JVM playback via JavaSound [SourceDataLine], fed by [PlaybackBuffer] transport.
 *
 * Seek moves the software cursor and [flush]es the line (never [drain]).
 * Published position uses `mediaBase + (lineFrames - lineFramesAtSeekBase)` because
 * [SourceDataLine.getLongFramePosition] is not reset by flush.
 *
 * Hot/live flows stream into a ring; they are never fully collected before play.
 */
class JvmAudioPlaybackSession(private val device: AudioDevice.Output) : BaseAudioPlaybackSession() {

    private var dataLine: SourceDataLine? = null
    private var preparedFormat: AudioFormat? = null
    private var lineFramesAtSeekBase: Long = 0L
    private var sink: JavaSoundSink? = null

    override fun useTransportPlayback(): Boolean = true

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        closeLine()
        val mixer = getMixer(device)
        val playbackFormat = format
            .takeIf { mixer.isSupported<SourceDataLine>(it) }
            ?: device.formatSupport.defaultFormat
        val line = mixer.getLine<SourceDataLine>(playbackFormat)
        line.open(playbackFormat)
        line.start()
        dataLine = line
        preparedFormat = playbackFormat
        lineFramesAtSeekBase = line.longFramePosition
        logger.debug { "preparePlayback: opened SourceDataLine format=$playbackFormat" }
        return playbackFormat
    }

    override fun createAudioSink(format: AudioFormat): AudioSink {
        val created = JavaSoundSink()
        sink = created
        return created
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        // Transport path never calls this.
        error("JvmAudioPlaybackSession uses transport playback")
    }

    override fun readDeviceMediaPosition(
        mediaBase: Duration,
        transport: TransportController,
    ): Duration {
        val line = dataLine ?: return mediaBase
        val framesSinceSeek = (line.longFramePosition - lineFramesAtSeekBase).coerceAtLeast(0L)
        val rate = preparedFormat?.sampleRate ?: transport.buffer.format.sampleRate
        if (rate <= 0) return mediaBase
        return mediaBase + (framesSinceSeek.toDouble() / rate).seconds
    }

    override fun onTransportSeekApplied(position: Duration) {
        val line = dataLine
        // flush already requested via sink; refresh baseline after seek.
        lineFramesAtSeekBase = line?.longFramePosition ?: lineFramesAtSeekBase
        logger.debug {
            "onTransportSeekApplied pos=$position lineFramesAtSeekBase=$lineFramesAtSeekBase"
        }
    }

    override fun onTransportPaused() {
        dataLine?.stop()
    }

    override fun onTransportResumed() {
        dataLine?.start()
    }

    override fun onTransportStopped() {
        closeLine()
        sink = null
    }

    override suspend fun ensureDeviceReadyForTransport(format: AudioFormat): AudioFormat {
        if (dataLine?.isOpen == true) return preparedFormat ?: format
        return preparePlayback(format)
    }

    override fun onPause() = onTransportPaused()
    override fun onResume() = onTransportResumed()
    override fun onStop() = onTransportStopped()

    private fun closeLine() {
        val line = dataLine ?: return
        runCatching { line.stop() }
        runCatching { line.flush() }
        runCatching { line.close() }
        dataLine = null
        preparedFormat = null
        lineFramesAtSeekBase = 0L
    }

    private inner class JavaSoundSink : AudioSink {
        override fun write(
            data: ByteArray,
            offset: Int,
            length: Int,
            seekEpoch: Long,
            mediaByteOffset: Long,
        ) {
            val line = dataLine ?: return
            var written = 0
            while (written < length) {
                // Cooperative pause: wait without holding a device lock forever.
                // Transport feed loop also awaits pause; this covers blocking write splits.
                val n = line.write(data, offset + written, length - written)
                if (n <= 0) break
                written += n
            }
        }

        override fun flush(seekEpoch: Long) {
            val line = dataLine ?: return
            val wasRunning = line.isRunning || line.isActive
            runCatching { line.stop() }
            runCatching { line.flush() }
            lineFramesAtSeekBase = line.longFramePosition
            // Resume only if session still wants playback (seek while paused stays stopped).
            if (wasRunning && state.value is AudioPlaybackSession.State.Playing) {
                runCatching { line.start() }
            }
            logger.debug { "JavaSoundSink.flush epoch=$seekEpoch wasRunning=$wasRunning" }
        }
    }
}
