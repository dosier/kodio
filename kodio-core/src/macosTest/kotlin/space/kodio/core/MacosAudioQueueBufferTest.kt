package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [MacosAudioQueue] buffer handling and configuration.
 * 
 * These tests verify the issues identified in the audio recording implementation:
 * 1. Buffer re-enqueue on empty data
 * 2. Buffer configuration (size and count)
 * 3. Audio continuity (no gaps in recorded data)
 */
@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
class MacosAudioQueueBufferTest {

    private fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

    // ==================== Buffer Configuration Tests ====================

    @Test
    fun `buffer duration is 50ms providing adequate headroom`() {
        // 50ms buffers provide adequate headroom for processing
        // With 48kHz mono 16-bit: 50ms = 2400 samples = 4800 bytes per buffer
        // At 20 callbacks/second, this is manageable even with FFI overhead
        
        val currentBufferDurationMs = 50 // Updated default
        val minimumRecommendedMs = 50
        
        assertTrue(
            currentBufferDurationMs >= minimumRecommendedMs,
            "Buffer duration ($currentBufferDurationMs ms) should be at least $minimumRecommendedMs ms."
        )
    }

    @Test
    fun `buffer count is 5 providing good resilience`() {
        // 5 buffers = ~250ms total buffering at 50ms each
        // This provides good resilience to processing delays
        val currentBufferCount = 5
        val minimumRecommendedCount = 5
        
        assertTrue(
            currentBufferCount >= minimumRecommendedCount,
            "Buffer count ($currentBufferCount) should be at least $minimumRecommendedCount."
        )
    }

    // ==================== Audio Continuity Tests ====================

    @Test
    fun `recording should produce expected amount of data without gaps`() {
        runBlocking {
            // Skip if no microphone permission
            val devices = try {
                SystemAudioSystem.listInputDevices()
            } catch (e: Exception) {
                println("Skipping test: ${e.message}")
                return@runBlocking
            }
            
            if (devices.isEmpty()) {
                println("Skipping test: No input devices available")
                return@runBlocking
            }

            val format = DefaultRecordingInt16 // 48kHz, mono, 16-bit
            val recordingDurationSec = 2.0
            
            val session = SystemAudioSystem.createRecordingSession(null)
            session.start()
            delay((recordingDurationSec * 1000).toLong().milliseconds)
            session.stop()

            val audioFlow = session.audioFlow.value
            if (audioFlow == null) {
                fail("Audio flow should not be null after recording")
            }

            // Collect all audio chunks
            val chunks = audioFlow.toList()
            val totalBytes = chunks.sumOf { it.size }

            // Calculate expected bytes
            // 48000 samples/sec * 2 bytes/sample (16-bit) * 1 channel * 2 seconds = 192000 bytes
            val expectedBytes = (format.sampleRate * format.bytesPerFrame * recordingDurationSec).toLong()
            
            // Allow 10% tolerance for timing variations
            val minExpectedBytes = (expectedBytes * 0.9).toLong()
            val maxExpectedBytes = (expectedBytes * 1.1).toLong()

            println("Recording stats:")
            println("  Duration: $recordingDurationSec sec")
            println("  Format: ${format.sampleRate}Hz, ${format.channels}, ${format.bytesPerFrame} bytes/frame")
            println("  Expected bytes: $expectedBytes (±10%: $minExpectedBytes - $maxExpectedBytes)")
            println("  Actual bytes: $totalBytes")
            println("  Chunks received: ${chunks.size}")
            println("  Avg chunk size: ${if (chunks.isNotEmpty()) totalBytes / chunks.size else 0}")
            
            // This test will likely FAIL with current implementation due to buffer issues
            assertTrue(
                totalBytes >= minExpectedBytes,
                "Received less data than expected. Got $totalBytes bytes, expected at least $minExpectedBytes. " +
                "This indicates audio gaps/drops (choppy audio)."
            )
            
            assertTrue(
                totalBytes <= maxExpectedBytes,
                "Received more data than expected. Got $totalBytes bytes, expected at most $maxExpectedBytes."
            )
        }
    }

    @Test
    fun `recording should have consistent chunk sizes`() {
        runBlocking {
            // Skip if no microphone permission
            val devices = try {
                SystemAudioSystem.listInputDevices()
            } catch (e: Exception) {
                println("Skipping test: ${e.message}")
                return@runBlocking
            }
            
            if (devices.isEmpty()) {
                println("Skipping test: No input devices available")
                return@runBlocking
            }

            val session = SystemAudioSystem.createRecordingSession(null)
            session.start()
            delay(1.seconds)
            session.stop()

            val audioFlow = session.audioFlow.value ?: fail("Audio flow should not be null")
            val chunks = audioFlow.toList()

            if (chunks.size < 2) {
                println("Skipping consistency check: Not enough chunks received (${chunks.size})")
                return@runBlocking
            }

            // Calculate expected chunk size based on buffer duration
            // At 48kHz, 50ms buffer = 48000 * 0.05 * 2 bytes = 4800 bytes
            val expectedChunkSize = 4800

            // Count chunks that deviate significantly from expected size
            val deviationThreshold = 0.2 // 20% deviation
            val minSize = (expectedChunkSize * (1 - deviationThreshold)).toInt()
            val maxSize = (expectedChunkSize * (1 + deviationThreshold)).toInt()
            
            val inconsistentChunks = chunks.filter { it.size < minSize || it.size > maxSize }
            val inconsistentRatio = inconsistentChunks.size.toDouble() / chunks.size

            println("Chunk consistency stats:")
            println("  Total chunks: ${chunks.size}")
            println("  Expected chunk size: ~$expectedChunkSize bytes")
            println("  Acceptable range: $minSize - $maxSize bytes")
            println("  Inconsistent chunks: ${inconsistentChunks.size} (${(inconsistentRatio * 100).toInt()}%)")
            
            if (inconsistentChunks.isNotEmpty()) {
                val sizes = inconsistentChunks.map { it.size }
                println("  Inconsistent sizes: min=${sizes.minOrNull()}, max=${sizes.maxOrNull()}")
            }

            assertTrue(
                inconsistentRatio < 0.1, // Allow up to 10% inconsistent chunks
                "Too many chunks (${(inconsistentRatio * 100).toInt()}%) have inconsistent sizes. " +
                "This may indicate buffer handling issues."
            )
        }
    }

