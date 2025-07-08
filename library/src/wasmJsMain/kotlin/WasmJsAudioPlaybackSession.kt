import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import org.khronos.webgl.*

class WasmJsAudioPlaybackSession(
    private val device: AudioDevice.Output
) : AudioPlaybackSession {

    private val _state = MutableStateFlow<AudioPlaybackState>(AudioPlaybackState.Idle)
    override val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private var audioContext: AudioContext? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun play(audioFlow: AudioFlow) {
        if (_state.value == AudioPlaybackState.Playing) return
        val format = audioFlow.format
        try {
            // Note: JS `deviceId` for output is often not directly settable on AudioContext.
            // The user typically selects the output from their system sound settings.
            // Some browsers allow it via `setSinkId()`, which is a newer API.

            val contextOptions = AudioContextOptions(
                latencyHint = AudioContextLatencyCategoryPlayback,
                sampleRate = format.sampleRate.toJsNumber()
            )
            val context = AudioContext(contextOptions)
            audioContext = context

            var nextStartTime = context.currentTime

            playbackJob = scope.launch {
                runCatching {
                    _state.value = AudioPlaybackState.Playing
                    val lastCompletable = audioFlow.map { rawAudioData ->

                        val wasmJsAudioBufferFinishedIndicator = CompletableDeferred<Unit>()

                        // Ensure context is not closed and we are still playing
                        if (context.state != AudioContextStateRunning || _state.value != AudioPlaybackState.Playing) {
                            wasmJsAudioBufferFinishedIndicator.complete(Unit)
                            coroutineContext.cancel()
                            return@map wasmJsAudioBufferFinishedIndicator
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
                    _state.value = AudioPlaybackState.Finished
                }.onFailure {
                    if (it !is CancellationException) {
                        it.printStackTrace()
                        _state.value = AudioPlaybackState.Error(it)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = AudioPlaybackState.Error(e)
        }
    }

    override fun pause() {
        if (_state.value != AudioPlaybackState.Playing) return
        scope.launch {
            audioContext?.suspend()?.await<Unit>()
            _state.value = AudioPlaybackState.Paused
        }
    }

    override fun resume() {
        if (_state.value != AudioPlaybackState.Paused) return
        scope.launch {
            audioContext?.resume()?.await<Unit>()
            _state.value = AudioPlaybackState.Playing
        }
    }

    override fun stop() {
        if (_state.value == AudioPlaybackState.Idle) return
        playbackJob?.cancel()
        scope.launch {
            audioContext?.close()?.await<Unit>()
            audioContext = null
        }
        _state.value = AudioPlaybackState.Idle
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