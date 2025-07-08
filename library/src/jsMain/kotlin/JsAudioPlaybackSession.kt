import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import js.typedarrays.Int16Array
import js.typedarrays.toUint8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import web.audio.AudioContext
import web.audio.AudioContextOptions
import web.audio.AudioContextState
import web.events.EventHandler

class JsAudioPlaybackSession(
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
            val contextOptions = AudioContextOptions(sampleRate = format.sampleRate.toFloat())
            val context = AudioContext(contextOptions)
            audioContext = context

            var nextStartTime = context.currentTime

            playbackJob = scope.launch {
                runCatching {
                    _state.value = AudioPlaybackState.Playing
                    val lastCompletable = audioFlow.map { rawAudioData ->

                        val jsAudioBufferFinishedIndicator = CompletableDeferred<Unit>()

                        // Ensure context is not closed and we are still playing
                        if (context.state != AudioContextState.running || _state.value != AudioPlaybackState.Playing) {
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
            audioContext?.suspend()
            _state.value = AudioPlaybackState.Paused
        }
    }

    override fun resume() {
        if (_state.value != AudioPlaybackState.Paused) return
        scope.launch {
            audioContext?.resume()
            _state.value = AudioPlaybackState.Playing
        }
    }

    override fun stop() {
        if (_state.value == AudioPlaybackState.Idle) return
        playbackJob?.cancel()
        scope.launch {
            audioContext?.close()
            audioContext = null
        }
        _state.value = AudioPlaybackState.Idle
    }
}
private fun<B : ArrayBufferLike> Int16Array<B>.to32FloatArray(): Float32Array<B> {
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