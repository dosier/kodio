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

    /**
     * Transport seek epoch the finite source was last started for. The feed loop
     * keeps writing after the source is running (its soft cursor is paced with
     * delay() and lags real audio), so writes must not start it again: doing so
     * replayed the whole recording from the start once the source had ended.
     * Only a seek (new epoch) or an explicit restart may start a new source.
     */
    private var finiteStartedForEpoch: Long = NO_EPOCH

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
        finiteStartedForEpoch = NO_EPOCH
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
        // Prefer the converted-flow hint (set by Base before createAudioSink).
        val known = convertedKnownByteCount ?: audioFlow.value?.knownByteCount
        useFiniteAudioBuffer = known != null && known <= bufferConfig.maxBytes
        return WebAudioSink(format)
    }

    @Deprecated("Transport playback", level = DeprecationLevel.HIDDEN)
    override suspend fun playBlocking(audioFlow: AudioFlow) {
        error("WebAudioPlaybackSession uses transport playback")
    }

    override fun readDeviceMediaPosition(
        mediaBase: Duration,
        transport: TransportController,
    ): Duration = transport.buffer.format.durationOfByteOffset(transport.readCursor)

    override fun onTransportSeekApplied(position: Duration) {
        pendingOffsetSec = position.toDouble(DurationUnit.SECONDS)
        val epoch = currentTransport()?.seekEpoch ?: NO_EPOCH
        finiteStartedForEpoch = NO_EPOCH
        if (useFiniteAudioBuffer && audioBuffer != null) {
            when (state.value) {
                is State.Playing, is State.Paused -> {
                    startFiniteSourceAt(pendingOffsetSec)
                    finiteStartedForEpoch = epoch
                }
                // e.g. seek after Finished: the next write starts it at this epoch.
                else -> Unit
            }
        }
        logger.debug { "onTransportSeekApplied pos=$position finite=$useFiniteAudioBuffer" }
    }

    override fun onTransportPaused() {
        scope.launch { audioContext?.suspend() }
    }

    override fun onTransportResumed() {
        // AudioContext.suspend()/resume() keeps every source at its position, so
        // resuming must not restart the finite source (that jumped back to the
        // last seek / start). Sources that ended are restarted by writes or seeks.
        scope.launch {
            val context = audioContext ?: return@launch
            ensureContextRunning(context)
        }
    }

    override fun onTransportStopped() {
        sourceGeneration++
        stopAllSources()
        audioBuffer = null
        pendingOffsetSec = 0.0
        useFiniteAudioBuffer = false
        finiteSourcePlaying = false
        finiteStartedForEpoch = NO_EPOCH
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

        val durationSec = buffer.duration
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

    private fun stopStreamingSlicesOnly() {
        scheduledSources.forEach { src ->
            src.onended = EventHandler { }
            runCatching { src.stop() }
            runCatching { src.disconnect() }
        }
        scheduledSources = mutableListOf()
        nextSliceStartTime = audioContext?.currentTime ?: 0.0
    }

    private fun stopAllSources() {
        currentSource?.let { src ->
            src.onended = EventHandler { }
            runCatching { src.stop() }
            runCatching { src.disconnect() }
        }
        currentSource = null
        finiteSourcePlaying = false
        stopStreamingSlicesOnly()
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

    private companion object {
        const val NO_EPOCH = -1L
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
                val transport = currentTransport()
                // Stale write from before a seek: drop it (as scheduleSlice does).
                if (transport != null && transport.seekEpoch != seekEpoch) return
                if (transport?.isIngestComplete == true) {
                    // Full growable buffer is ready: one AudioBuffer + offset seeks.
                    // Stop any interim slices from the pre-complete streaming phase.
                    if (scheduledSources.isNotEmpty()) {
                        stopStreamingSlicesOnly()
                    }
                    // Ingest is complete, so the PCM is final: build the buffer once
                    // (rebuilding on every write copied the whole recording each time).
                    if (audioBuffer == null) rebuildFiniteAudioBuffer(context)
                    if (finiteStartedForEpoch != seekEpoch && state.value is State.Playing) {
                        // Start where the transport actually is, not at the last seek.
                        startFiniteSourceAt(format.durationOfByteOffset(mediaByteOffset).toDouble(DurationUnit.SECONDS))
                        finiteStartedForEpoch = seekEpoch
                    }
                } else {
                    // Do not start an AudioBufferSourceNode on partial ingest —
                    // the node keeps the buffer snapshot from start(); later
                    // rebuilds would not extend an already-playing source.
                    val chunk = data.copyOfRange(offset, offset + length)
                    scheduleSlice(context, chunk, seekEpoch)
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
