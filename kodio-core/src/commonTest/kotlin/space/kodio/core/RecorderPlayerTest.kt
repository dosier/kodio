package space.kodio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [Recorder] and [Player] classes.
 */
class RecorderPlayerTest {

    // ==================== Recorder Tests ====================

    @Test
    fun `Recorder start changes isRecording to true`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        assertFalse(recorder.isRecording)
        recorder.start()
        assertTrue(recorder.isRecording)
    }

    @Test
    fun `Recorder stop changes isRecording to false`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        assertTrue(recorder.isRecording)
        
        recorder.stop()
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `Recorder toggle starts when idle`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        val result = recorder.toggle()
        assertTrue(result)
        assertTrue(recorder.isRecording)
    }

    @Test
    fun `Recorder toggle stops when recording`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        val result = recorder.toggle()
        assertFalse(result)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `Recorder quality property reflects construction parameter`() {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Lossless)

        assertEquals(AudioQuality.Lossless, recorder.quality)
    }

    @Test
    fun `Recorder getRecording returns recording after stop`() = runTest {
        val testData = byteArrayOf(1, 2, 3, 4)
        val session = FakeRecordingSession(testData = testData)
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        recorder.stop()
        
        val recording = recorder.getRecording()
        assertNotNull(recording)
        assertTrue(recording.sizeInBytes > 0)
    }

    @Test
    fun `Recorder getRecording returns null while recording`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        val recording = recorder.getRecording()
        assertNull(recording)
    }

    @Test
    fun `Recorder getRecording caches result`() = runTest {
        val testData = byteArrayOf(1, 2, 3, 4)
        val session = FakeRecordingSession(testData = testData)
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        recorder.stop()
        
        val recording1 = recorder.getRecording()
        val recording2 = recorder.getRecording()
        
        // Should be the same instance
        assertTrue(recording1 === recording2)
    }

    @Test
    fun `Recorder reset clears state`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        recorder.stop()
        recorder.reset()

        assertFalse(recorder.isRecording)
        assertEquals(AudioSessionState.Idle, recorder.sessionState)
    }

    @Test
    fun `Recorder use extension calls release`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.use {
            it.start()
            it.stop()
        }

        // After use block, session should be reset
        assertTrue(session.resetCalled)
    }

    @Test
    fun `Recorder hasRecording is true after stop with data`() = runTest {
        val testData = byteArrayOf(1, 2, 3, 4)
        val session = FakeRecordingSession(testData = testData)
        val recorder = Recorder(session, AudioQuality.Standard)

        assertFalse(recorder.hasRecording)
        
        recorder.start()
        assertFalse(recorder.hasRecording)
        
        recorder.stop()
        assertTrue(recorder.hasRecording)
    }

    @Test
    fun `Recorder sessionState reflects session state`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        assertEquals(AudioSessionState.Idle, recorder.sessionState)
        
        recorder.start()
        assertEquals(AudioSessionState.Active, recorder.sessionState)
        
        recorder.stop()
        assertEquals(AudioSessionState.Complete, recorder.sessionState)
    }

    // ==================== Player Tests ====================

    @Test
    fun `Player isPlaying reflects state`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)

        assertFalse(player.isPlaying)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )
        player.load(recording)
        player.start()
        
        assertTrue(player.isPlaying)
    }

    @Test
    fun `Player load sets audio flow and marks ready`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )

        assertFalse(player.isReady)
        player.load(recording)
        assertTrue(player.isReady)
    }

    @Test
    fun `Player pause changes state to paused`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )

        player.load(recording)
        player.start()
        player.pause()
        
        assertTrue(player.isPaused)
        assertFalse(player.isPlaying)
    }

    @Test
    fun `Player resume after pause`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )

        player.load(recording)
        player.start()
        player.pause()
        player.resume()
        
        assertTrue(player.isPlaying)
        assertFalse(player.isPaused)
    }

    @Test
    fun `Player stop resets state`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )

        player.load(recording)
        player.start()
        player.stop()
        
        assertFalse(player.isPlaying)
        assertFalse(player.isPaused)
    }

    @Test
    fun `Player toggle starts when ready`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )

        player.load(recording)
        
        val result = player.toggle()
        assertEquals(true, result)
        assertTrue(player.isPlaying)
    }

    @Test
    fun `Player toggle pauses when playing`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )

        player.load(recording)
        player.start()
        
        val result = player.toggle()
        assertEquals(false, result)
        assertTrue(player.isPaused)
    }

    @Test
    fun `Player toggle returns null when no audio loaded`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)

        val result = player.toggle()
        assertNull(result)
    }

    @Test
    fun `Player use extension calls stop on cleanup`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)

        player.use {
            it.load(AudioRecording.fromBytes(AudioQuality.Standard.format, byteArrayOf(1, 2)))
            it.start()
        }

        // Player.release() calls session.stop(), so state should be Idle
        assertTrue(session.stopCalled)
    }

    @Test
    fun `Player isReady includes Finished state`() = runTest {
        val session = FakePlaybackSession()
        val player = Player(session)
        
        val recording = AudioRecording.fromBytes(
            AudioQuality.Standard.format,
            byteArrayOf(1, 2, 3, 4)
        )

        player.load(recording)
        session.simulateFinished()
        
        assertTrue(player.isReady)
    }

    // ==================== Fake Implementations ====================

    private class FakeRecordingSession(
        private val testData: ByteArray = byteArrayOf(1, 2, 3, 4)
    ) : AudioRecordingSession {
        private val _state = MutableStateFlow<AudioRecordingSession.State>(AudioRecordingSession.State.Idle)
        override val state: StateFlow<AudioRecordingSession.State> = _state
        
        private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
        override val audioFlow: StateFlow<AudioFlow?> = _audioFlow

        var resetCalled = false
        private val format = AudioQuality.Standard.format

        override suspend fun start() {
            _state.value = AudioRecordingSession.State.Recording
            _audioFlow.value = AudioFlow(format, flowOf(testData))
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

    private class FakePlaybackSession : AudioPlaybackSession {
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
}
