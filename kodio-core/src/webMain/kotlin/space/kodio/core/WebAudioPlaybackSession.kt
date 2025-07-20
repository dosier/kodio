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

class WebAudioPlaybackSession(
    private val device: AudioDevice.Output
) : BaseAudioPlaybackSession() {

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
        println("1. playBlocking")
        val format = audioFlow.format
        println("2. playBlocking $format")
        val context = audioContext ?: return
        println("3. playBlocking $context")
        var nextStartTime = context.currentTime
        println("4. playBlocking $nextStartTime")
        val lastCompletable = audioFlow.transformToPcm32().map { pcm32Data ->
            val jsAudioBufferFinishedIndicator = CompletableDeferred<Unit>()

            // Ensure context is not closed and we are still playing
            if (context.state != AudioContextState.running || state.value != State.Playing) {
                jsAudioBufferFinishedIndicator.complete(Unit)
                coroutineContext.cancel()
                return@map jsAudioBufferFinishedIndicator
            }

            // 2. Create AudioBuffer
            val buffer = context.createBuffer(
                numberOfChannels = format.channels.count,
                length = pcm32Data.length,
                sampleRate = format.sampleRate.toFloat()
            )

            buffer.copyToChannel(pcm32Data, 0) // Assuming mono audio for simplicity

            // 3. Create a source and play it
            val source = context.createBufferSource()
            source.buffer = buffer
            source.onended = EventHandler {
                println("onended")
                jsAudioBufferFinishedIndicator.complete(Unit)
            }
            source.connect(context.destination)

            // Schedule playback. Wait if the context time hasn't caught up yet.
            val scheduleTime = if (nextStartTime < context.currentTime) context.currentTime else nextStartTime
            println("playing buffer(${scheduleTime}) at ${context.destination}")
            source.start(scheduleTime)

            // Update the start time for the next buffer
            nextStartTime = scheduleTime + buffer.duration

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
