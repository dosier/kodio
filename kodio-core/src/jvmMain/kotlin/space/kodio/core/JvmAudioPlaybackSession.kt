package space.kodio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import javax.sound.sampled.SourceDataLine

/**
 * JVM implementation for [AudioPlaybackSession].
 *
 * @param device The output device to play to.
 */
class JvmAudioPlaybackSession(private val device: AudioDevice.Output) : BaseAudioPlaybackSession() {

    private val isPaused = MutableStateFlow(false)

    private var dataLine: SourceDataLine? = null

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
        audioFlow.collect { buffer ->
            isPaused.first { !it } // blocks until false
            line.write(buffer, 0, buffer.size)
        }
        line.drain()
        line.stop()
        line.close()
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
        dataLine?.stop()
        dataLine?.flush()
        dataLine?.close()
    }
}