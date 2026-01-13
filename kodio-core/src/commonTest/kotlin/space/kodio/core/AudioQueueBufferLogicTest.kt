package space.kodio.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that verify the buffer handling logic in MacosAudioQueue.
 * 
 * These tests simulate the callback logic and verify correct behavior.
 */
class AudioQueueBufferLogicTest {

    /**
     * Simulates the CURRENT (fixed) implementation from MacosAudioQueue.createInput().
     * 
     * Returns true if buffer would be re-enqueued, false otherwise.
     */
    private fun callbackLogic(
        audioData: ByteArray,
        channel: Channel<ByteArray>
    ): Boolean {
        var bufferReEnqueued = false
        
        // This is the ACTUAL logic from MacosAudioQueue.kt (fixed version)
        if (audioData.isEmpty()) {
            println("Input callback called but audioData is empty, re-enqueuing")
            bufferReEnqueued = true // Buffer must be re-enqueued to prevent starvation
        } else {
            val result = channel.trySend(audioData)
            if (!result.isClosed) {
                bufferReEnqueued = true
            }
        }
        
        return bufferReEnqueued
    }

    // ==================== BUFFER RE-ENQUEUE TESTS ====================

    /**
     * Verifies that empty buffers are correctly re-enqueued.
     * 
     * This prevents buffer starvation and choppy audio.
     */
    @Test
    fun `empty buffer is re-enqueued to prevent buffer starvation`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val emptyData = ByteArray(0)
        
        val reEnqueued = callbackLogic(emptyData, channel)
        
        assertTrue(
            reEnqueued,
            "Empty buffer must be re-enqueued to prevent buffer starvation"
        )
        
        channel.close()
    }

    /**
     * Verifies that buffer pool does not deplete over time with occasional empty data.
     */
    @Test
    fun `buffer pool does not deplete over time`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val initialBufferCount = 5 // Updated to match new config
        var availableBuffers = initialBufferCount
        
        // Simulate 20 callbacks with 10% empty data rate
        repeat(20) { iteration ->
            val audioData = if (iteration % 10 == 0) ByteArray(0) else ByteArray(4800)
            val reEnqueued = callbackLogic(audioData, channel)
            
            if (!reEnqueued) {
                availableBuffers--
            }
        }
        
        // With fix: All buffers should be retained
        assertEquals(
            initialBufferCount, availableBuffers,
            "Buffer pool should not deplete. Lost ${initialBufferCount - availableBuffers} buffers."
        )
        
        channel.close()
    }

    // ==================== VERIFICATION TESTS ====================

    /**
     * Verifies that empty buffers are correctly re-enqueued.
     */
    @Test
    fun `empty buffer is correctly re-enqueued`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val emptyData = ByteArray(0)
        
        val reEnqueued = callbackLogic(emptyData, channel)
        
        assertTrue(
            reEnqueued,
            "Buffer should be re-enqueued even when audioData is empty"
        )
        
        channel.close()
    }

    /**
     * Verifies that normal (non-empty) data is handled correctly.
     */
    @Test
    fun `normal data re-enqueues buffer and emits data`() = runTest {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val normalData = ByteArray(4800) { it.toByte() } // Updated to match new buffer size
        
        val reEnqueued = callbackLogic(normalData, channel)
        
        assertTrue(reEnqueued, "Buffer should be re-enqueued for normal data")
        
        // Verify data was sent to channel
        val received = channel.tryReceive()
        assertTrue(received.isSuccess, "Data should be in the channel")
        assertEquals(4800, received.getOrNull()?.size, "Data size should match")
        
        channel.close()
    }

    /**
     * Verifies backpressure handling - buffer re-enqueued even when channel is full.
     */
    @Test
    fun `backpressure re-enqueues buffer but loses data`() = runTest {
        // Channel with capacity 0 - trySend will always fail
        val channel = Channel<ByteArray>(capacity = 0)
        val normalData = ByteArray(4800) // Updated to match new buffer size
        
        val reEnqueued = callbackLogic(normalData, channel)
        
        // Buffer IS re-enqueued (correct behavior for backpressure)
        assertTrue(reEnqueued, "Buffer should be re-enqueued even when channel is full")
        
        // But the data was lost (acceptable for backpressure)
        val received = channel.tryReceive()
        assertFalse(received.isSuccess, "Data was lost due to backpressure (expected)")
        
        channel.close()
    }
}
