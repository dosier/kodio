package space.kodio.core

import space.kodio.core.AudioPlaybackSession.State
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.khronos.webgl.*
import kotlin.coroutines.coroutineContext

class WasmJsAudioPlaybackSession(
    private val device: AudioDevice.Output
) : BaseAudioPlaybackSession() {

    private var audioContext: AudioContext? = null

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        // Note: JS `deviceId` for output is often not directly settable on gg.kodio.core.AudioContext.
        // The user typically selects the output from their system sound settings.
        // Some browsers allow it via `setSinkId()`, which is a newer API.
        val contextOptions = AudioContextOptions(
            latencyHint = AudioContextLatencyCategoryPlayback,
            sampleRate = format.sampleRate.toJsNumber()
        )
        val context = AudioContext(contextOptions)
        audioContext = context
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        val context = audioContext ?: return
        var nextStartTime = context.currentTime
        val format = audioFlow.format
        val lastCompletable = audioFlow.map { rawAudioData ->

            val wasmJsAudioBufferFinishedIndicator = CompletableDeferred<Unit>()

            // Ensure context is not closed and we are still playing
            if (context.state != AudioContextStateRunning || state.value != State.Playing) {
                wasmJsAudioBufferFinishedIndicator.complete(Unit)
                coroutineContext.cancel()
                return@map wasmJsAudioBufferFinishedIndicator
            }

            // 1. Convert ByteArray to Float32Array
            val pcm16Data = Int16Array(rawAudioData.toJsArrayBuffer())
            val pcm32Data = pcm16Data.to32FloatArray()

            // 2. Create gg.kodio.core.AudioBuffer
            val buffer = context.createBuffer(
                numberOfChannels = format.channels.count,
                length = pcm32Data.length,
                sampleRate = format.sampleRate.toFloat()
            )
            buffer.copyToChannel(pcm32Data, 0) // Assuming mono audio for simplicity

            // 3. Create a source and play it
            val source = context.createBufferSource()
            source.buffer = buffer
            source.onended = {
                wasmJsAudioBufferFinishedIndicator.complete(Unit)
            }

            source.connect(context.destination)

            // Schedule playback. Wait if the context time hasn't caught up yet.
            val scheduleTime = if (nextStartTime < context.currentTime) context.currentTime else nextStartTime
            source.start(scheduleTime)

            // Update the start time for the next buffer
            nextStartTime = scheduleTime + buffer.duration

            wasmJsAudioBufferFinishedIndicator
        }.lastOrNull()
        lastCompletable?.await()
    }

    override fun onPause() {
        scope.launch { audioContext?.suspend()?.await() }
    }

    override fun onResume() {
        scope.launch { audioContext?.resume()?.await() }
    }

    override fun onStop() {
        val context = audioContext?:return
        audioContext = null
        scope.launch { context.close().await() }
    }
}

private fun Int16Array.to32FloatArray(): Float32Array {
    val floatArray = Float32Array(this.length)
    for (i in 0 until this.length) {
        floatArray[i] = get(i) / 32767.0f
    }
    return floatArray
}
// Helper extension is needed to convert a ByteArray to an ArrayBuffer
private fun ByteArray.toJsArrayBuffer(): ArrayBuffer {
    return toUint8Array().buffer
}
private fun ByteArray.toUint8Array(): Uint8Array {
    val result = Uint8Array(this.size)
    for (index in this.indices) {
        result[index] = this[index]
    }
    return result
}