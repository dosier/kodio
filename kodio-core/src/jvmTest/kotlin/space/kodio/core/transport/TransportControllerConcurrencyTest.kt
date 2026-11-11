package space.kodio.core.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import space.kodio.core.AudioFormat
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.IntBitDepth
import space.kodio.core.PlaybackBufferConfig
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Multi-threaded stress (JVM) complementing the portable interleaved schedule in
 * commonTest. Fixed seed for reproducibility.
 */
class TransportControllerConcurrencyTest {

    private val format = AudioFormat(
        sampleRate = 8_000,
        channels = Channels.Mono,
        encoding = SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        ),
    )

    @Test
    fun concurrentWriterReaderSeeker_fixedSeed_multiThreaded() = runBlocking {
        val config = PlaybackBufferConfig(maxBytes = 4_096, maxDuration = 60.seconds)
        val known = 50_000L
        val buffer = PlaybackBuffer(format, knownByteCount = known, config = config)
        val sink = FakeAudioSink()
        val transport = TransportController(
            buffer = buffer,
            sink = sink,
            config = config,
            knownByteCount = known,
        )

        var appendErrors = 0
        var feedErrors = 0
        var seekErrors = 0

        val writer = launch(Dispatchers.Default) {
            var written = 0
            val local = Random(42)
            try {
                while (written < known) {
                    val size = local.nextInt(16, 257) * 2
                    val chunk = ByteArray(minOf(size, (known - written).toInt())) { 1 }
                    transport.append(chunk)
                    written += chunk.size
                    yield()
                }
                transport.markIngestComplete()
            } catch (_: Throwable) {
                appendErrors++
            }
        }

        val reader = launch(Dispatchers.Default) {
            try {
                var idle = 0
                while (!transport.isFinished && idle < 200_000) {
                    when (transport.feed(128)) {
                        -1 -> break
                        0 -> idle++
                        else -> idle = 0
                    }
                    yield()
                }
            } catch (_: Throwable) {
                feedErrors++
            }
        }

        val seeker = launch(Dispatchers.Default) {
            val local = Random(7)
            try {
                repeat(200) {
                    transport.seekTo(local.nextInt(0, 40).milliseconds)
                    yield()
                }
            } catch (_: Throwable) {
                seekErrors++
            }
        }

        writer.join()
        seeker.join()
        // Join the reader before draining: the controller is single-reader, so
        // feeding from here while it still runs would be two concurrent readers.
        reader.join()
        repeat(20_000) {
            if (transport.feed(256) < 0) return@repeat
        }

        assertEquals(0, appendErrors)
        assertEquals(0, feedErrors)
        assertEquals(0, seekErrors)
        assertTrue(transport.isIngestComplete)

        val lastEpoch = transport.seekEpoch
        val lastWrites = sink.writesAtEpoch(lastEpoch)
        for (i in 1 until lastWrites.size) {
            assertTrue(lastWrites[i].mediaByteOffset >= lastWrites[i - 1].mediaByteOffset)
        }
    }
}
