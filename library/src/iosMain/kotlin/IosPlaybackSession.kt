import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pointed
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosPlaybackSession(private val device: AudioDevice.Output) : PlaybackSession {

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val engine = AVAudioEngine()
    private val playerNode = AVAudioPlayerNode()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        engine.attachNode(playerNode)
        engine.connect(playerNode, engine.mainMixerNode, null)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun play(audioData: Flow<ByteArray>, format: AudioFormat) {
        if (_state.value == PlaybackState.PLAYING) return

        try {
            val avFormat = format.toAVAudioFormat() ?: error("Unsupported format")
            engine.prepare()
            engine.startAndReturnError(null)
            playerNode.play()
            _state.value = PlaybackState.PLAYING

            playbackJob = scope.launch {
                audioData.collect { bytes ->
                    val streamDescription = avFormat.streamDescription?.pointed ?: error("No stream description")
                    val buffer = AVAudioPCMBuffer(
                        avFormat,
                        bytes.size.toUInt() / streamDescription.mBytesPerFrame
                    )
                    val audioBufferList = buffer.audioBufferList?.pointed?: error("No audio buffer list")
                    buffer.frameLength = buffer.frameCapacity
                    bytes.usePinned { pinned ->
                        memcpy(audioBufferList.mBuffers.pointed.mData, pinned.addressOf(0), bytes.size.toULong())
                    }

                    // Schedule buffer in a coroutine to handle completion
                    suspendCoroutine<Unit> { continuation ->
                        playerNode.scheduleBuffer(buffer) {
                            continuation.resume(Unit)
                        }
                    }
                }
                _state.value = PlaybackState.FINISHED
            }
        } catch (e: Exception) {
            _state.value = PlaybackState.ERROR
            e.printStackTrace()
        }
    }

    override fun pause() {
        if (playerNode.isPlaying()) {
            playerNode.pause()
            _state.value = PlaybackState.PAUSED
        }
    }

    override fun resume() {
        if (!playerNode.isPlaying() && _state.value == PlaybackState.PAUSED) {
            playerNode.play()
            _state.value = PlaybackState.PLAYING
        }
    }

    override fun stop() {
        playbackJob?.cancel()
        if (playerNode.isPlaying()) {
            playerNode.stop()
        }
        engine.stop()
        _state.value = PlaybackState.IDLE
    }
}