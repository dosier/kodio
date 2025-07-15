package gg.kodio.core

import gg.kodio.core.AudioPlaybackSession.State
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import js.typedarrays.Int16Array
import js.typedarrays.toUint8Array
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import web.audio.AudioContext
import web.audio.AudioContextOptions
import web.audio.AudioContextState
import web.events.EventHandler
import kotlin.coroutines.coroutineContext

class JsAudioPlaybackSession(
    private val device: AudioDevice.Output
) : BaseAudioPlaybackSession() {

    private var audioContext: AudioContext? = null

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        // Note: JS `deviceId` for output is often not directly settable on AudioContext.
        // The user typically selects the output from their system sound settings.
        // Some browsers allow it via `setSinkId()`, which is a newer API.
        val contextOptions = AudioContextOptions(sampleRate = format.sampleRate.toFloat())
        val context = AudioContext(contextOptions)
        audioContext = context
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        val format = audioFlow.format
        val context = audioContext ?: return
        var nextStartTime = context.currentTime

        val lastCompletable = audioFlow.map { rawAudioData ->

            val jsAudioBufferFinishedIndicator = CompletableDeferred<Unit>()

            // Ensure context is not closed and we are still playing
            if (context.state != AudioContextState.running || state.value != State.Playing) {
                jsAudioBufferFinishedIndicator.complete(Unit)
                coroutineContext.cancel()
                return@map jsAudioBufferFinishedIndicator
            }

            // 1. Convert ByteArray to Float32Array
            val pcm16Data = Int16Array(rawAudioData.toJsArrayBuffer())
            val pcm32Data = pcm16Data.to32FloatArray()

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
                jsAudioBufferFinishedIndicator.complete(Unit)
            }
            source.connect(context.destination)

            // Schedule playback. Wait if the context time hasn't caught up yet.
            val scheduleTime = if (nextStartTime < context.currentTime) context.currentTime else nextStartTime
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

private fun <B : ArrayBufferLike> Int16Array<B>.to32FloatArray(): Float32Array<B> {
    val floatArray = Float32Array<B>(this.length)
    for (i in 0 until this.length) {
        floatArray[i] = this[i] / 32767.0f
    }
    return floatArray
}

// Helper extension is needed to convert a ByteArray to an ArrayBuffer
private fun ByteArray.toJsArrayBuffer(): ArrayBuffer {
    return toUint8Array().buffer
}