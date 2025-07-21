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
 * TODO: support output device selection when setSinkId becomes widely adapted (https://developer.mozilla.org/en-US/docs/Web/API/AudioContext/setSinkId)
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
        return format
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
