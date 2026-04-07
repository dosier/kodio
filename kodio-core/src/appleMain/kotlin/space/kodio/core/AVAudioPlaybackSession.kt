package space.kodio.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode
import space.kodio.core.io.convertSimple
import space.kodio.core.io.toIosAudioBuffer
import space.kodio.core.util.namedLogger

private val log = namedLogger("AVAudioPlayback")

abstract class AVAudioPlaybackSession() : BaseAudioPlaybackSession() {
    
    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private lateinit var standardAVFormat: AVAudioFormat
    private lateinit var interleavedAVFormat: AVAudioFormat
    private var deinterleaveConverter: AVAudioConverter? = null

    init {
        engine.attachNode(player)
        log.info { "Attached player node to engine" }
    }

    abstract fun configureAudioSession()

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        log.info { "preparePlayback() called with format: $format" }

        val playbackFormat = toNativePlaybackFormat(format)
        if (playbackFormat != format) {
            log.info { "Format not natively supported, will convert to: $playbackFormat" }
        }

        // Interleaved format matching what the conversion pipeline produces
        interleavedAVFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatFloat32,
            sampleRate = playbackFormat.sampleRate.toDouble(),
            channels = playbackFormat.channels.count.toUInt(),
            interleaved = true
        )

        // AVAudioEngine on iOS requires the standard (non-interleaved Float32) format
        standardAVFormat = AVAudioFormat(
            standardFormatWithSampleRate = playbackFormat.sampleRate.toDouble(),
            channels = playbackFormat.channels.count.toUInt()
        )
        log.info {
            "Standard AVAudioFormat: sampleRate=${standardAVFormat.sampleRate}, " +
                "channels=${standardAVFormat.channelCount}, commonFormat=${standardAVFormat.commonFormat}, " +
                "interleaved=${standardAVFormat.isInterleaved()}, isStandard=${standardAVFormat.isStandard()}"
        }

        deinterleaveConverter = if (playbackFormat.channels.count > 1) {
            AVAudioConverter(fromFormat = interleavedAVFormat, toFormat = standardAVFormat).also {
                log.info { "Created AVAudioConverter for interleaved -> non-interleaved conversion" }
            }
        } else null

        val mainMixerOutputFormat = engine.mainMixerNode.outputFormatForBus(0u)
        log.info {
            "mainMixerNode outputFormatForBus(0): sampleRate=${mainMixerOutputFormat.sampleRate}, " +
                "channels=${mainMixerOutputFormat.channelCount}, commonFormat=${mainMixerOutputFormat.commonFormat}"
        }

        log.info { "Connecting player -> mainMixerNode with standard avFormat" }
        engine.connect(player, engine.mainMixerNode, standardAVFormat)

        log.info { "Configuring audio session" }
        configureAudioSession()

        log.info { "Starting engine" }
        runErrorCatching { errorVar ->
            engine.startAndReturnError(errorVar)
        }.onFailure {
            log.error(it) { "Engine failed to start: ${it.message}" }
            throw AVAudioEngineException.FailedToStart(it.message ?: "Unknown error")
        }
        log.info { "Engine started successfully" }
        return playbackFormat
    }

    /**
     * Always normalize to interleaved Float32 for the conversion pipeline.
     * The actual AVAudioEngine connection uses the standard (non-interleaved)
     * format; an AVAudioConverter handles the deinterleaving per-buffer.
     */
    private fun toNativePlaybackFormat(format: AudioFormat): AudioFormat {
        val enc = format.encoding
        if (enc is SampleEncoding.PcmFloat && enc.precision == FloatPrecision.F32) return format
        return AudioFormat(
            sampleRate = format.sampleRate,
            channels = format.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun playBlocking(audioFlow: AudioFlow) {
        log.info { "playBlocking() called with format: ${audioFlow.format}" }
        player.play()
        val converter = deinterleaveConverter
        val bufferFormat = if (converter != null) interleavedAVFormat else standardAVFormat
        log.info {
            "Scheduling buffers: bufferFormat interleaved=${bufferFormat.isInterleaved()}, " +
                "converter=${converter != null}"
        }
        var bufferCount = 0
        val lastCompletable = audioFlow.map { bytes ->
            bufferCount++
            var iosAudioBuffer = bytes.toIosAudioBuffer(bufferFormat)
            if (converter != null) {
                iosAudioBuffer = converter.convertSimple(iosAudioBuffer, standardAVFormat)
            }
            if (bufferCount <= 3 || bufferCount % 50 == 0) {
                log.info { "Scheduling buffer #$bufferCount: ${bytes.size} bytes, ${iosAudioBuffer.frameLength} frames" }
            }
            val iosAudioBufferFinishedIndicator = CompletableDeferred<Unit>()
            player.scheduleBuffer(iosAudioBuffer) {
                iosAudioBufferFinishedIndicator.complete(Unit)
            }
            iosAudioBufferFinishedIndicator
        }.lastOrNull()
        log.info { "Awaiting last buffer (total scheduled: $bufferCount)" }
        lastCompletable?.await()
        log.info { "playBlocking() finished" }
    }

    override fun onPause() {
        log.info { "onPause()" }
        if (player.isPlaying())
            player.pause()
    }

    override fun onResume() {
        log.info { "onResume()" }
        player.play()
    }

    override fun onStop() {
        log.info { "onStop()" }
        if (player.isPlaying())
            player.stop()
        engine.stop()
        engine.disconnectNodeOutput(player)
        log.info { "Engine stopped and nodes disconnected" }
    }
}