package space.kodio.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.kodio.core.AudioPlaybackSession.State
import space.kodio.core.transport.AudioSink
import space.kodio.core.transport.TransportController
import space.kodio.core.util.namedLogger
import web.audio.*
import web.events.EventHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private val logger = namedLogger("WebPlayback")

/**
 * Web playback session using [PlaybackBuffer] transport.
 *
 * - Known-finite content under the retention cap: materialize an [AudioBuffer]
 *   and seek with [AudioBufferSourceNode.start] offsets.
 * - Streaming / ring content: short scheduled [AudioBufferSourceNode] slices
 *   from the transport cursor (no SharedArrayBuffer / COOP-COEP required).
 *
 * Hot flows are never drained before playback starts.
 */
class WebAudioPlaybackSession() : BaseAudioPlaybackSession() {

    private var audioContext: AudioContext? = null
    private var audioBuffer: AudioBuffer? = null
    private var currentSource: AudioBufferSourceNode? = null
    private var scheduledSources: MutableList<AudioBufferSourceNode> = mutableListOf()

    private var pendingOffsetSec: Double = 0.0
    private var sourceGeneration: Int = 0
    private var nextSliceStartTime: Double = 0.0
    private var useFiniteAudioBuffer: Boolean = false
    private var finiteSourcePlaying: Boolean = false

