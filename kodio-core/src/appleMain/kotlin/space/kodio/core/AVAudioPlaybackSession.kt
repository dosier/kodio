package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioPlayerNode
import space.kodio.core.io.toIosAudioBuffer
import space.kodio.core.util.namedLogger

private val log = namedLogger("AVAudioPlayback")

abstract class AVAudioPlaybackSession() : BaseAudioPlaybackSession() {
    
    private val engine = AVAudioEngine()
    private val mixer = AVAudioMixerNode()
    private val player = AVAudioPlayerNode()

    init {
        engine.attachNode(mixer)
        engine.attachNode(player)
        log.info { "Attached mixer and player nodes to engine" }
    }

    abstract fun configureAudioSession()

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        log.info { "preparePlayback() called with format: $format" }

        val playbackFormat = toNativePlaybackFormat(format)
        if (playbackFormat != format) {
            log.info { "Format not natively supported by AVAudioFormat, will convert to: $playbackFormat" }
        }

        val avFormat = playbackFormat.toAVAudioFormat()
        log.info {
            "Converted to AVAudioFormat: sampleRate=${avFormat.sampleRate}, " +
                "channels=${avFormat.channelCount}, commonFormat=${avFormat.commonFormat}, " +
                "interleaved=${avFormat.isInterleaved()}"
        }

        val mainMixerOutputFormat = engine.mainMixerNode.outputFormatForBus(0u)
        log.info {
            "mainMixerNode outputFormatForBus(0): sampleRate=${mainMixerOutputFormat.sampleRate}, " +
                "channels=${mainMixerOutputFormat.channelCount}, commonFormat=${mainMixerOutputFormat.commonFormat}"
        }

        log.info { "Connecting player -> mixer with avFormat" }
        engine.connect(player, mixer, avFormat)

        log.info { "Connecting player -> mainMixerNode with null format" }
        engine.connect(player, engine.mainMixerNode, null)

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

    private fun isNativeAVFormat(format: AudioFormat): Boolean = when (val enc = format.encoding) {
        is SampleEncoding.PcmFloat -> true
        is SampleEncoding.PcmInt ->
            enc.bitDepth == IntBitDepth.Sixteen && enc.signed
    }

    private fun toNativePlaybackFormat(format: AudioFormat): AudioFormat {
        if (isNativeAVFormat(format)) return format
        return AudioFormat(
            sampleRate = format.sampleRate,
            channels = format.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        log.info { "playBlocking() called with format: ${audioFlow.format}" }
        player.play()
        val iosAudioFormat = audioFlow.format.toAVAudioFormat()
        log.info {
            "Scheduling buffers with AVAudioFormat: sampleRate=${iosAudioFormat.sampleRate}, " +
                "channels=${iosAudioFormat.channelCount}, commonFormat=${iosAudioFormat.commonFormat}"
        }
        var bufferCount = 0
        val lastCompletable = audioFlow.map { bytes ->
            bufferCount++
            val iosAudioBuffer = bytes.toIosAudioBuffer(iosAudioFormat)
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
        engine.disconnectNodeOutput(mixer)
        log.info { "Engine stopped and nodes disconnected" }
    }
}