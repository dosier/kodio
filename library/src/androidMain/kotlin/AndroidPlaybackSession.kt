import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class AndroidPlaybackSession(
    private val context: Context,
    private val device: AudioDevice.Output
) : PlaybackSession {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @OptIn(ExperimentalTime::class)
    override suspend fun play(audioDataFlow: AudioDataFlow) {
        if (_state.value == PlaybackState.Playing) return

        try {
            val format = audioDataFlow.format
            val minBufferSize = AudioTrack.getMinBufferSize(
                format.sampleRate,
                format.channels.toAndroidChannelOutMask(),
                format.bitDepth.toAndroidFormatEncoding()
            )
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE)
                error("$format is not supported by the device")
            if (minBufferSize == AudioRecord.ERROR)
                error("Failed to get min buffer size")
            val playbackBufferSize = minBufferSize * 8
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(format.sampleRate)
                        .setChannelMask(format.channels.toAndroidChannelOutMask())
                        .setEncoding(format.bitDepth.toAndroidFormatEncoding())
                        .build()
                )
//                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(playbackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            println(audioTrack?.format)
            // Set the preferred device
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val selectedDevice = devices.firstOrNull { it.id.toString() == device.id }
            selectedDevice?.let { audioTrack?.preferredDevice = it }

            audioTrack?.playbackRate = format.sampleRate
            audioTrack?.play()
            _state.value = PlaybackState.Playing

            playbackJob = scope.launch {
                runCatching {
                    val audioData = audioDataFlow.toList()

                    for (chunk in audioData) {
                        audioTrack?.write(chunk, 0, chunk.size)
                    }

                    _state.value = PlaybackState.Finished
                }.onFailure {
                    if (it !is kotlinx.coroutines.CancellationException) {
                        _state.value = PlaybackState.Error(it)
                    }
                }
            }

        } catch (e: Exception) {
            _state.value = PlaybackState.Error(e)
        }
    }

    override fun pause() {
        if (_state.value != PlaybackState.Playing) return
        audioTrack?.pause()
        _state.value = PlaybackState.Paused
    }

    override fun resume() {
        if (_state.value != PlaybackState.Paused) return
        audioTrack?.play()
        _state.value = PlaybackState.Playing
    }

    override fun stop() {
        if (_state.value == PlaybackState.Idle || _state.value == PlaybackState.Finished) return
        playbackJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        _state.value = PlaybackState.Idle
    }
}