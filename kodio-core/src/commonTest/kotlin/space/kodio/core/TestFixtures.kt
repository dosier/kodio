package space.kodio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

internal fun fmtInt(
    rate: Int,
    channels: Channels,
    depth: IntBitDepth,
    signed: Boolean = true,
    endianness: Endianness = Endianness.Little,
    layout: SampleLayout = SampleLayout.Interleaved,
): AudioFormat = AudioFormat(
    sampleRate = rate,
    channels = channels,
    encoding = SampleEncoding.PcmInt(
        bitDepth = depth,
        endianness = endianness,
        layout = layout,
        signed = signed,
        packed = true,
    ),
)

internal fun fmtFloat(
    rate: Int,
    channels: Channels,
    precision: FloatPrecision,
    layout: SampleLayout = SampleLayout.Interleaved,
): AudioFormat = AudioFormat(
    sampleRate = rate,
    channels = channels,
    encoding = SampleEncoding.PcmFloat(
        precision = precision,
        layout = layout,
    ),
)

internal fun rampBytes(size: Int): ByteArray =
    ByteArray(size) { i -> (i % 251).toByte() }

internal class FakeRecordingSession(
    private val testData: ByteArray = byteArrayOf(1, 2, 3, 4),
) : AudioRecordingSession {
    private val _state = MutableStateFlow<AudioRecordingSession.State>(AudioRecordingSession.State.Idle)
    override val state: StateFlow<AudioRecordingSession.State> = _state

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow

    var resetCalled = false
    var pauseCalled = false
    var resumeCalled = false
    private val format = AudioQuality.Standard.format

    override suspend fun start() {
        _state.value = AudioRecordingSession.State.Recording
        _audioFlow.value = AudioFlow(format, flowOf(testData))
    }

    override suspend fun pause() {
        pauseCalled = true
        _state.value = AudioRecordingSession.State.Paused
    }

    override suspend fun resume() {
        resumeCalled = true
        _state.value = AudioRecordingSession.State.Recording
    }

    override fun stop() {
        _state.value = AudioRecordingSession.State.Stopped
    }

    override fun reset() {
        resetCalled = true
        _state.value = AudioRecordingSession.State.Idle
        _audioFlow.value = null
    }
}

internal class FakePlaybackSession : AudioPlaybackSession {
    private val _state = MutableStateFlow<AudioPlaybackSession.State>(AudioPlaybackSession.State.Idle)
    override val state: StateFlow<AudioPlaybackSession.State> = _state

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow

    var stopCalled = false

    override suspend fun load(audioFlow: AudioFlow) {
        _audioFlow.value = audioFlow
        _state.value = AudioPlaybackSession.State.Ready
    }

    override suspend fun play() {
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun pause() {
        _state.value = AudioPlaybackSession.State.Paused
    }

    override fun resume() {
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun stop() {
        stopCalled = true
        _state.value = AudioPlaybackSession.State.Idle
    }

    fun simulateFinished() {
        _state.value = AudioPlaybackSession.State.Finished
    }
}
