import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioSystem as JvmAudioSystem

class JvmRecordingSession(private val device: AudioDevice.Input) : RecordingSession {

    private val _state = MutableStateFlow(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _audioDataFlow = MutableSharedFlow<ByteArray>()
    override val audioDataFlow: Flow<ByteArray> = _audioDataFlow.asSharedFlow()

    private var recordingJob: Job? = null
    private var dataLine: TargetDataLine? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun start(format: AudioFormat) {
        if (_state.value == RecordingState.Recording) return

        try {
            // Correctly get the Mixer instance first
            val mixerInfo = JvmAudioSystem.getMixerInfo().first { it.name == device.id }
            val mixer = JvmAudioSystem.getMixer(mixerInfo)

            // Then get the line from the specific mixer
            val lineInfo = DataLine.Info(TargetDataLine::class.java, format.toJvmAudioFormat())
            dataLine = mixer.getLine(lineInfo) as TargetDataLine

            dataLine?.let { line ->
                line.open(format.toJvmAudioFormat())
                line.start()
                _state.value = RecordingState.Recording

                recordingJob = scope.launch {
                    val buffer = ByteArray(line.bufferSize / 5)
                    while (isActive && line.isOpen) {
                        val bytesRead = line.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            _audioDataFlow.emit(buffer.clone())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _state.value = RecordingState.Error
            // Optionally handle the exception (e.g., logging)
            e.printStackTrace()
        }
    }

    override fun stop() {
        if (_state.value != RecordingState.Recording) return
        recordingJob?.cancel()
        dataLine?.stop()
        dataLine?.close()
        _state.value = RecordingState.Stopped
    }
}

