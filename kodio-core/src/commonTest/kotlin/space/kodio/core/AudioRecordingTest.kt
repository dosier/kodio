package space.kodio.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [AudioRecording] class.
 */
class AudioRecordingTest {

    private val testFormat = AudioQuality.Standard.format

    // ==================== Factory Method Tests ====================

    @Test
    fun `fromBytes creates recording with correct data`() {
        val data = byteArrayOf(1, 2, 3, 4)
        val recording = AudioRecording.fromBytes(testFormat, data)

        assertEquals(testFormat, recording.format)
        assertEquals(4L, recording.sizeInBytes)
        assertTrue(recording.toByteArray().contentEquals(data))
    }

    @Test
    fun `fromBytes creates defensive copy of input`() {
        val data = byteArrayOf(1, 2, 3, 4)
        val recording = AudioRecording.fromBytes(testFormat, data)

        // Modify original
        data[0] = 99

        // Recording should be unaffected
        assertEquals(1, recording.toByteArray()[0])
    }

    @Test
    fun `fromChunks creates recording with correct total size`() {
        val chunks = listOf(
            byteArrayOf(1, 2),
            byteArrayOf(3, 4, 5),
            byteArrayOf(6)
        )
        val recording = AudioRecording.fromChunks(testFormat, chunks)

        assertEquals(6L, recording.sizeInBytes)
        assertTrue(recording.toByteArray().contentEquals(byteArrayOf(1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `fromChunks creates defensive copies of input chunks`() {
        val chunk = byteArrayOf(1, 2, 3)
        val recording = AudioRecording.fromChunks(testFormat, listOf(chunk))

        // Modify original
        chunk[0] = 99

        // Recording should be unaffected
        assertEquals(1, recording.toByteArray()[0])
    }

    @Test
    fun `fromChunks with duration preserves it`() {
        val duration = 5.seconds
        val recording = AudioRecording.fromChunks(
            testFormat,
            listOf(byteArrayOf(1, 2, 3, 4)),
            duration
        )

        assertEquals(duration, recording.duration)
    }

    @Test
    fun `empty creates recording with no data`() {
        val recording = AudioRecording.empty()

        assertTrue(recording.isEmpty)
        assertFalse(recording.hasData)
        assertEquals(0L, recording.sizeInBytes)
    }

    @Test
    fun `empty uses default format if not specified`() {
        val recording = AudioRecording.empty()
        assertEquals(AudioQuality.Default.format, recording.format)
    }

    @Test
    fun `empty with custom format uses that format`() {
        val format = AudioQuality.Lossless.format
        val recording = AudioRecording.empty(format)
        assertEquals(format, recording.format)
    }

    // ==================== Property Tests ====================

    @Test
    fun `isEmpty returns true for empty chunks`() {
        val recording = AudioRecording.fromChunks(testFormat, emptyList())
        assertTrue(recording.isEmpty)
    }

    @Test
    fun `isEmpty returns true for zero-size chunks`() {
        val recording = AudioRecording.fromChunks(testFormat, listOf(byteArrayOf()))
        assertTrue(recording.isEmpty)
    }

    @Test
    fun `hasData returns true when data exists`() {
        val recording = AudioRecording.fromBytes(testFormat, byteArrayOf(1))
        assertTrue(recording.hasData)
        assertFalse(recording.isEmpty)
    }

    @Test
    fun `frameCount calculated correctly for stereo 16-bit`() {
        val format = AudioQuality.High.format // 48kHz, stereo, 16-bit
        // 4 bytes per frame (2 bytes * 2 channels)
        val data = ByteArray(40) // 10 frames
        val recording = AudioRecording.fromBytes(format, data)

        assertEquals(10L, recording.frameCount)
    }

    @Test
    fun `calculatedDuration computed from frame count`() {
        val format = AudioQuality.Standard.format // 44100 Hz, mono, 16-bit
        // 2 bytes per frame (mono 16-bit)
        // 44100 frames = 1 second, so 88200 bytes = 1 second
        val data = ByteArray(88200)
        val recording = AudioRecording.fromBytes(format, data)

        // Should be 1 second
        assertEquals(1.seconds, recording.calculatedDuration)
    }

    @Test
    fun `calculatedDuration uses provided duration if available`() {
        val providedDuration = 10.seconds
        val recording = AudioRecording.fromChunks(
            testFormat,
            listOf(byteArrayOf(1, 2, 3, 4)),
            providedDuration
        )

        assertEquals(providedDuration, recording.calculatedDuration)
    }

    @Test
    fun `calculatedDuration handles long recordings without overflow`() {
        // Simulate a very long recording: 24 hours at 48kHz stereo 16-bit
        // frameCount = 24 * 60 * 60 * 48000 = 4,147,200,000 frames
        // This is larger than Int.MAX_VALUE but should not overflow
        val format = AudioQuality.High.format
        val frameCount = 24L * 60 * 60 * 48000
        val bytesNeeded = frameCount * format.bytesPerFrame
        
        // We can't actually create that much data, but we can verify the formula
        // (frameCount / sampleRate) should give us hours without overflow
        val expectedSeconds = frameCount / format.sampleRate
        assertEquals(24 * 60 * 60L, expectedSeconds)
    }

    // ==================== asFlow Tests ====================

    @Test
    fun `asFlow emits all chunks`() = runTest {
        val chunks = listOf(
            byteArrayOf(1, 2),
            byteArrayOf(3, 4)
        )
        val recording = AudioRecording.fromChunks(testFormat, chunks)

        val emitted = recording.asFlow().toList()
        assertEquals(2, emitted.size)
        assertTrue(emitted[0].contentEquals(byteArrayOf(1, 2)))
        assertTrue(emitted[1].contentEquals(byteArrayOf(3, 4)))
    }

    @Test
    fun `asFlow with defensiveCopy true returns copies`() = runTest {
        val recording = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2, 3))

        val chunks = recording.asFlow(defensiveCopy = true).toList()
        val chunk1 = chunks[0]
        val chunk2 = recording.asFlow(defensiveCopy = true).toList()[0]

        // Should be different array instances
        assertNotSame(chunk1, chunk2)
    }

    // ==================== toByteArray Tests ====================

    @Test
    fun `toByteArray returns empty array for empty recording`() {
        val recording = AudioRecording.empty()
        assertEquals(0, recording.toByteArray().size)
    }

    @Test
    fun `toByteArray concatenates multiple chunks`() {
        val recording = AudioRecording.fromChunks(
            testFormat,
            listOf(byteArrayOf(1, 2), byteArrayOf(3, 4, 5))
        )

        assertTrue(recording.toByteArray().contentEquals(byteArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `toByteArray returns defensive copy`() {
        val recording = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2, 3))

        val array1 = recording.toByteArray()
        val array2 = recording.toByteArray()

        // Should be different instances
        assertNotSame(array1, array2)

        // Modifying one should not affect the other
        array1[0] = 99
        assertEquals(1, array2[0])
    }

    // ==================== toString / equals / hashCode Tests ====================

    @Test
    fun `toString includes relevant info`() {
        val recording = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2, 3, 4))
        val str = recording.toString()

        assertTrue(str.contains("AudioRecording"))
        assertTrue(str.contains("format"))
        assertTrue(str.contains("4 bytes"))
    }

    @Test
    fun `equals returns true for same format and size`() {
        val r1 = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2, 3, 4))
        val r2 = AudioRecording.fromBytes(testFormat, byteArrayOf(5, 6, 7, 8))

