import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.setActive
import platform.Foundation.NSError

class IosRecordingSession(private val device: AudioDevice.Input) : RecordingSession {
    private val _state = MutableStateFlow(RecordingState.IDLE)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _audioDataFlow = MutableSharedFlow<ByteArray>()
    override val audioDataFlow: Flow<ByteArray> = _audioDataFlow.asSharedFlow()

    private val engine = AVAudioEngine()
    private val scope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun start(format: AudioFormat) {
        if (_state.value == RecordingState.RECORDING) return

        try {
            val audioSession = AVAudioSession.Companion.sharedInstance()
            memScoped {
                val err1 = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setCategory(
                    category = AVAudioSessionCategoryPlayAndRecord,
                    withOptions = AVAudioSessionCategoryOptions.MAX_VALUE,
                    error = err1.ptr
                )
                err1.value?.let { error("Failed to set category: ${it.localizedDescription}") }
                val err2 = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setActive(true, error = err2.ptr)
                err2.value?.let { error("Failed to activate session: ${it.localizedDescription}") }
            }

            // Find the port description matching our device
            val portDescription = audioSession.availableInputs
                ?.filterIsInstance<AVAudioSessionPortDescription>()
                ?.firstOrNull { it.UID == device.id }
            if (portDescription != null) {
                audioSession.setPreferredInput(portDescription, error = null)
            }

            val inputNode = engine.inputNode
            val avFormat = inputNode.outputFormatForBus(0u)

            inputNode.installTapOnBus(0u, 1024u, avFormat) { buffer, _ ->
                buffer?.let {
                    val audioBuffer = it.audioBufferList?.pointed?.mBuffers?.pointed
                    val data = audioBuffer?.mData?.readBytes(audioBuffer.mDataByteSize.toInt())
                    if (data != null) {
                        scope.launch { _audioDataFlow.emit(data) }
                    }
                }
            }

            engine.prepare()
            engine.startAndReturnError(null)
            _state.value = RecordingState.RECORDING

        } catch (e: Exception) {
            // This requires a proper error handling mapping from NSError
            _state.value = RecordingState.ERROR
            e.printStackTrace()
        }
    }

    override fun stop() {
        if (_state.value != RecordingState.RECORDING) return
        engine.stop()
        engine.inputNode.removeTapOnBus(0u)
        _state.value = RecordingState.STOPPED
    }
}