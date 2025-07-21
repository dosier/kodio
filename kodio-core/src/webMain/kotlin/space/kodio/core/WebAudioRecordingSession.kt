package space.kodio.core

import js.typedarrays.Float32Array
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import web.audio.*
import web.events.EventHandler
import web.mediadevices.getUserMedia
import web.mediastreams.MediaStream
import web.messaging.MessageEvent
import web.navigator.navigator
import web.worklets.addModule

class WebAudioRecordingSession(
    private val device: AudioDevice.Input?,
    private val format: AudioFormat = DefaultWebRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private var audioContext: AudioContext? = null
    private var mediaStreamSource: MediaStreamAudioSourceNode? = null
    private var mediaStream: MediaStream? = null
    private var audioWorkletNode: AudioWorkletNode? = null // Replaces ScriptProcessorNode

    private val blobUrl = createCodeBlobUrl(AUDIO_PROCESSOR_CODE)

    override suspend fun prepareRecording(): AudioFormat {
        val mediaStreamConstraints = createMediaStreamConstraints(
            audio = createMediaTrackConstraints(
                deviceId = device?.id,
                sampleRate = format.sampleRate,
                sampleSize = format.bitDepth.value,
                channelCount = format.channels.count
            )
        )
        val stream = navigator.mediaDevices.getUserMedia(mediaStreamConstraints)
        mediaStream = stream

        val inputTrack = stream.getAudioTracks().toList().first()
        val settings = inputTrack.getSettings()
        val actualSampleRate = settings.sampleRate ?: format.sampleRate

        // Our worklet downmixes to mono, so the output format is always 1 channel.
        val finalFormat = AudioFormat(
            sampleRate = actualSampleRate,
            bitDepth = BitDepth.Sixteen, // We still convert to 16-bit PCM in Kotlin
            channels = Channels.Mono   // Worklet guarantees mono output
        )
        val context = AudioContext(
            contextOptions = createAudioContextOptions(
                latencyHint = AudioContextLatencyCategory.interactive,
                sampleRate = finalFormat.sampleRate
            )
        )
        audioContext = context
        context.audioWorklet.addModule(blobUrl)
        return finalFormat
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val context = audioContext ?: return
        val stream = mediaStream ?: return

        val workletNode = AudioWorkletNode(context, "pcm-recorder-processor")
        workletNode.port.onmessage = EventHandler { event ->
            try {
                val pcmData = (event as MessageEvent).data as Float32Array<*>
                val byteData = pcmData.encodeAs16BitPcmByteArray()
                channel.trySend(byteData)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        this.audioWorkletNode = workletNode
        mediaStreamSource = context.createMediaStreamSource(stream)
        mediaStreamSource?.connect(workletNode)
        workletNode.connect(context.destination)
    }

    override fun cleanup() {
        mediaStreamSource?.disconnect()
        audioWorkletNode?.disconnect()
        audioWorkletNode?.port?.close() // Close the message port
        mediaStream?.getTracks()?.toList()?.forEach { it.stop() }
        scope.launch { audioContext?.close() }
        audioContext = null
        mediaStream = null
        mediaStreamSource = null
        audioWorkletNode = null
    }
}