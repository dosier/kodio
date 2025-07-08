import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioPlayerNode

/**
 * IOS implementation for [AudioPlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosAudioPlaybackSession() : AudioPlaybackSession {

    private val _state = MutableStateFlow<AudioPlaybackState>(AudioPlaybackState.Idle)
    override val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private val engine = AVAudioEngine()
    private val playerNode = AVAudioPlayerNode()
    private val formatConverterMixer = AVAudioMixerNode()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        engine.attachNode(playerNode)
        engine.attachNode(formatConverterMixer)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun play(audioFlow: AudioFlow) {
        if (_state.value == AudioPlaybackState.Playing) return
        val format = audioFlow.format
        try {
            val iosAudioFormat = format.toIosAudioFormat()

            engine.connect(playerNode, formatConverterMixer, iosAudioFormat)
            engine.connect(formatConverterMixer, engine.mainMixerNode, null)

            engine.prepare()
            engine.startAndReturnError(null)
            playerNode.play()
            _state.value = AudioPlaybackState.Playing

            playbackJob = scope.launch {
                runCatching {
                    val lastCompletable = audioFlow.map { bytes ->
                        val iosAudioBuffer = bytes.toIosAudioBuffer(iosAudioFormat)
                        val iosAudioBufferFinishedIndicator = CompletableDeferred<Unit>()
                        playerNode.scheduleBuffer(iosAudioBuffer) {
                            // somehow indicate that the buffer has finished playing
                            iosAudioBufferFinishedIndicator.complete(Unit)
                        }
                        iosAudioBufferFinishedIndicator
                    }.lastOrNull()
                    println("boo")
                    lastCompletable?.await()
                    _state.value = AudioPlaybackState.Finished
                }.onFailure {
                    _state.value = AudioPlaybackState.Error(it)
                }
            }
        } catch (e: Exception) {
            _state.value = AudioPlaybackState.Error(e)
            e.printStackTrace()
        }
    }

    override fun pause() {
        if (playerNode.isPlaying()) {
            playerNode.pause()
            _state.value = AudioPlaybackState.Paused
        }
    }

    override fun resume() {
        if (!playerNode.isPlaying() && _state.value == AudioPlaybackState.Paused) {
            playerNode.play()
            _state.value = AudioPlaybackState.Playing
        }
    }

    override fun stop() {
        playbackJob?.cancel()
        if (playerNode.isPlaying())
            playerNode.stop()
        engine.stop()
        engine.disconnectNodeOutput(playerNode)
        engine.disconnectNodeOutput(formatConverterMixer)
        _state.value = AudioPlaybackState.Idle
    }
}