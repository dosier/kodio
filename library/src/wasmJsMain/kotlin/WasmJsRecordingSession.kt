import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.khronos.webgl.*
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.math.max
import kotlin.math.min

class WasmJsRecordingSession(
    private val device: AudioDevice.Input
) : RecordingSession {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _audioDataFlow = MutableSharedFlow<ByteArray>()
    override val audioDataFlow: Flow<ByteArray> = _audioDataFlow.asSharedFlow()

    private val _actualFormat = MutableStateFlow<AudioFormat?>(null)
    override val actualFormat: StateFlow<AudioFormat?> = _actualFormat.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    private var audioContext: AudioContext? = null
    private var mediaStreamSource: MediaStreamAudioSourceNode? = null
    private var mediaStream: MediaStream? = null
    private var audioWorkletNode: AudioWorkletNode? = null // Replaces ScriptProcessorNode

    private val blobUrl: String
    init {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val blob = Blob(
            blobParts = arrayOf(AUDIO_PROCESSOR_CODE.toJsString() as JsAny?).toJsArray(),
            options = BlobPropertyBag(type = "application/javascript")
        )
        blobUrl = URL.createObjectURL(blob)
    }

    override suspend fun start(format: AudioFormat) {
        if (_state.value == RecordingState.Recording) return

        try {
            println("Starting recording session...")
            val mediaConstraints = getMediaConstraints(device, format)
            println("mediaConstraints: $mediaConstraints")
            val stream = navigator.mediaDevices.getUserMedia(mediaConstraints).await<MediaStream>()
            println("stream: $stream")
            mediaStream = stream

            val inputTrack = stream.getAudioTracks()[0]
            println("inputTrack: $inputTrack")
            val settings = inputTrack?.getSettings()
            println("settings: $settings")
            val actualSampleRate = settings?.sampleRate ?: format.sampleRate
            println("actualSampleRate: $actualSampleRate")
            // Our worklet downmixes to mono, so the output format is always 1 channel.
            val finalFormat = AudioFormat(
                sampleRate = actualSampleRate,
                bitDepth = 16, // We still convert to 16-bit PCM in Kotlin
                channels = 1   // Worklet guarantees mono output
            )
            println("finalFormat: $finalFormat")
            _actualFormat.value = finalFormat

            val context = AudioContext(
                AudioContextOptions(
                    latencyHint = AudioContextLatencyCategoryInteractive,
                    sampleRate = finalFormat.sampleRate.toJsNumber()
                )
            )
            println("context: $context")
            audioContext = context

            // 3. Load the worklet from our Blob URL.
            context.audioWorklet.addModule(blobUrl).await<Unit>()
            println("context.audioWorklet.addModule($blobUrl)")

            val workletNode = AudioWorkletNode(context, "pcm-recorder-processor")
            println("workletNode: $workletNode")
            // 3. Set up the message listener to receive data from the worklet.
            workletNode.port.onmessage = { event ->

                val pcmData = event.data as Float32Array
                // Convert to ByteArray and emit in a coroutine, off the .main thread.
                val byteData = pcmData.to16BitPcmByteArray()
                scope.launch {
                    _audioDataFlow.emit(byteData)
                }
            }

            // 4. Connect the audio graph: Mic -> Worklet -> Destination
            mediaStreamSource = context.createMediaStreamSource(stream)
            mediaStreamSource?.connect(workletNode)
            workletNode.connect(context.destination) // Still needed for the graph to process

            // --- END OF MIGRATION LOGIC ---

            _state.value = RecordingState.Recording

        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = RecordingState.Error(e)
        }
    }

    override fun stop() {
        if (_state.value != RecordingState.Recording) return

        // Disconnect nodes
        mediaStreamSource?.disconnect()
        audioWorkletNode?.disconnect()
        audioWorkletNode?.port?.close() // Close the message port

        // Stop the microphone track
        mediaStream?.getTracks()?.toList()?.forEach { it.stop() }

        scope.launch {
            audioContext?.close()
        }

        _state.value = RecordingState.Stopped
    }
}

// Helper to convert Float32 PCM (-1.0 to 1.0) to 16-bit PCM ByteArray
private fun Float32Array.to16BitPcmByteArray(): ByteArray {
    val pcm16 = Int16Array(this.length)
    for (i in 0 until this.length) {
        val s = max(-1.0f, min(1.0f, get(i)))
        pcm16[i] = (s * 32767.0f).toInt().toShort()
    }
    return pcm16.buffer.toByteArray()
}

private fun ArrayBuffer.toByteArray(): ByteArray =
    Int8Array(this).toByteArray()