import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
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
        val format = audioFlow.format
        val jvmAudioFormat = format.toJvmAudioFormat()
        try {
            // Correctly get the Mixer instance first
            val mixerInfo = AudioSystem.getMixerInfo().first { it.name == device.id }
            val mixer = AudioSystem.getMixer(mixerInfo)

            // Then get the line from the specific mixer
            val dataLineInfo = DataLine.Info(SourceDataLine::class.java, jvmAudioFormat)
            val dataLine = mixer.getLine(dataLineInfo) as SourceDataLine

            this.dataLine = dataLine

            dataLine.open(jvmAudioFormat)
            dataLine.start()
            _state.value = AudioPlaybackState.Playing

            playbackJob = scope.launch {
                audioFlow.collect { buffer ->
                    isPaused.first { !it } // blocks until false
                    dataLine.write(buffer, 0, buffer.size)
                }
                dataLine.drain()
                dataLine.stop()
                dataLine.close()
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