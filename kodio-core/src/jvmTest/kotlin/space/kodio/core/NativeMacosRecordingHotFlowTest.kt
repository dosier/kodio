package space.kodio.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Documents and guards the hot-flow subscription contract used by
 * [NativeMacosAudioRecordingSession]: native callbacks emit via [MutableSharedFlow.tryEmit]
 * from a non-coroutine thread, so late live subscribers depend on [replay], not
 * [MutableSharedFlow.extraBufferCapacity] (extra slots are not replayed on subscribe).
 */
class NativeMacosRecordingHotFlowTest {

    @Test
    fun `extraBufferCapacity alone does not replay to late subscribers`() = runTest {
        val hot = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
        repeat(10) { hot.tryEmit(byteArrayOf(it.toByte())) }

        val received = mutableListOf<ByteArray>()
        val job = launch { hot.collect { received.add(it) } }
        advanceUntilIdle()

        hot.tryEmit(byteArrayOf(99))
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, received.size)
        assertEquals(99.toByte(), received.single()[0])
    }

    @Test
    fun `bounded replay delivers recent chunks to late subscribers`() = runTest {
        val hot = MutableSharedFlow<ByteArray>(replay = 64, extraBufferCapacity = 64)
        repeat(10) { hot.tryEmit(byteArrayOf(it.toByte())) }

        val replayed = hot.take(3).toList()
        assertEquals(listOf(0.toByte(), 1.toByte(), 2.toByte()), replayed.map { it[0] })
    }

    @Test
    fun `audioFlow exposed before tryEmit is visible to immediate collectors`() = runTest {
        val hot = MutableSharedFlow<ByteArray>(replay = 64, extraBufferCapacity = 64)
        val flow = AudioFlow(DefaultJvmRecordingAudioFormat, hot.asSharedFlow())

        val received = mutableListOf<ByteArray>()
        val job = launch { flow.collect { received.add(it) } }
        advanceUntilIdle()

        repeat(5) { hot.tryEmit(byteArrayOf(it.toByte())) }
        advanceUntilIdle()
        job.cancel()

        assertEquals(5, received.size)
        assertTrue(received.last()[0] == 4.toByte())
    }
}
