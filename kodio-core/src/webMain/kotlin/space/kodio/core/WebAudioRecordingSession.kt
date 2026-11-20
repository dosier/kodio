package space.kodio.core

import js.array.toList
import js.typedarrays.Float32Array
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import web.audio.*
import web.events.EventHandler
import web.mediadevices.getUserMedia
import web.mediastreams.MediaStream
import web.messaging.MessageEvent
import web.navigator.navigator
import space.kodio.core.util.namedLogger
import web.worklets.addModule

private val logger = namedLogger("WebRecording")

class WebAudioRecordingSession(
    private val device: AudioDevice.Input?,
    private val format: AudioFormat = DefaultWebRecordingAudioFormat,
    private val inputProcessing: InputProcessing = InputProcessing.PlatformDefault,
) : BaseAudioRecordingSession() {

    private var audioContext: AudioContext? = null
    private var mediaStreamSource: MediaStreamAudioSourceNode? = null
    private var mediaStream: MediaStream? = null
    private var audioWorkletNode: AudioWorkletNode? = null // Replaces ScriptProcessorNode

    private val blobUrl = createCodeBlobUrl(AUDIO_PROCESSOR_CODE)

    override suspend fun prepareRecording(): AudioFormat {
        val requestedDeviceId = device?.id?.takeIf { it.isNotEmpty() }
        logger.debug {
            "prepareRecording: requestedDeviceId=${requestedDeviceId ?: "<none>"}, " +
                "requestedFormat rate=${format.sampleRate} channels=${format.channels.count}"
        }

        val stream = acquireMediaStream(
            deviceId = requestedDeviceId,
            sampleRate = format.sampleRate,
            channelCount = format.channels.count,
        )
        mediaStream = stream

        val inputTrack = stream.getAudioTracks().toList().firstOrNull()
            ?: throw AudioError.DeviceError("getUserMedia returned no audio tracks")
        val settings = inputTrack.getSettings()
        val actualSampleRate = settings.sampleRate ?: format.sampleRate

        logger.debug {
            "Track ready: id=${inputTrack.id}, label='${inputTrack.label}', " +
                "readyState=${inputTrack.readyState}, enabled=${inputTrack.enabled}, muted=${inputTrack.muted}, " +
                "settings.deviceId=${settings.deviceId?.take(12)}, rate=${settings.sampleRate}, " +
                "channels=${settings.channelCount}, sampleSize=${settings.sampleSize}, " +
                "echoCancellation=${settings.echoCancellation}, noiseSuppression=${settings.noiseSuppression}, " +
                "autoGainControl=${settings.autoGainControl}"
        }
        if (!inputTrack.enabled) {
            logger.warn { "Audio track is disabled; enabling before capture" }
            inputTrack.enabled = true
        }
        if (inputTrack.muted) {
            logger.warn { "Audio track reports muted=true (often transient until hardware starts)" }
        }

        val finalFormat = AudioFormat(
            sampleRate = actualSampleRate,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen, Endianness.Little, SampleLayout.Interleaved, signed = true)
        )
        val context = AudioContext(
            contextOptions = createAudioContextOptions(
                latencyHint = AudioContextLatencyCategory.interactive,
                sampleRate = finalFormat.sampleRate
            )
        )
        audioContext = context

        logger.debug {
            "AudioContext created: state=${context.state}, sampleRate=${context.sampleRate}, finalFormat=$finalFormat"
        }

        // Autoplay policies leave new contexts suspended; without resume(), the
        // AudioWorklet process() callback never runs and capture stays silent.
        if (context.state != AudioContextState.running) {
            logger.debug { "Resuming AudioContext from state=${context.state}" }
            context.resume()
            logger.debug { "AudioContext after resume: state=${context.state}" }
        }

        context.audioWorklet.addModule(blobUrl)
        return finalFormat
    }

    private suspend fun acquireMediaStream(
        deviceId: String?,
        sampleRate: Int,
        channelCount: Int,
    ): MediaStream {
        val raw = inputProcessing == InputProcessing.Raw
        val primary = createMediaTrackConstraints(
            deviceId = deviceId,
            sampleRate = sampleRate,
            channelCount = channelCount,
            raw = raw,
        )
        logger.debug {
            "getUserMedia primary: deviceId=${deviceId?.let { "{exact:${it.take(12)}}" } ?: "<omitted>"}, " +
                "sampleRate.ideal=$sampleRate, channelCount.ideal=$channelCount, inputProcessing=$inputProcessing"
        }
        return try {
            navigator.mediaDevices.getUserMedia(createMediaStreamConstraints(audio = primary)).also {
                logger.debug { "getUserMedia succeeded with ${it.getAudioTracks().toList().size} audio track(s)" }
            }
        } catch (e: Throwable) {
            if (deviceId == null) {
                logger.error(e) { "getUserMedia failed for system default" }
                throw e
            }
            // Chrome 130+ may reject stale exact IDs; fall back to browser default.
            logger.warn(e) {
                "getUserMedia with exact deviceId failed; retrying without deviceId constraint"
            }
            val fallback = createMediaTrackConstraints(
                deviceId = null,
                sampleRate = sampleRate,
                channelCount = channelCount,
                raw = raw,
            )
            try {
                navigator.mediaDevices.getUserMedia(createMediaStreamConstraints(audio = fallback)).also {
                    logger.debug { "getUserMedia fallback (no deviceId) succeeded" }
                }
            } catch (fallbackError: Throwable) {
                logger.error(fallbackError) { "getUserMedia fallback also failed" }
                throw fallbackError
            }
        }
    }

    private var chunkCount = 0

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val context = audioContext ?: return
        val stream = mediaStream ?: return
        chunkCount = 0

        if (context.state != AudioContextState.running) {
            logger.debug { "startRecording: resuming AudioContext from state=${context.state}" }
            context.resume()
        }

        val workletNode = createAudioWorkletNode(context, "pcm-recorder-processor")
        workletNode.port.onmessage = EventHandler { event ->
            try {
                val pcmData = (event as MessageEvent).data as Float32Array<*>
                chunkCount++
                val byteData = pcmData.encodeAs16BitPcmByteArray()
                if (chunkCount <= 5 || chunkCount % 200 == 0) {
                    logger.debug {
                        "Chunk #$chunkCount: samples=${pcmData.length}, bytes=${byteData.size}, " +
                            "format=pcm16le mono @ ${context.sampleRate}Hz"
                    }
                }
                val sendResult = channel.trySend(byteData)
                if (sendResult.isFailure) {
                    logger.warn { "Failed to emit ${byteData.size} bytes" }
                }
            } catch (e: Throwable) {
                logger.error(e) { "Worklet message handling failed" }
            }
        }
        this.audioWorkletNode = workletNode
        mediaStreamSource = context.createMediaStreamSource(stream)
        mediaStreamSource?.connect(workletNode)
        // Keep the worklet in a live graph. Outputs are unused (silence) so this
        // does not monitor the microphone through speakers.
        workletNode.connect(context.destination)
        logger.debug {
            "Capture graph connected: MediaStreamSource -> AudioWorklet -> destination, " +
                "context.state=${context.state}"
        }
    }

    override fun cleanup() {
        mediaStreamSource?.disconnect()
        val node = audioWorkletNode
        val stream = mediaStream
        val context = audioContext
        node?.port?.postMessage(createWorkletFlushMessage())
        scope.launch {
            delay(50)
            node?.disconnect()
            node?.port?.close()
            stream?.getTracks()?.toList()?.forEach { it.stop() }
            context?.close()
        }
        audioContext = null
        mediaStream = null
        mediaStreamSource = null
        audioWorkletNode = null
    }

    override suspend fun pauseRecording() {
        // Native pause: disconnect the source from the worklet so PCM frames
        // stop arriving, but keep the AudioContext, MediaStream and worklet
        // alive so resume can simply reconnect.
        mediaStreamSource?.disconnect()
    }

    override suspend fun resumeRecording() {
        val context = audioContext
        val source = mediaStreamSource
        val node = audioWorkletNode
        if (context != null && source != null && node != null) {
            if (context.state != AudioContextState.running) {
                context.resume()
            }
            source.connect(node)
        } else {
            super.resumeRecording()
        }
    }
}
