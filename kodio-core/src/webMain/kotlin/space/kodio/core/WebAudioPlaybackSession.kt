package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import space.kodio.core.AudioPlaybackSession.State
import space.kodio.core.io.collectToByteArray
import space.kodio.core.util.namedLogger
import web.audio.*
import web.events.EventHandler
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

private val logger = namedLogger("WebPlayback")

/**
 * Web playback session.
 *
 * Known-finite PCM ([AudioFlow.isKnownFinite]) is collected into one
 * [AudioBuffer] so seek can use [AudioBufferSourceNode.start] offsets.
 * Live/unbounded flows keep chunked [AudioBufferSourceNode] scheduling and
 * return [SeekResult.Unsupported] for seek — never drained fully.
 *
 * Output device selection via [AudioContext.setSinkId] remains deferred
 * (Chrome/Edge-only).
 */
class WebAudioPlaybackSession() : BaseAudioPlaybackSession() {

    private var audioContext: AudioContext? = null
    private var audioBuffer: AudioBuffer? = null
    private var currentSource: AudioBufferSourceNode? = null

    /** Absolute offset (seconds) for the next [AudioBufferSourceNode.start]. */
    private var pendingOffsetSec: Double = 0.0

    /** Bumped on seek/stop so stale [AudioBufferSourceNode.onended] handlers are ignored. */
    private var sourceGeneration: Int = 0

    private var playbackFinished: CompletableDeferred<Unit>? = null

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        val contextOptions = createAudioContextOptions(
            latencyHint = AudioContextLatencyCategory.playback,
            sampleRate = format.sampleRate
        )
        val context = AudioContext(contextOptions)
        audioContext = context
        audioBuffer = null
        currentSource = null
        sourceGeneration++
        return toWebPlaybackFormat(format)
    }

    /**
     * Web Audio's [AudioBuffer] always stores samples as Float32 per channel, so we
     * normalize whatever the source produces to interleaved Float32 here. The
     * actual normalization is performed by [BaseAudioPlaybackSession.play] via
     * [space.kodio.core.io.convertAudio], which means [playBlocking] only ever
     * sees Float32 LE bytes.
     */
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

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        val context = audioContext ?: return
        ensureContextRunning(context)

        if (audioFlow.isKnownFinite) {
            playBuffered(context, audioFlow)
        } else {
            audioBuffer = null
            playStreaming(context, audioFlow)
        }
    }

    private suspend fun playBuffered(context: AudioContext, audioFlow: AudioFlow) {
        val data = audioFlow.collectToByteArray()
        val buffer = context.createBufferFrom(
            format = audioFlow.format,
            data = data,
        )
        audioBuffer = buffer

        val finished = CompletableDeferred<Unit>()
        playbackFinished = finished
        startSourceAt(pendingOffsetSec, finished)

        finished.await()
    }

    /**
     * Chunk-scheduled playback for live/unbounded streams. Does not wait for
     * the flow to complete before starting audio.
     */
    private suspend fun playStreaming(context: AudioContext, audioFlow: AudioFlow) {
        @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
        val audioFormat = audioFlow.format
        var nextStartTime = context.currentTime
        val lastCompletable = audioFlow.map { audioData ->
            val jsAudioBufferFinishedIndicator = CompletableDeferred<Unit>()

            // Ensure context is not closed and we are still playing
            if (context.state != AudioContextState.running || state.value != State.Playing) {
                jsAudioBufferFinishedIndicator.complete(Unit)
                coroutineContext.cancel()
                return@map jsAudioBufferFinishedIndicator
            }

            val chunkBuffer = context.createBufferFrom(
                format = audioFormat,
                data = audioData,
            )
            val source = context.createBufferSource()
            source.buffer = chunkBuffer
            source.onended = EventHandler {
                jsAudioBufferFinishedIndicator.complete(Unit)
            }
            source.connect(context.destination)

            val scheduleTime =
                if (nextStartTime < context.currentTime) context.currentTime else nextStartTime
            source.start(scheduleTime)
            nextStartTime = scheduleTime + chunkBuffer.duration

            jsAudioBufferFinishedIndicator
        }.lastOrNull()
        lastCompletable?.await()
    }

    /**
     * Buffer-backed seek via [AudioBufferSourceNode.start] offset.
     * Only for known-finite content (cached [audioBuffer] or pending materialization).
     */
    override fun onSeek(position: Duration): SeekResult {
        val buffer = audioBuffer
        val canSeek = buffer != null || audioFlow.value?.isKnownFinite == true
        if (!canSeek) {
            logger.debug { "onSeek(position=$position) -> Unsupported (not buffered)" }
            return SeekResult.Unsupported
        }

        val durationSec = buffer?.duration?.toDouble() ?: Double.POSITIVE_INFINITY
        val offsetSec = pcmSeekOffsetSeconds(position, durationSec)
        pendingOffsetSec = offsetSec
        logger.debug { "onSeek(position=$position) -> offsetSec=$offsetSec (buffered=${buffer != null})" }

        if (buffer == null) {
            // Offset is remembered for the next playBuffered start.
            return SeekResult.Applied
        }

        val finished = playbackFinished
        when (state.value) {
            is State.Playing -> {
                // Replace the active source; playBlocking keeps waiting on [finished].
                startSourceAt(offsetSec, finished)
            }
            is State.Paused -> {
                // Rebuild the scheduled source under a suspended context so resume
                // continues from the new offset without an audible glitch.
                startSourceAt(offsetSec, finished)
            }
            else -> {
                // Ready / Finished: offset applies on the next play().
            }
        }
        return SeekResult.Applied
    }

    private fun startSourceAt(offsetSec: Double, finished: CompletableDeferred<Unit>?) {
        val context = audioContext ?: return
        val buffer = audioBuffer ?: return

        val generation = ++sourceGeneration
        stopCurrentSource()

        val durationSec = buffer.duration.toDouble()
        val clamped = offsetSec.coerceIn(0.0, durationSec)
        pendingOffsetSec = clamped

        if (clamped >= durationSec) {
            finished?.complete(Unit)
            return
        }

        scope.launch { ensureContextRunning(context) }

        val source = context.createBufferSource()
        source.buffer = buffer
        source.connect(context.destination)
        source.onended = EventHandler {
            if (generation == sourceGeneration) {
                currentSource = null
                finished?.complete(Unit)
            }
        }
        source.start(context.currentTime, clamped)
        currentSource = source
        logger.debug { "Started AudioBufferSourceNode at offset=$clamped gen=$generation" }
    }

    private fun stopCurrentSource() {
        val source = currentSource ?: return
        currentSource = null
        source.onended = EventHandler { }
        runCatching { source.stop() }
        runCatching { source.disconnect() }
    }

    private suspend fun ensureContextRunning(context: AudioContext) {
        if (context.state != AudioContextState.running) {
            context.resume()
        }
    }

    override fun onPause() {
        scope.launch { audioContext?.suspend() }
    }

    override fun onResume() {
        scope.launch {
            val context = audioContext ?: return@launch
            ensureContextRunning(context)
        }
    }

    override fun onStop() {
        sourceGeneration++
        stopCurrentSource()
        audioBuffer = null
        pendingOffsetSec = 0.0
        playbackFinished?.complete(Unit)
        playbackFinished = null
        val context = audioContext ?: return
        audioContext = null
        scope.launch { context.close() }
    }
}
