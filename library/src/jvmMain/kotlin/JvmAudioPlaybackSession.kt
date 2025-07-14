import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.sound.sampled.SourceDataLine

/**
 * JVM implementation for [AudioPlaybackSession].
 *
 * @param device The output device to play to.
 */
class JvmAudioPlaybackSession(private val device: AudioDevice.Output) : AudioPlaybackSession {

    private val _state = MutableStateFlow<AudioPlaybackState>(AudioPlaybackState.Idle)
    override val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private val isPaused = MutableStateFlow(false)

    private var playbackJob: Job? = null
    private var dataLine: SourceDataLine? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun play(audioFlow: AudioFlow) {
        if (_state.value == AudioPlaybackState.Playing) return
        val inputFormat = audioFlow.format
        try {
            // Correctly get the Mixer instance first
            val mixer = getMixer(device)
            val playbackFormat = inputFormat
                .takeIf { mixer.isSupported<SourceDataLine>(it) }
                ?: device.formatSupport.defaultFormat
//            PCM_SIGNED 48000.0 Hz, 16 bit, stereo, 4 bytes/frame, little-endian
            println(playbackFormat.toJvmAudioFormat())
            val line = mixer.getLine<SourceDataLine>(playbackFormat)
            line.open(playbackFormat)
            line.start()
            this.dataLine = line

            _state.value = AudioPlaybackState.Playing

            playbackJob = scope.launch {
                audioFlow
                    .convertAudio(playbackFormat)
                    .collect { buffer ->
                        isPaused.first { !it } // blocks until false
                        line.write(buffer, 0, buffer.size)
                    }
                line.drain()
                line.stop()
                line.close()
                _state.value = AudioPlaybackState.Finished
            }
        } catch (e: Exception) {
            _state.value = AudioPlaybackState.Error(e)
        }
    }

    override fun pause() {
        if (_state.value != AudioPlaybackState.Playing) return
        dataLine?.stop()
        isPaused.value = true
        _state.value = AudioPlaybackState.Paused
    }

    override fun resume() {
        if (_state.value != AudioPlaybackState.Paused) return
        isPaused.value = false
        dataLine?.start()
        _state.value = AudioPlaybackState.Playing
    }

    override fun stop() {
        if (_state.value == AudioPlaybackState.Idle || _state.value == AudioPlaybackState.Finished) return
        playbackJob?.cancel()
        isPaused.value = false
        dataLine?.stop()
        dataLine?.flush()
        dataLine?.close()
        _state.value = AudioPlaybackState.Idle
    }
}