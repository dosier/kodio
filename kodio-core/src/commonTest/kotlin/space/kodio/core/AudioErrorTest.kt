package space.kodio.core

import space.kodio.core.security.AudioPermissionDeniedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [AudioError] hierarchy.
 */
class AudioErrorTest {

    @Test
    fun `PermissionDenied has correct message`() {
        val error = AudioError.PermissionDenied()
        assertEquals("Audio permission denied", error.message)
        assertNull(error.cause)
    }

    @Test
    fun `DeviceNotFound without deviceId has generic message`() {
        val error = AudioError.DeviceNotFound()
        assertEquals("Audio device not found", error.message)
        assertNull(error.deviceId)
    }

    @Test
    fun `DeviceNotFound with deviceId includes it in message`() {
        val error = AudioError.DeviceNotFound("mic-123")
        assertEquals("Audio device not found: mic-123", error.message)
        assertEquals("mic-123", error.deviceId)
    }

    @Test
    fun `FormatNotSupported without format has generic message`() {
        val error = AudioError.FormatNotSupported()
        assertEquals("Audio format not supported", error.message)
        assertNull(error.format)
    }

    @Test
    fun `FormatNotSupported with format includes it in message`() {
        val format = AudioQuality.Voice.format
        val error = AudioError.FormatNotSupported(format)
        assertTrue(error.message!!.contains("Audio format not supported"))
        assertEquals(format, error.format)
    }

    @Test
    fun `DeviceError preserves message and cause`() {
        val cause = RuntimeException("Hardware failure")
        val error = AudioError.DeviceError("Microphone disconnected", cause)
        assertEquals("Microphone disconnected", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `NotInitialized has descriptive message`() {
        val error = AudioError.NotInitialized()
        assertTrue(error.message!!.contains("Kodio not initialized"))
        assertTrue(error.message!!.contains("Kodio.initialize()"))
    }

    @Test
    fun `AlreadyRecording has correct message`() {
        val error = AudioError.AlreadyRecording()
        assertEquals("A recording is already in progress", error.message)
    }

    @Test
    fun `AlreadyPlaying has correct message`() {
        val error = AudioError.AlreadyPlaying()
        assertEquals("A playback session is already active", error.message)
    }

    @Test
    fun `NoRecordingData has correct message`() {
        val error = AudioError.NoRecordingData()
        assertEquals("No recording data available", error.message)
    }

    @Test
    fun `Unknown wraps cause with message`() {
        val cause = IllegalStateException("Something went wrong")
        val error = AudioError.Unknown(cause)
        assertTrue(error.message!!.contains("Unknown audio error"))
        assertTrue(error.message!!.contains("Something went wrong"))
        assertEquals(cause, error.cause)
        assertEquals(cause, error.originalCause)
    }

    @Test
    fun `Unknown handles null message in cause`() {
        val cause = RuntimeException()  // No message
        val error = AudioError.Unknown(cause)
        assertNotNull(error.message)
        assertTrue(error.message!!.contains("RuntimeException"))
    }

    @Test
    fun `from returns same AudioError unchanged`() {
        val original = AudioError.PermissionDenied()
        val result = AudioError.from(original)
        assertEquals(original, result)
    }

    @Test
    fun `from converts AudioPermissionDeniedException to PermissionDenied`() {
        val exception = AudioPermissionDeniedException()
        val result = AudioError.from(exception)
        assertIs<AudioError.PermissionDenied>(result)
    }

    @Test
    fun `from wraps unknown exceptions in Unknown`() {
        val exception = IllegalArgumentException("Bad input")
        val result = AudioError.from(exception)
        assertIs<AudioError.Unknown>(result)
        assertEquals(exception, result.originalCause)
    }

    @Test
    fun `AudioError is Throwable`() {
        val error: Throwable = AudioError.PermissionDenied()
        assertNotNull(error.message)
    }

    @Test
    fun `PermissionDenied equality works correctly`() {
        val error1 = AudioError.PermissionDenied()
        val error2 = AudioError.PermissionDenied()
        assertEquals(error1, error2)
        assertEquals(error1.hashCode(), error2.hashCode())
    }

    @Test
    fun `Each error creates fresh stack trace`() {
        val error1 = AudioError.PermissionDenied()
        val error2 = AudioError.PermissionDenied()
        // They should be equal but different instances
        assertEquals(error1, error2)
        assertTrue(error1 !== error2)
    }
}
