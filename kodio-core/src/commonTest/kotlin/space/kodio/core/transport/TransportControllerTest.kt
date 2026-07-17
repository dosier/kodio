package space.kodio.core.transport

import space.kodio.core.AudioFormat
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.IntBitDepth
import space.kodio.core.PlaybackBufferConfig
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import space.kodio.core.SeekResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TransportControllerTest {

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

    private fun finiteController(
        knownBytes: Long,
        capacityOverride: PlaybackBufferConfig = PlaybackBufferConfig(maxBytes = 1L shl 20),
        sink: FakeAudioSink = FakeAudioSink(),
    ): Pair<TransportController, FakeAudioSink> {
        val buffer = PlaybackBuffer(format, knownByteCount = knownBytes, config = capacityOverride)
        val transport = TransportController(
            buffer = buffer,
            sink = sink,
            config = capacityOverride,
            knownByteCount = knownBytes,
        )
        return transport to sink
    }

    private fun liveController(
        maxBytes: Long = 64,
        sink: FakeAudioSink = FakeAudioSink(),
    ): Pair<TransportController, FakeAudioSink> {
        val config = PlaybackBufferConfig(maxBytes = maxBytes, maxDuration = 60.seconds)
        val buffer = PlaybackBuffer(format, knownByteCount = null, config = config)
        val transport = TransportController(
            buffer = buffer,
            sink = sink,
            config = config,
            knownByteCount = null,
        )
        return transport to sink
    }

    @Test
    fun seek_midWrite_abandonOldEpochWrites() {
        val (transport, sink) = finiteController(knownBytes = 256)
        transport.append(ByteArray(128) { idx -> idx.toByte() })

        assertEquals(16, transport.feed(16))
        val epoch0 = sink.writes.single().seekEpoch
        assertEquals(0L, sink.writes.single().mediaByteOffset)

        // 8000 Hz * 2 bpf = 16000 bytes/s → 1 ms = 16 bytes.
        assertEquals(SeekResult.Applied, transport.seekTo(2.milliseconds)) // byte 32
        assertTrue(transport.seekEpoch > epoch0)
        assertEquals(32L, transport.readCursor)
        assertEquals(1, sink.flushes.size)

        assertEquals(16, transport.feed(16))
        val after = sink.writes.filter { it.seekEpoch == transport.seekEpoch }
        assertEquals(1, after.size)
        assertEquals(32L, after.single().mediaByteOffset)
        assertEquals(32.toByte(), after.single().bytes[0])

        // No write after the epoch bump should carry the old cursor progression (offset 16).
        val stale = sink.writes.filter { it.seekEpoch == epoch0 && it.mediaByteOffset >= 16L }
        assertTrue(stale.isEmpty(), "no bytes from old cursor after epoch bump")
    }

    @Test
    fun pendingCursor_finite_waitsForIngestThenPlaysFromTarget() {
        val (transport, sink) = finiteController(knownBytes = 200)
        transport.append(ByteArray(20) { it.toByte() })

        // 5 ms → 80 bytes at 8 kHz / 16-bit mono.
        assertEquals(SeekResult.Applied, transport.seekTo(5.milliseconds))
        assertEquals(80L, transport.readCursor)
        assertEquals(0, transport.feed(32), "pending: nothing until ingest reaches target")
        assertTrue(sink.writes.isEmpty())

        transport.append(ByteArray(60) { (20 + it).toByte() }) // writePos = 80
        assertEquals(0, transport.feed(16), "still pending: need data at cursor (writePos == cursor)")

        transport.append(ByteArray(40) { (80 + it).toByte() }) // writePos = 120
        assertEquals(16, transport.feed(16))
        val write = sink.writes.single()
        assertEquals(80L, write.mediaByteOffset)
        assertEquals(80.toByte(), write.bytes[0])
    }

    @Test
    fun live_seekPastWritePosition_unsupported() {
        val (transport, _) = liveController(maxBytes = 128)
        transport.append(ByteArray(32) { 1 })
        assertEquals(SeekResult.Unsupported, transport.seekTo(10.milliseconds))
    }

    @Test
    fun live_seekInsideWindow_applied() {
        val (transport, sink) = liveController(maxBytes = 256)
        transport.append(ByteArray(64) { it.toByte() })
        assertEquals(SeekResult.Applied, transport.seekTo(1.milliseconds)) // 16 bytes
        assertEquals(16L, transport.readCursor)
        transport.feed(8)
        assertEquals(16L, sink.writes.single().mediaByteOffset)
    }

    @Test
    fun evictionPastCursor_clampsAndCountsUnderrun() {
        val config = PlaybackBufferConfig(maxBytes = 16, maxDuration = 60.seconds)
        val buffer = RingPlaybackBuffer(format, capacityBytes = 16)
        val sink = FakeAudioSink()
        val transport = TransportController(
            buffer = buffer,
            sink = sink,
            config = config,
            knownByteCount = null,
        )

        transport.append(ByteArray(16) { it.toByte() })
        assertEquals(SeekResult.Applied, transport.seekTo(0.milliseconds))
        assertEquals(0L, transport.readCursor)

        val before = transport.underrunCount
        // Evict everything the cursor pointed at.
        transport.append(ByteArray(16) { (100 + it).toByte() })
        assertEquals(16L until 32L, buffer.bufferedRange)
        assertTrue(transport.underrunCount > before, "eviction must count underrun")
        assertEquals(16L, transport.readCursor, "cursor clamps to new range start")

        assertEquals(16, transport.feed(16))
        assertEquals(32L, transport.readCursor)
        assertEquals(16L, sink.writes.single().mediaByteOffset)
        assertEquals(100.toByte(), sink.writes.single().bytes[0])
    }

    @Test
    fun pendingCursor_atEos_clampsWhenIngestCompletesEarly() {
        val (transport, _) = finiteController(knownBytes = 10_000)
        transport.append(ByteArray(40) { 1 })
        assertEquals(SeekResult.Applied, transport.seekTo(10.milliseconds)) // 160 bytes pending
        assertEquals(160L, transport.readCursor)

        transport.markIngestComplete()
        assertEquals(40L, transport.readCursor, "clamp pending to actual EOS")
        assertEquals(-1, transport.feed())
        assertTrue(transport.isFinished)
    }

    @Test
    fun virtualClock_advancesOnInjectedWriteDelay() {
        val sink = FakeAudioSink()
        sink.writeDelay = 5.milliseconds
        val (transport, _) = finiteController(knownBytes = 64, sink = sink)
        transport.append(ByteArray(32) { 1 })
        transport.feed(8)
        transport.feed(8)
        assertEquals(10_000_000L, sink.clock.nanos)
    }

    /**
     * Fixed-seed interleaved writer/reader/seeker schedule.
     *
     * True multi-threaded stress is not portable to JS (single-threaded); this
     * exercises the same API interleavings deterministically on every CI target.
     */
    @Test
    fun concurrentWriterReaderSeeker_fixedSeed() {
        val random = Random(42)
        val config = PlaybackBufferConfig(maxBytes = 4_096, maxDuration = 60.seconds)
        val known = 20_000L
        val buffer = PlaybackBuffer(format, knownByteCount = known, config = config)
        val sink = FakeAudioSink()
        val transport = TransportController(
            buffer = buffer,
            sink = sink,
            config = config,
            knownByteCount = known,
        )
        assertTrue(transport.isKnownFinite)

        var written = 0L
        var seeks = 0
        repeat(5_000) {
            when (random.nextInt(3)) {
                0 -> {
                    if (written < known) {
                        val size = minOf(random.nextInt(1, 64) * 2, (known - written).toInt())
                        transport.append(ByteArray(size) { 1 })
                        written += size
                        if (written >= known) transport.markIngestComplete()
                    }
                }
                1 -> transport.feed(128)
                else -> {
                    transport.seekTo(random.nextInt(0, 30).milliseconds)
                    seeks++
                }
            }
        }
        if (!transport.isIngestComplete) {
            if (written < known) {
                transport.append(ByteArray((known - written).toInt()) { 1 })
            }
            transport.markIngestComplete()
        }
        // Drain / prime remaining.
        transport.prime()
        repeat(10_000) {
            if (transport.feed(256) < 0) return@repeat
        }

        assertTrue(seeks > 0)
        assertTrue(transport.isIngestComplete)
        val lastEpoch = transport.seekEpoch
        val lastWrites = sink.writesAtEpoch(lastEpoch)
        for (i in 1 until lastWrites.size) {
            assertTrue(
                lastWrites[i].mediaByteOffset >= lastWrites[i - 1].mediaByteOffset,
                "epoch $lastEpoch offsets must be non-decreasing",
            )
        }
        // Stale-epoch writes must never appear after a newer epoch flush in the transcript order.
        var maxEpochSeen = -1L
        for (event in sink.writes) {
            assertTrue(event.seekEpoch >= 0)
            if (event.seekEpoch >= maxEpochSeen) {
                maxEpochSeen = event.seekEpoch
            }
        }
        assertTrue(maxEpochSeen <= transport.seekEpoch)
    }

    @Test
    fun seek_evictedHistory_unsupported() {
        val buffer = RingPlaybackBuffer(format, capacityBytes = 16)
        val transport = TransportController(
            buffer = buffer,
            sink = FakeAudioSink(),
            config = PlaybackBufferConfig(maxBytes = 16),
            knownByteCount = null,
        )
        transport.append(ByteArray(32) { 1 }) // range [16, 32)
        assertEquals(SeekResult.Unsupported, transport.seekTo(0.milliseconds))
    }
}