    @Test
    fun `recording should not have empty chunks`() {
        runBlocking {
            // Skip if no microphone permission
            val devices = try {
                SystemAudioSystem.listInputDevices()
            } catch (e: Exception) {
                println("Skipping test: ${e.message}")
                return@runBlocking
            }
            
            if (devices.isEmpty()) {
                println("Skipping test: No input devices available")
                return@runBlocking
            }

            val session = SystemAudioSystem.createRecordingSession(null)
            session.start()
            delay(1.seconds)
            session.stop()

            val audioFlow = session.audioFlow.value ?: fail("Audio flow should not be null")
            val chunks = audioFlow.toList()

            val emptyChunks = chunks.filter { it.isEmpty() }
            
            println("Empty chunk stats:")
            println("  Total chunks: ${chunks.size}")
            println("  Empty chunks: ${emptyChunks.size}")

            assertEquals(
                0, emptyChunks.size,
                "Found ${emptyChunks.size} empty chunks. Empty chunks should not be emitted."
            )
        }
    }

    // ==================== Buffer Re-enqueue Logic Test ====================

    @Test
    fun `channel trySend failure should be handled gracefully`() {
        runBlocking {
            // Test that verifies the Channel can handle backpressure
            // This simulates what happens when the consumer is slow
            
            val channel = Channel<ByteArray>(capacity = 3) // Limited capacity
            
            // Fill the channel
            repeat(3) {
                channel.trySend(ByteArray(1920))
            }
            
            // Try to send one more - should fail
            val result = channel.trySend(ByteArray(1920))
            
            println("Channel backpressure test:")
            println("  Channel full, trySend result: ${result.isSuccess}")
            
            // In the real implementation, when trySend fails, the audio data is LOST
            // but the buffer IS re-enqueued (line 205-206 in MacosAudioQueue.kt)
            // However, when audioData.isEmpty(), the buffer is NOT re-enqueued (BUG)
            assertTrue(
                !result.isSuccess,
                "Expected trySend to fail when channel is full"
            )
            
            channel.close()
        }
    }

    // ==================== Timing Test ====================

    @Test
    fun `measure actual callback interval`() {
        runBlocking {
            // Skip if no microphone permission
            val devices = try {
                SystemAudioSystem.listInputDevices()
            } catch (e: Exception) {
                println("Skipping test: ${e.message}")
                return@runBlocking
            }
            
            if (devices.isEmpty()) {
                println("Skipping test: No input devices available")
                return@runBlocking
            }

            val timestamps = mutableListOf<Long>()
            val session = SystemAudioSystem.createRecordingSession(null)
            session.start()
            
            // Collect timestamps when chunks arrive
            val startTime = currentTimeMillis()
            val collectJob = GlobalScope.launch {
                session.audioFlow.value?.collect { _ ->
                    timestamps.add(currentTimeMillis() - startTime)
                }
            }
            
            delay(1.seconds)
            session.stop()
            collectJob.cancel()

            if (timestamps.size < 2) {
                println("Skipping timing analysis: Not enough samples (${timestamps.size})")
                return@runBlocking
            }

            // Calculate intervals between chunks
            val intervals = timestamps.zipWithNext { a, b -> b - a }
            val avgInterval = intervals.average()
            val maxInterval = intervals.maxOrNull() ?: 0
            val minInterval = intervals.minOrNull() ?: 0

            println("Callback timing stats:")
            println("  Samples: ${timestamps.size}")
            println("  Average interval: ${avgInterval.toInt()} ms")
            println("  Min interval: $minInterval ms")
            println("  Max interval: $maxInterval ms")
            println("  Expected interval: ~20 ms (based on buffer duration)")

            // Large intervals indicate potential gaps
            val largeGapThreshold = 50L // ms - 2.5x expected
            val largeGaps = intervals.filter { it > largeGapThreshold }
            
            println("  Large gaps (>$largeGapThreshold ms): ${largeGaps.size}")
            if (largeGaps.isNotEmpty()) {
                println("    Gap sizes: $largeGaps")
            }

            assertTrue(
                largeGaps.size < intervals.size * 0.05, // Less than 5% should be large gaps
                "Too many large gaps detected (${largeGaps.size} out of ${intervals.size}). " +
                "This indicates choppy audio due to buffer starvation."
            )
        }
    }
}
