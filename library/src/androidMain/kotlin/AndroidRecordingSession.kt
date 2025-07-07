import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
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

@SuppressLint("MissingPermission") // Permissions should be checked before calling.
internal class AndroidRecordingSession(
    private val context: Context,
    private val device: AudioDevice.Input
) : RecordingSession {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _audioDataFlow = MutableSharedFlow<ByteArray>()
    override val audioDataFlow: Flow<ByteArray> = _audioDataFlow.asSharedFlow()

    private val _actualFormat = MutableStateFlow<AudioFormat?>(null)
    override val actualFormat: StateFlow<AudioFormat?> = _actualFormat.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun start(format: AudioFormat) {
        if (_state.value == RecordingState.Recording) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                format.sampleRate,
                format.channels.toAndroidChannelInMask(),
                format.bitDepth.toAndroidFormatEncoding()
            )
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE)
                error("$format is not supported by the device")
            if (minBufferSize == AudioRecord.ERROR)
                error("Failed to get min buffer size")
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(format.sampleRate)
                        .setChannelMask(format.channels.toAndroidChannelInMask())
                        .setEncoding(format.bitDepth.toAndroidFormatEncoding())
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .build()
            this.audioRecord = audioRecord

            println("audiotrack(device=${audioRecord.routedDevice.toOutputDevice()}, format=${audioRecord.format})")
            // Set the preferred device
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val selectedDevice = devices.firstOrNull { it.id.toString() == device.id }
            if (selectedDevice != null)
                audioRecord.preferredDevice = selectedDevice

            audioRecord.startRecording()
            _state.value = RecordingState.Recording

            recordingJob = scope.launch {
                val buffer = ByteArray(minBufferSize)
                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0)
                        _audioDataFlow.emit(buffer.copyOf(read))
                }
            }
        } catch (e: Exception) {
            _state.value = RecordingState.Error(e)
        }
    }

    override fun stop() {
        if (_state.value != RecordingState.Recording) return
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _state.value = RecordingState.Stopped
    }
}