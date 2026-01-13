package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [AudioSessionState] and state conversion extensions.
 */
class AudioSessionStateTest {

    // ==================== Extension Property Tests ====================

    @Test
    fun `isActive returns true only for Active state`() {
        assertTrue(AudioSessionState.Active.isActive)
        assertFalse(AudioSessionState.Idle.isActive)
        assertFalse(AudioSessionState.Paused.isActive)
        assertFalse(AudioSessionState.Complete.isActive)
        assertFalse(AudioSessionState.Failed(AudioError.NoRecordingData()).isActive)
    }

    @Test
    fun `isTerminal returns true for Complete and Failed`() {
        assertTrue(AudioSessionState.Complete.isTerminal)
        assertTrue(AudioSessionState.Failed(AudioError.NoRecordingData()).isTerminal)
        assertFalse(AudioSessionState.Idle.isTerminal)
        assertFalse(AudioSessionState.Active.isTerminal)
        assertFalse(AudioSessionState.Paused.isTerminal)
    }

    @Test
    fun `canStart returns true for Idle and Complete`() {
        assertTrue(AudioSessionState.Idle.canStart)
        assertTrue(AudioSessionState.Complete.canStart)
        assertFalse(AudioSessionState.Active.canStart)
        assertFalse(AudioSessionState.Paused.canStart)
        assertFalse(AudioSessionState.Failed(AudioError.NoRecordingData()).canStart)
    }

    // ==================== Recording State Conversion Tests ====================

    @Test
    fun `Recording Idle converts to SessionState Idle`() {
        val state = AudioRecordingSession.State.Idle
        assertEquals(AudioSessionState.Idle, state.toSessionState())
    }

    @Test
    fun `Recording Recording converts to SessionState Active`() {
        val state = AudioRecordingSession.State.Recording
        assertEquals(AudioSessionState.Active, state.toSessionState())
    }

    @Test
    fun `Recording Stopped converts to SessionState Complete`() {
        val state = AudioRecordingSession.State.Stopped
        assertEquals(AudioSessionState.Complete, state.toSessionState())
    }

    @Test
    fun `Recording Error converts to SessionState Failed with wrapped error`() {
        val cause = RuntimeException("Test error")
        val state = AudioRecordingSession.State.Error(cause)
        val result = state.toSessionState()
        
        assertIs<AudioSessionState.Failed>(result)
        assertIs<AudioError.Unknown>(result.error)
    }

    // ==================== Playback State Conversion Tests ====================

    @Test
    fun `Playback Idle converts to SessionState Idle`() {
        val state = AudioPlaybackSession.State.Idle
        assertEquals(AudioSessionState.Idle, state.toSessionState())
    }

    @Test
    fun `Playback Ready converts to SessionState Idle`() {
        val state = AudioPlaybackSession.State.Ready
        assertEquals(AudioSessionState.Idle, state.toSessionState())
    }

    @Test
    fun `Playback Playing converts to SessionState Active`() {
        val state = AudioPlaybackSession.State.Playing
        assertEquals(AudioSessionState.Active, state.toSessionState())
    }

    @Test
    fun `Playback Paused converts to SessionState Paused`() {
        val state = AudioPlaybackSession.State.Paused
        assertEquals(AudioSessionState.Paused, state.toSessionState())
    }

    @Test
    fun `Playback Finished converts to SessionState Complete`() {
        val state = AudioPlaybackSession.State.Finished
        assertEquals(AudioSessionState.Complete, state.toSessionState())
    }

    @Test
    fun `Playback Error converts to SessionState Failed with wrapped error`() {
        val cause = IllegalStateException("Playback failed")
        val state = AudioPlaybackSession.State.Error(cause)
        val result = state.toSessionState()
        
        assertIs<AudioSessionState.Failed>(result)
        assertIs<AudioError.Unknown>(result.error)
    }

    // ==================== Failed State Tests ====================

    @Test
    fun `Failed state preserves error`() {
        val error = AudioError.DeviceNotFound("test-device")
        val state = AudioSessionState.Failed(error)
        assertEquals(error, state.error)
    }

    @Test
    fun `Different Failed states with same error type are equal`() {
        val error1 = AudioError.PermissionDenied()
        val error2 = AudioError.PermissionDenied()
        val state1 = AudioSessionState.Failed(error1)
        val state2 = AudioSessionState.Failed(error2)
        // Failed states with equal errors should be equal
        assertEquals(state1, state2)
    }
}


