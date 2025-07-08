import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import js.typedarrays.Int16Array
import js.typedarrays.Int8Array
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.w3c.dom.MessageEvent
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import web.audio.AudioContext
import web.audio.AudioContextOptions
import web.audio.AudioWorkletNode
import web.audio.MediaStreamAudioSourceNode
import web.events.EventHandler
import web.media.streams.MediaStream
import web.navigator.navigator
class JsRecordingSession(
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
        val blob = Blob(
            blobParts = arrayOf(AUDIO_PROCESSOR_CODE),
            options = BlobPropertyBag(type = "application/javascript")
        )
        blobUrl = URL.createObjectURL(blob)
    }

    override suspend fun start(format: AudioFormat) {
        if (_state.value == RecordingState.Recording) return

        try {
            val mediaConstraints = getMediaConstraints(device, format)
            val stream = navigator.mediaDevices.getUserMedia(mediaConstraints)
            mediaStream = stream

            val inputTrack = stream.getAudioTracks().first()
            val settings = inputTrack.getSettings()
            val actualSampleRate = settings.sampleRate ?: format.sampleRate

            // Our worklet downmixes to mono, so the output format is always 1 channel.
            val finalFormat = AudioFormat(
                sampleRate = actualSampleRate,
                bitDepth = BitDepth.Sixteen, // We still convert to 16-bit PCM in Kotlin
                channels = Channels.Mono   // Worklet guarantees mono output
            )
            _actualFormat.value = finalFormat

            val context = AudioContext(AudioContextOptions(sampleRate = finalFormat.sampleRate.toFloat()))
            audioContext = context

            // 3. Load the worklet from our Blob URL.
            context.audioWorklet.addModule(blobUrl)

            val workletNode = AudioWorkletNode(context, "pcm-recorder-processor")
            // 3. Set up the message listener to receive data from the worklet.
            workletNode.port.onmessage = EventHandler { event ->

                event as MessageEvent
                val pcmData = event.data as Float32Array<*>
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
        mediaStream?.getTracks()?.forEach { it.stop() }

        scope.launch {
            audioContext?.close()
        }

        _state.value = RecordingState.Stopped
    }
}

// Helper to convert Float32 PCM (-1.0 to 1.0) to 16-bit PCM ByteArray
private fun<B : ArrayBufferLike> Float32Array<B>.to16BitPcmByteArray(): ByteArray {
    val pcm16 = Int16Array<B>(this.length)
    for (i in 0 until this.length) {
        val s = kotlin.math.max(-1.0f, kotlin.math.min(1.0f, this[i]))
        pcm16[i] = (s * 32767.0f).toInt().toShort()
    }
    return pcm16.buffer.toByteArray()
}

private fun ArrayBufferLike.toByteArray(): ByteArray =
    Int8Array(this).unsafeCast<ByteArray>()