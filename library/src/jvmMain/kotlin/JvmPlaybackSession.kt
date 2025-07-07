import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * JVM implementation for [PlaybackSession].
 *
 * @param device The output device to play to.
 */
class JvmPlaybackSession(private val device: AudioDevice.Output) : PlaybackSession {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val isPaused = MutableStateFlow(false)

    private var playbackJob: Job? = null
    private var dataLine: SourceDataLine? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun play(dataFlow: Flow<ByteArray>, format: AudioFormat) {
        if (_state.value == PlaybackState.Playing) return

        val jvmAudioFormat = format.toJvmAudioFormat()
        try {
            // Correctly get the Mixer instance first
            val mixerInfo = AudioSystem.getMixerInfo().first { it.name == device.id }
            val mixer = AudioSystem.getMixer(mixerInfo)

            // Then get the line from the specific mixer
            val lineInfo = DataLine.Info(SourceDataLine::class.java, jvmAudioFormat)
            dataLine = mixer.getLine(lineInfo) as SourceDataLine

            dataLine?.let { line ->
                line.open(jvmAudioFormat)
                line.start()
                _state.value = PlaybackState.Playing

                playbackJob = scope.launch {
                    dataFlow.collect { buffer ->
                        isPaused.first { !it } // blocks until false
                        line.write(buffer, 0, buffer.size)
                    }
                    line.drain()
                    line.stop()
                    line.close()
                    _state.value = PlaybackState.Finished
                }
            }
        } catch (e: Exception) {
            _state.value = PlaybackState.Error(e)
        }
    }

    override fun pause() {
        if (_state.value != PlaybackState.Playing) return
        dataLine?.stop()
        isPaused.value = true
        _state.value = PlaybackState.Paused
    }

    override fun resume() {
        if (_state.value != PlaybackState.Paused) return
        isPaused.value = false
        dataLine?.start()
        _state.value = PlaybackState.Playing
    }

    override fun stop() {
        if (_state.value == PlaybackState.Idle || _state.value == PlaybackState.Finished) return
        playbackJob?.cancel()
        isPaused.value = false
        dataLine?.stop()
        dataLine?.flush()
        dataLine?.close()
        _state.value = PlaybackState.Idle
    }
}