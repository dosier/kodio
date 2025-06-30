import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioPlayerNode

/**
 * IOS implementation for [PlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosPlaybackSession() : PlaybackSession {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

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
    override suspend fun play(audioDataFlow: AudioDataFlow) {
        if (_state.value == PlaybackState.Playing) return

        try {
            val iosAudioFormat = audioDataFlow.format.toIosAudioFormat()

            engine.connect(playerNode, formatConverterMixer, iosAudioFormat)
            engine.connect(formatConverterMixer, engine.mainMixerNode, null)

            engine.prepare()
            engine.startAndReturnError(null)
            playerNode.play()
            _state.value = PlaybackState.Playing

            playbackJob = scope.launch {
                runCatching {
                    val lastCompletable = audioDataFlow.map { bytes ->
                        val iosAudioBuffer = bytes.toIosAudioBuffer(iosAudioFormat)
                        val iosAudioBufferFinishedIndicator = CompletableDeferred<Unit>()
                        playerNode.scheduleBuffer(iosAudioBuffer) {
                            // somehow indicate that the buffer has finished playing
                            iosAudioBufferFinishedIndicator.complete(Unit)
                        }
                        iosAudioBufferFinishedIndicator
                    }.lastOrNull()
                    // The timeout is a safety measure in case something goes wrong with the audio engine
                    lastCompletable?.await()
                    _state.value = PlaybackState.Finished
                }.onFailure {
                    _state.value = PlaybackState.Error(it)
                }
            }
        } catch (e: Exception) {
            _state.value = PlaybackState.Error(e)
            e.printStackTrace()
        }
    }

    override fun pause() {
        if (playerNode.isPlaying()) {
            playerNode.pause()
            _state.value = PlaybackState.Paused
        }
    }

    override fun resume() {
        if (!playerNode.isPlaying() && _state.value == PlaybackState.Paused) {
            playerNode.play()
            _state.value = PlaybackState.Playing
        }
    }

    override fun stop() {
        playbackJob?.cancel()
        if (playerNode.isPlaying())
            playerNode.stop()
        engine.stop()
        engine.disconnectNodeOutput(playerNode)
        engine.disconnectNodeOutput(formatConverterMixer)
        _state.value = PlaybackState.Idle
    }
}