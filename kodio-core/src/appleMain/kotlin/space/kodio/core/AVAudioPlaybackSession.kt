package space.kodio.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode
import space.kodio.core.io.convertSimple
import space.kodio.core.io.toIosAudioBuffer
import space.kodio.core.transport.AudioSink
import space.kodio.core.transport.TransportController
import space.kodio.core.util.namedLogger
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = namedLogger("AVAudioPlayback")

/**
 * Apple AVAudioEngine + AVAudioPlayerNode transport session.
 *
 * Seek hops off the completion queue: [AVAudioPlayerNode.stop] must never run
 * inside a buffer completion handler (documented deadlock). Completions only
 * enqueue "schedule more" work onto the session [scope].
 */
abstract class AVAudioPlaybackSession() : BaseAudioPlaybackSession() {

    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private lateinit var standardAVFormat: AVAudioFormat
    private lateinit var interleavedAVFormat: AVAudioFormat
    private var deinterleaveConverter: AVAudioConverter? = null

    /** Completions post here; never call [AVAudioPlayerNode.stop] from the handler. */
    private val scheduleMore = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var completionScheduleEnabled: Boolean = true

    private var playbackFormat: AudioFormat? = null

    init {
        engine.attachNode(player)
        log.info { "Attached player node to engine" }
        scope.launch {
            for (unit in scheduleMore) {
                // Drain signal; feed loop / prime owns actual scheduling via sink.write.
                log.trace { "scheduleMore signal (completion hop)" }
            }
        }
    }

    abstract fun configureAudioSession()

    override fun useTransportPlayback(): Boolean = true

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        log.info { "preparePlayback() called with format: $format" }

        val normalized = toNativePlaybackFormat(format)
        if (engine.isRunning() && ::standardAVFormat.isInitialized &&
            standardAVFormat.sampleRate == normalized.sampleRate.toDouble() &&
            standardAVFormat.channelCount == normalized.channels.count.toUInt()
        ) {
            playbackFormat = normalized
            return normalized
        }

        interleavedAVFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatFloat32,
            sampleRate = normalized.sampleRate.toDouble(),
            channels = normalized.channels.count.toUInt(),
            interleaved = true
        )
        standardAVFormat = AVAudioFormat(
            standardFormatWithSampleRate = normalized.sampleRate.toDouble(),
            channels = normalized.channels.count.toUInt()
        )
        deinterleaveConverter = if (normalized.channels.count > 1) {
            AVAudioConverter(fromFormat = interleavedAVFormat, toFormat = standardAVFormat)
        } else null

        engine.connect(player, engine.mainMixerNode, standardAVFormat)
        configureAudioSession()
        engine.prepare()
        runErrorCatching { errorVar ->
            engine.startAndReturnError(errorVar)
        }.onFailure {
            log.error(it) { "Engine failed to start: ${it.message}" }
            throw AVAudioEngineException.FailedToStart(it.message ?: "Unknown error")
        }
        playbackFormat = normalized
        completionScheduleEnabled = true
        return normalized
    }

    private fun toNativePlaybackFormat(format: AudioFormat): AudioFormat {
        val enc = format.encoding
        if (enc is SampleEncoding.PcmFloat && enc.precision == FloatPrecision.F32) return format
        return AudioFormat(
            sampleRate = format.sampleRate,
            channels = format.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
    }

    override fun createAudioSink(format: AudioFormat): AudioSink = AvAudioSink()

    @Deprecated("Transport playback", level = DeprecationLevel.HIDDEN)
    override suspend fun playBlocking(audioFlow: AudioFlow) {
        error("AVAudioPlaybackSession uses transport playback")
    }

    override fun readDeviceMediaPosition(
        mediaBase: Duration,
        transport: TransportController,
    ): Duration {
        val nodeTime = player.lastRenderTime ?: return mediaBase
        val playerTime = player.playerTimeForNodeTime(nodeTime) ?: return mediaBase
        if (!playerTime.isSampleTimeValid()) return mediaBase
        val rate = playbackFormat?.sampleRate ?: transport.buffer.format.sampleRate
        if (rate <= 0) return mediaBase
        val sampleTime = playerTime.sampleTime
        if (sampleTime < 0) return mediaBase
        return mediaBase + (sampleTime.toDouble() / rate).seconds
    }

    override fun onTransportSeekApplied(position: Duration) {
        // stop+reschedule is done in sink.flush on the session/API thread.
        log.debug { "onTransportSeekApplied pos=$position" }
    }

    override fun onTransportPaused() {
        if (player.isPlaying()) player.pause()
    }

    override fun onTransportResumed() {
        player.play()
    }

    override fun onTransportStopped() {
        completionScheduleEnabled = false
        runCatching {
            if (player.isPlaying()) player.stop()
        }
        runCatching { engine.stop() }
        runCatching { engine.disconnectNodeOutput(player) }
    }

    override suspend fun ensureDeviceReadyForTransport(format: AudioFormat): AudioFormat {
        if (engine.isRunning()) {
            completionScheduleEnabled = true
            return playbackFormat ?: format
        }
        return preparePlayback(format)
    }

    override fun onPause() = onTransportPaused()
    override fun onResume() = onTransportResumed()
    override fun onStop() = onTransportStopped()

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private inner class AvAudioSink : AudioSink {
        override fun write(
            data: ByteArray,
            offset: Int,
            length: Int,
            seekEpoch: Long,
            mediaByteOffset: Long,
        ) {
            if (length <= 0) return
            val transport = currentTransport()
            if (transport != null && transport.seekEpoch != seekEpoch) return

            val converter = deinterleaveConverter
            val bufferFormat = if (converter != null) interleavedAVFormat else standardAVFormat
            val chunk = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
            var iosAudioBuffer = chunk.toIosAudioBuffer(bufferFormat)
            if (converter != null) {
                iosAudioBuffer = converter.convertSimple(iosAudioBuffer, standardAVFormat)
            }

            // Completion only hops to session queue — never stop() here.
            player.scheduleBuffer(iosAudioBuffer) {
                if (completionScheduleEnabled) {
                    scheduleMore.trySend(Unit)
                }
            }
            if (!player.isPlaying() && state.value is AudioPlaybackSession.State.Playing) {
                player.play()
            }
        }

        override fun flush(seekEpoch: Long) {
            // Session/API queue only (seekTo caller thread / Base seek path).
            completionScheduleEnabled = false
            runCatching { player.stop() } // clears schedule; player time → 0
            completionScheduleEnabled = true
            log.debug { "AvAudioSink.flush epoch=$seekEpoch (stop on session queue)" }
            if (state.value is AudioPlaybackSession.State.Playing) {
                player.play()
            }
        }
    }
}
