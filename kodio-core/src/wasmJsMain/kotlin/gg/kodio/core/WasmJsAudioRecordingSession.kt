package gg.kodio.core

import kotlinx.coroutines.await
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.khronos.webgl.*
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.get
import kotlin.math.max
import kotlin.math.min

class WasmJsAudioRecordingSession(
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultWasmJsRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private var audioContext: AudioContext? = null
    private var mediaStreamSource: MediaStreamAudioSourceNode? = null
    private var mediaStream: MediaStream? = null
    private var audioWorkletNode: AudioWorkletNode? = null

    private val blobUrl: String by lazy {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val blob = Blob(
            blobParts = arrayOf(AUDIO_PROCESSOR_CODE.toJsString() as JsAny?).toJsArray(),
            options = BlobPropertyBag(type = "application/javascript")
        )
        URL.createObjectURL(blob)
    }

    override suspend fun prepareRecording(): AudioFormat {
        val mediaConstraints = getMediaConstraints(device, format)
        val stream = navigator.mediaDevices.getUserMedia(mediaConstraints).await<MediaStream>()
        mediaStream = stream

        val inputTrack = stream.getAudioTracks()[0]
        val settings = inputTrack?.getSettings()
        val actualSampleRate = settings?.sampleRate ?: format.sampleRate
        val finalFormat = AudioFormat(
            sampleRate = actualSampleRate,
            bitDepth = BitDepth.Sixteen,
            channels = Channels.Mono
        )

        val context = AudioContext(AudioContextOptions(sampleRate = finalFormat.sampleRate.toJsNumber()))
        audioContext = context
        context.audioWorklet.addModule(blobUrl).await<Unit>()

        return finalFormat
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val context = audioContext ?: return
        val stream = mediaStream ?: return

        val workletNode = AudioWorkletNode(context, "pcm-recorder-processor")
        workletNode.port.onmessage = { event ->
            val pcmData = event.data as Float32Array
            val byteData = pcmData.to16BitPcmByteArray()
            channel.trySend(byteData)
        }
        this.audioWorkletNode = workletNode

        mediaStreamSource = context.createMediaStreamSource(stream)
        mediaStreamSource?.connect(workletNode)
        workletNode.connect(context.destination)

        // Keep the coroutine alive until it's cancelled by stop()
        awaitCancellation()
    }

    override fun cleanup() {
        mediaStreamSource?.disconnect()
        audioWorkletNode?.disconnect()
        audioWorkletNode?.port?.close()
        mediaStream?.getTracks()?.toList()?.forEach { it.stop() }
        scope.launch { audioContext?.close() }
        audioContext = null
        mediaStream = null
        mediaStreamSource = null
        audioWorkletNode = null
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