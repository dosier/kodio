package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import space.kodio.core.AudioPlaybackSession.State
import web.audio.*
import web.events.EventHandler
import kotlin.coroutines.coroutineContext

/**
 * Web playback session. Output device selection via [AudioContext.setSinkId] is
 * deferred: the API is Chrome/Edge-only as of 2026 (unsupported in Firefox and
 * Safari). Adding it would require feature-detected progressive enhancement and
 * a device-passing API on the web playback path, which does not exist yet.
 * [AudioError.DeviceSelectionUnsupported] remains the fallback behavior.
 */
class WebAudioPlaybackSession() : BaseAudioPlaybackSession() {

    private var audioContext: AudioContext? = null

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        val contextOptions = createAudioContextOptions(
            latencyHint = AudioContextLatencyCategory.playback,
            sampleRate = format.sampleRate
        )
        val context = AudioContext(contextOptions)
        audioContext = context
        return toWebPlaybackFormat(format)
    }

    /**
     * Web Audio's [AudioBuffer] always stores samples as Float32 per channel, so we
     * normalize whatever the source produces to interleaved Float32 here. The
     * actual normalization is performed by [BaseAudioPlaybackSession.play] via
     * [convertAudio], which means [playBlocking] only ever sees Float32 LE bytes.
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
        @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
        val audioFormat = audioFlow.format
        val context = audioContext ?: return
        var nextStartTime = context.currentTime
        val lastCompletable = audioFlow.map { audioData ->
            val jsAudioBufferFinishedIndicator = CompletableDeferred<Unit>()

            // Ensure context is not closed and we are still playing
            if (context.state != AudioContextState.running || state.value != State.Playing) {
                jsAudioBufferFinishedIndicator.complete(Unit)
                coroutineContext.cancel()
                return@map jsAudioBufferFinishedIndicator
            }

            // 2. Create AudioBuffer
            val audioBuffer = context.createBufferFrom(
                format = audioFormat,
                data = audioData,
            )
            // 3. Create a source and play it
            val source = context.createBufferSource()
            source.buffer = audioBuffer
            source.onended = EventHandler {
                jsAudioBufferFinishedIndicator.complete(Unit)
            }
            source.connect(context.destination)

            // Schedule playback. Wait if the context time hasn't caught up yet.
            val scheduleTime = if (nextStartTime < context.currentTime) context.currentTime else nextStartTime
            source.start(scheduleTime)

            // Update the start time for the next buffer
            nextStartTime = scheduleTime + audioBuffer.duration

            jsAudioBufferFinishedIndicator
        }.lastOrNull()
        lastCompletable?.await()
    }

    override fun onPause() {
        scope.launch { audioContext?.suspend() }
    }

    override fun onResume() {
        scope.launch { audioContext?.resume() }
    }

    override fun onStop() {
        val context = audioContext?:return
        audioContext = null
        scope.launch { context.close() }
    }
}
