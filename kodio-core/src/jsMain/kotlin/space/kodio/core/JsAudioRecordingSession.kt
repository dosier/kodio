package space.kodio.core

import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import js.typedarrays.Int16Array
import js.typedarrays.Int8Array
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
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
import kotlin.math.max
import kotlin.math.min

class JsAudioRecordingSession(
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultJsRecordingAudioFormat
) : BaseAudioRecordingSession() {

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

    override suspend fun prepareRecording(): AudioFormat {
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
        val context = AudioContext(AudioContextOptions(sampleRate = finalFormat.sampleRate.toFloat()))
        audioContext = context
        context.audioWorklet.addModule(blobUrl)
        return finalFormat
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val context = audioContext ?: return
        val stream = mediaStream ?: return

        val workletNode = AudioWorkletNode(context, "pcm-recorder-processor")
        workletNode.port.onmessage = EventHandler { event ->
            val pcmData = (event as MessageEvent).data as Float32Array<*>
            val byteData = pcmData.to16BitPcmByteArray()
            channel.trySend(byteData)
        }
        this.audioWorkletNode = workletNode

        mediaStreamSource = context.createMediaStreamSource(stream)
        mediaStreamSource?.connect(workletNode)
        workletNode.connect(context.destination)

        awaitCancellation()
    }

    override fun cleanup() {
        mediaStreamSource?.disconnect()
        audioWorkletNode?.disconnect()
        audioWorkletNode?.port?.close() // Close the message port
        mediaStream?.getTracks()?.forEach { it.stop() }
        scope.launch { audioContext?.close() }
        audioContext = null
        mediaStream = null
        mediaStreamSource = null
        audioWorkletNode = null
    }
}

// Helper to convert Float32 PCM (-1.0 to 1.0) to 16-bit PCM ByteArray
private fun<B : ArrayBufferLike> Float32Array<B>.to16BitPcmByteArray(): ByteArray {
    val pcm16 = Int16Array<B>(this.length)
    for (i in 0 until this.length) {
        val s = max(-1.0f, min(1.0f, this[i]))
        pcm16[i] = (s * 32767.0f).toInt().toShort()
    }
    return pcm16.buffer.toByteArray()
}

private fun ArrayBufferLike.toByteArray(): ByteArray =
    Int8Array(this).unsafeCast<ByteArray>()