    override fun useTransportPlayback(): Boolean = true

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        closeContext()
        val contextOptions = createAudioContextOptions(
            latencyHint = AudioContextLatencyCategory.playback,
            sampleRate = format.sampleRate
        )
        audioContext = AudioContext(contextOptions)
        audioBuffer = null
        currentSource = null
        scheduledSources = mutableListOf()
        sourceGeneration++
        nextSliceStartTime = 0.0
        useFiniteAudioBuffer = false
        finiteSourcePlaying = false
        pendingOffsetSec = 0.0
        return toWebPlaybackFormat(format)
    }

    private fun toWebPlaybackFormat(format: AudioFormat): AudioFormat {
        val enc = format.encoding
        if (enc is SampleEncoding.PcmFloat &&
            enc.precision == FloatPrecision.F32 &&
            enc.layout == SampleLayout.Interleaved
        ) return format
        return AudioFormat(
            sampleRate = format.sampleRate,
            channels = format.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
    }

    override fun createAudioSink(format: AudioFormat): AudioSink {
        val known = audioFlow.value?.knownByteCount
        useFiniteAudioBuffer = known != null && known <= bufferConfig.maxBytes
        return WebAudioSink(format)
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        error("WebAudioPlaybackSession uses transport playback")
    }

    override fun readDeviceMediaPosition(
        mediaBase: Duration,
        transport: TransportController,
    ): Duration = transport.buffer.format.durationOfByteOffset(transport.readCursor)

    override fun onTransportSeekApplied(position: Duration) {
        pendingOffsetSec = position.toDouble(DurationUnit.SECONDS)
        if (useFiniteAudioBuffer && audioBuffer != null) {
            when (state.value) {
                is State.Playing, is State.Paused -> startFiniteSourceAt(pendingOffsetSec)
                else -> Unit
            }
        }
        logger.debug { "onTransportSeekApplied pos=$position finite=$useFiniteAudioBuffer" }
    }

    override fun onTransportPaused() {
        scope.launch { audioContext?.suspend() }
    }

    override fun onTransportResumed() {
        scope.launch {
            val context = audioContext ?: return@launch
            ensureContextRunning(context)
            if (useFiniteAudioBuffer && audioBuffer != null && state.value is State.Playing) {
                startFiniteSourceAt(pendingOffsetSec)
            }
        }
    }

    override fun onTransportStopped() {
        sourceGeneration++
        stopAllSources()
        audioBuffer = null
        pendingOffsetSec = 0.0
        useFiniteAudioBuffer = false
        finiteSourcePlaying = false
        closeContext()
    }

    override suspend fun ensureDeviceReadyForTransport(format: AudioFormat): AudioFormat {
        val ctx = audioContext
        if (ctx != null && ctx.state != AudioContextState.closed) {
            ensureContextRunning(ctx)
            return format
        }
        return preparePlayback(format)
    }

    override suspend fun paceAfterTransportWrite(bytesWritten: Int, format: AudioFormat) {
        val context = audioContext ?: return
        if (useFiniteAudioBuffer) {
            // Finite path: AudioBufferSourceNode paces itself; soft-cursor feed
            // advances in realtime-sized steps so position stays sane.
            val bpf = format.bytesPerFrame.coerceAtLeast(1)
            val frames = bytesWritten / bpf
            if (frames > 0 && format.sampleRate > 0) {
                val ms = (frames * 1000L) / format.sampleRate
                if (ms > 0) delay(ms.milliseconds)
            }
            return
        }
        // Streaming: keep ~150ms of scheduled look-ahead.
        val lookAheadSec = 0.15
        var guard = 0
        while (nextSliceStartTime - context.currentTime > lookAheadSec && guard++ < 200) {
            delay(5.milliseconds)
        }
    }

    override fun onPause() = onTransportPaused()
    override fun onResume() = onTransportResumed()
    override fun onStop() = onTransportStopped()

    private fun startFiniteSourceAt(offsetSec: Double) {
        val context = audioContext ?: return
        val buffer = audioBuffer ?: return
        val generation = ++sourceGeneration
        stopAllSources()

        val durationSec = buffer.duration.toDouble()
        val clamped = offsetSec.coerceIn(0.0, durationSec)
        pendingOffsetSec = clamped
        if (clamped >= durationSec) {
            finiteSourcePlaying = false
            return
        }

        scope.launch { ensureContextRunning(context) }

        val source = context.createBufferSource()
        source.buffer = buffer
        source.connect(context.destination)
        source.onended = EventHandler {
            if (generation == sourceGeneration) {
                currentSource = null
                finiteSourcePlaying = false
            }
        }
        source.start(context.currentTime, clamped)
        currentSource = source
        finiteSourcePlaying = true
        logger.debug { "Started finite AudioBufferSourceNode at offset=$clamped gen=$generation" }
    }

    private fun stopAllSources() {
        currentSource?.let { src ->
            src.onended = EventHandler { }
            runCatching { src.stop() }
            runCatching { src.disconnect() }
        }
        currentSource = null
        finiteSourcePlaying = false
        scheduledSources.forEach { src ->
            src.onended = EventHandler { }
            runCatching { src.stop() }
            runCatching { src.disconnect() }
        }
        scheduledSources = mutableListOf()
        nextSliceStartTime = audioContext?.currentTime ?: 0.0
    }

    private fun closeContext() {
        val context = audioContext ?: return
        audioContext = null
        scope.launch { runCatching { context.close() } }
    }

    private suspend fun ensureContextRunning(context: AudioContext) {
        if (context.state != AudioContextState.running) {
            context.resume()
        }
    }

    private inner class WebAudioSink(
        private val format: AudioFormat,
    ) : AudioSink {

        override fun write(
            data: ByteArray,
            offset: Int,
            length: Int,
            seekEpoch: Long,
            mediaByteOffset: Long,
        ) {
            val context = audioContext ?: return
            if (length <= 0) return

            if (useFiniteAudioBuffer) {
                // Mirror the retained PlaybackBuffer into one AudioBuffer (not the
                // feed chunk stream — seek restarts from offset, chunks would duplicate).
                rebuildFiniteAudioBuffer(context)
                if (!finiteSourcePlaying && state.value is State.Playing) {
                    startFiniteSourceAt(pendingOffsetSec)
                }
                return
            }

            val chunk = data.copyOfRange(offset, offset + length)
            scheduleSlice(context, chunk, seekEpoch)
        }

        override fun flush(seekEpoch: Long) {
            sourceGeneration++
            stopAllSources()
            nextSliceStartTime = audioContext?.currentTime ?: 0.0
            logger.debug { "WebAudioSink.flush epoch=$seekEpoch" }
        }

        private fun rebuildFiniteAudioBuffer(context: AudioContext) {
            val transport = currentTransport() ?: return
            val range = transport.buffer.bufferedRange
            if (range.isEmpty()) return
            val size = (range.endInclusive - range.start + 1).toInt()
            if (size <= 0) return
            val merged = ByteArray(size)
            val n = transport.buffer.read(range.start, merged, 0, size)
            if (n <= 0) return
            audioBuffer = context.createBufferFrom(
                format = format,
                data = if (n == size) merged else merged.copyOf(n),
            )
        }

        private fun scheduleSlice(context: AudioContext, chunk: ByteArray, seekEpoch: Long) {
            val transport = currentTransport()
            if (transport != null && transport.seekEpoch != seekEpoch) return

            scope.launch { ensureContextRunning(context) }

            val sliceBuffer = context.createBufferFrom(format = format, data = chunk)
            val source = context.createBufferSource()
            source.buffer = sliceBuffer
            source.connect(context.destination)
            val now = context.currentTime
            val startAt = if (nextSliceStartTime < now) now else nextSliceStartTime
            source.start(startAt)
            nextSliceStartTime = startAt + sliceBuffer.duration
            scheduledSources.add(source)
            source.onended = EventHandler {
                scheduledSources.remove(source)
            }
        }
    }
}
