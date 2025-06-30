import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
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
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.setActive
import platform.Foundation.NSError

class IosRecordingSession(private val device: AudioDevice.Input) : RecordingSession {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _audioDataFlow = MutableSharedFlow<ByteArray>()
    override val audioDataFlow: Flow<ByteArray> = _audioDataFlow.asSharedFlow()

    private val engine = AVAudioEngine()
    private val scope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun start(format: AudioFormat) {
        if (_state.value == RecordingState.Recording) return

        try {
            val audioSession = AVAudioSession.Companion.sharedInstance()
            audioSession.configureCategoryRecord()
            audioSession.activate()
            audioSession.setPreferredInput(device)

            val inputNode = engine.inputNode

            val hardwareIosAudioFormat = inputNode.outputFormatForBus(0u)
            val targetIosAudioFormat = format.toIosAudioFormat()
            val converter = AVAudioConverter(hardwareIosAudioFormat, targetIosAudioFormat)

            inputNode.installTapOnBus(
                bus = 0u,
                bufferSize = 1024u, // A common buffer size
                format = hardwareIosAudioFormat // Tap in the hardware's native format
            ) { buffer, _ ->
                if (buffer == null) return@installTapOnBus
                try {
                    val bufferInTargetFormat = converter.convert(buffer, targetIosAudioFormat)
                    val bufferData = bufferInTargetFormat.toByteArray()
                    if (bufferData != null)
                        scope.launch { _audioDataFlow.emit(bufferData) }
                    else
                        println("Buffer data is null?")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            engine.prepare()
            engine.startAndReturnError(null)
            _state.value = RecordingState.Recording

        } catch (e: Exception) {
            // This requires a proper error handling mapping from NSError
            _state.value = RecordingState.Error(e)
        }
    }

    override fun stop() {
        if (_state.value != RecordingState.Recording) return
        engine.stop()
        engine.inputNode.removeTapOnBus(0u)
        _state.value = RecordingState.Stopped
    }
}


@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun AVAudioSession.configureCategoryRecord() {
    runIosCatching { errorPtr ->
        setCategory(
            category = AVAudioSessionCategoryRecord,
            withOptions = AVAudioSessionCategoryOptions.MAX_VALUE,
            error = errorPtr
        )
    }.onFailure {
        throw IosAudioSessionException.FailedToSetCategory(it.message ?: "Unknown error")
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun AVAudioSession.activate() {
    runIosCatching { errorPtr ->
        setActive(true, error = errorPtr)
    }.onFailure {
        throw IosAudioSessionException.FailedToActivate(it.message ?: "Unknown error")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVAudioSession.setPreferredInput(device: AudioDevice.Input) {
    // Find the port description matching our device
    val portDescription = availableInputs
        ?.filterIsInstance<AVAudioSessionPortDescription>()
        ?.firstOrNull { it.UID == device.id }
    if (portDescription != null) {
        runIosCatching { errorVar ->
            setPreferredInput(portDescription, error = null)
        }.onFailure {
            throw IosAudioSessionException.FailedToSetPreferredInput(device, it.message ?: "Unknown error")
        }
    } else
        throw IosAudioSessionException.InputNotFound(device)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun<T> runIosCatching(block: (CPointer<ObjCObjectVar<NSError?>>) -> T) : Result<T> {
    memScoped {
        val errorVar = alloc<ObjCObjectVar<NSError?>>()
        val result = block(errorVar.ptr)
        val errorValue = errorVar.value
        return if (errorValue != null)
            Result.failure(IosException(errorValue))
        else
            Result.success(result)
    }
}

private class IosException(error: NSError) : Exception(error.localizedDescription)