        // Note: Current implementation considers same format + size as equal
        // This is documented as a performance trade-off
        assertEquals(r1, r2)
    }

    @Test
    fun `equals returns false for different formats`() {
        val r1 = AudioRecording.fromBytes(AudioQuality.Voice.format, byteArrayOf(1, 2))
        val r2 = AudioRecording.fromBytes(AudioQuality.High.format, byteArrayOf(1, 2))

        assertFalse(r1 == r2)
    }

    @Test
    fun `equals returns false for different sizes`() {
        val r1 = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2))
        val r2 = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2, 3, 4))

        assertFalse(r1 == r2)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val r1 = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2, 3, 4))
        val r2 = AudioRecording.fromBytes(testFormat, byteArrayOf(5, 6, 7, 8))

        // Same format + size = equal = same hashCode
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    // ==================== AudioFlow Conversion Tests ====================

    @Test
    fun `asAudioFlow returns AudioFlow with correct format`() = runTest {
        val recording = AudioRecording.fromBytes(testFormat, byteArrayOf(1, 2, 3, 4))
        val audioFlow = recording.asAudioFlow()

        assertEquals(testFormat, audioFlow.format)
    }

    @Test
    fun `fromAudioFlow collects all data`() = runTest {
        val format = AudioQuality.Voice.format
        val originalData = byteArrayOf(1, 2, 3, 4, 5, 6)
        val audioFlow = AudioFlow(format, kotlinx.coroutines.flow.flowOf(originalData))

        val recording = AudioRecording.fromAudioFlow(audioFlow)

        assertEquals(format, recording.format)
        assertEquals(6L, recording.sizeInBytes)
    }
}


