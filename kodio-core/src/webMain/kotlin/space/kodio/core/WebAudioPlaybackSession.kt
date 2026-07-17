package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import space.kodio.core.AudioPlaybackSession.State
import space.kodio.core.io.collectToByteArray
import space.kodio.core.util.namedLogger
import web.audio.*
import web.events.EventHandler
import kotlin.time.Duration

private val logger = namedLogger("WebPlayback")

/**
 * Web playback session backed by a single cached [AudioBuffer] for seekable
 * playback of finite PCM (typical [AudioRecording] loads).
 *
 * Seek uses [AudioBufferSourceNode.start] with a buffer offset so scrubbing does
 * not tear down the session or reload clipped PCM windows. Output device
 * selection via [AudioContext.setSinkId] remains deferred (Chrome/Edge-only).
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

        // Finite recordings are collected into one buffer so seek can use
        // AudioBufferSourceNode offset starts. Live/unbounded streams would
        // block here; Player loads AudioRecording (finite) in practice.
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
     * Buffer-backed seek via [AudioBufferSourceNode.start] offset.
     * Remembers [position] even before the first buffer is materialized so
     * pre-play seeks apply on the subsequent start.
     */
    override fun onSeek(position: Duration): SeekResult {
        val buffer = audioBuffer
        val durationSec = buffer?.duration?.toDouble() ?: Double.POSITIVE_INFINITY
        val offsetSec = pcmSeekOffsetSeconds(position, durationSec)
        pendingOffsetSec = offsetSec
        logger.debug { "onSeek(position=$position) -> offsetSec=$offsetSec (buffered=${buffer != null})" }

        if (buffer == null) {
            // Offset is remembered for the next playBlocking start.
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
