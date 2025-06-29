import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

// --- JVM PLAYBACK SESSION IMPLEMENTATION ---
class JvmPlaybackSession(private val device: AudioDevice.Output) : PlaybackSession {
    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var dataLine: SourceDataLine? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun play(audioData: Flow<ByteArray>, format: AudioFormat) {
        if (_state.value == PlaybackState.PLAYING) return

        try {
            // Correctly get the Mixer instance first
            val mixerInfo = AudioSystem.getMixerInfo().first { it.name == device.id }
            val mixer = AudioSystem.getMixer(mixerInfo)

            // Then get the line from the specific mixer
            val lineInfo = DataLine.Info(SourceDataLine::class.java, format.toJvmAudioFormat())
            dataLine = mixer.getLine(lineInfo) as SourceDataLine

            dataLine?.let { line ->
                line.open(format.toJvmAudioFormat())
                line.start()
                _state.value = PlaybackState.PLAYING

                playbackJob = scope.launch {
                    audioData.collect { buffer ->
                        line.write(buffer, 0, buffer.size)
                    }
                    line.drain()
                    line.stop()
                    line.close()
                    _state.value = PlaybackState.FINISHED
                }
            }
        } catch (e: Exception) {
            _state.value = PlaybackState.ERROR
            e.printStackTrace()
        }
    }

    override fun pause() {
        if (_state.value != PlaybackState.PLAYING) return
        dataLine?.stop()
        _state.value = PlaybackState.PAUSED
    }

    override fun resume() {
        if (_state.value != PlaybackState.PAUSED) return
        dataLine?.start()
        _state.value = PlaybackState.PLAYING
    }

    override fun stop() {
        if (_state.value == PlaybackState.IDLE || _state.value == PlaybackState.FINISHED) return
        playbackJob?.cancel()
        dataLine?.stop()
        dataLine?.flush()
        dataLine?.close()
        _state.value = PlaybackState.IDLE
    }
}