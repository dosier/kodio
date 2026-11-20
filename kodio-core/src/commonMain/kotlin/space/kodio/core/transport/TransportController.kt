@file:OptIn(ExperimentalAtomicApi::class)

package space.kodio.core.transport

import space.kodio.core.PlaybackBufferConfig
import space.kodio.core.SeekResult
import space.kodio.core.pcmSeekByteOffset
import space.kodio.core.util.namedLogger
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.time.Duration

private val logger = namedLogger("TransportController")

/**
 * Owns the transport read cursor, [seekEpoch], pending-cursor wait, and
 * underrun/eviction rules. Feeds an [AudioSink] from a [PlaybackBuffer].
 *
 * Seek is reject-only in v1 ([SeekResult.Applied] / [SeekResult.Unsupported]);
 * a future clamped result type is intentionally not used here. Known-finite
 * content may seek ahead of ingest (pending cursor); live/unbounded content
 * may not.
 *
 * [seekEpoch] is independent of session-level `playEpoch` (BaseAudioPlaybackSession):
 * bumping seek abandons in-flight device writes without cancelling an in-flight
 * play() handshake. Platform AudioQueue seek must NOT reuse channel-closing
 * teardown from stop/dispose; that is a later platform-glue concern. This
 * controller only moves the soft cursor and stamps epochs.
 */
internal class TransportController(
    val buffer: PlaybackBuffer,
    private val sink: AudioSink,
    private val config: PlaybackBufferConfig = PlaybackBufferConfig(),
    /**
     * Converted-flow size hint. Non-null enables pending-cursor seeks past
     * [PlaybackBuffer.writePosition] up to the expected total.
     */
    private val knownByteCount: Long?,
) {
    private val readCursorAtomic = AtomicLong(0L)
    private val seekEpochAtomic = AtomicLong(0L)
    private val underrunCountAtomic = AtomicLong(0L)
    private val ingestCompleteAtomic = AtomicBoolean(false)
    private val finishedAtomic = AtomicBoolean(false)
    private val ingestFailureRef = AtomicReference<Throwable?>(null)

    private val feedScratch: ByteArray =
        ByteArray(
            buffer.format.bytesForDuration(config.devicePrimeDuration)
                .coerceIn(256L, 64L * 1024L)
                .toInt()
                .coerceAtLeast(buffer.format.bytesPerFrame.coerceAtLeast(1)),
        )

    val readCursor: Long
        get() = readCursorAtomic.load()

    val seekEpoch: Long
        get() = seekEpochAtomic.load()

    val underrunCount: Long
        get() = underrunCountAtomic.load()

    val isFinished: Boolean
        get() = finishedAtomic.load()

    val isIngestComplete: Boolean
        get() = ingestCompleteAtomic.load()

    val isKnownFinite: Boolean
        get() = knownByteCount != null

    /**
     * Non-null when ingest ended with an error. The buffer then holds only the
     * PCM decoded before the failure; [feed] plays it out and reports EOS so the
     * session can surface the error instead of waiting for data that never comes.
     */
    val ingestFailure: Throwable?
        get() = ingestFailureRef.load()

    /**
     * Append converted PCM (ingest path). Does not move the read cursor.
     */
    fun append(chunk: ByteArray) {
        buffer.append(chunk)
        clampCursorIfEvicted()
    }

    fun markIngestComplete() {
        ingestCompleteAtomic.store(true)
        val wp = buffer.writePosition
        val cursor = readCursorAtomic.load()
        if (cursor > wp) {
            // Pending seek past actual EOS: clamp to final byte offset.
            logger.debug { "pending cursor $cursor clamped to EOS writePosition=$wp" }
            readCursorAtomic.store(wp)
        }
        maybeFinish()
    }

    /**
     * Ingest stopped with [cause] (e.g. a decode error mid-stream). Treated as a
     * truncated end of stream: retained PCM still plays, then [feed] returns `-1`.
     */
    fun markIngestFailed(cause: Throwable) {
        ingestFailureRef.store(cause)
        markIngestComplete()
    }

    /**
     * Move the software read cursor. Non-suspending; device flush is requested
     * synchronously via [AudioSink.flush]. [SeekResult.Applied] means the cursor
     * was accepted, not that audible playback has restarted.
     */
    fun seekTo(position: Duration): SeekResult {
        val format = buffer.format
        val bpf = format.bytesPerFrame
        if (bpf <= 0 || format.sampleRate <= 0) return SeekResult.Unsupported

        val target = frameAlignedByteOffset(position, format, knownByteCount)
        val range = buffer.bufferedRange
        val writePos = buffer.writePosition

        if (target < 0L) return SeekResult.Unsupported

        // Evicted history / before retained window.
        if (writePos > 0L && target < range.first) {
            logger.debug { "seekTo($position) -> Unsupported (evicted target=$target range=$range)" }
            return SeekResult.Unsupported
        }
        if (writePos == 0L && target > 0L && knownByteCount == null) {
            logger.debug { "seekTo($position) -> Unsupported (empty live buffer)" }
            return SeekResult.Unsupported
        }

        when {
            target < writePos -> {
                // Inside retained data (or at 0 on empty growable before first append).
                if (writePos > 0L && target < range.first) return SeekResult.Unsupported
                applySeek(target)
                return SeekResult.Applied
            }
            target == writePos && writePos == 0L -> {
                // Seek to start before any ingest.
                applySeek(0L)
                return SeekResult.Applied
            }
            knownByteCount != null -> {
                val expectedEnd = frameAlignDown(knownByteCount, bpf)
                if (target <= expectedEnd) {
                    // Pending cursor: wait for ingest (same path as underrun).
                    applySeek(target)
                    logger.debug {
                        "seekTo($position) -> Applied pending cursor=$target writePos=$writePos"
                    }
                    return SeekResult.Applied
                }
                logger.debug {
                    "seekTo($position) -> Unsupported (past expected end target=$target end=$expectedEnd)"
                }
                return SeekResult.Unsupported
            }
            else -> {
                // Live/unbounded: no wait-for-future-data past the trailing edge.
                logger.debug { "seekTo($position) -> Unsupported (live past writePos=$writePos)" }
                return SeekResult.Unsupported
            }
        }
    }

    /**
     * Pull up to [maxBytes] from the read cursor into the sink.
     * Returns bytes written to the sink, `0` on underrun/pending wait, or `-1` at EOS.
     */
    fun feed(maxBytes: Int = feedScratch.size): Int {
        if (finishedAtomic.load()) return -1
        clampCursorIfEvicted()

        val epoch = seekEpochAtomic.load()
        val cursor = readCursorAtomic.load()
        val writePos = buffer.writePosition

        if (cursor >= writePos) {
            if (ingestCompleteAtomic.load()) {
                finishedAtomic.store(true)
                return -1
            }
            underrunCountAtomic.fetchAndIncrement()
            return 0
        }

        val bpf = buffer.format.bytesPerFrame.coerceAtLeast(1)
        var want = maxBytes.coerceAtMost(feedScratch.size)
        want -= want % bpf
        if (want <= 0) want = minOf(feedScratch.size, (writePos - cursor).toInt().coerceAtLeast(1))

        val n = buffer.read(cursor, feedScratch, 0, want)
        if (n <= 0) {
            underrunCountAtomic.fetchAndIncrement()
            return 0
        }

        // Abandon if a seek landed while we were copying.
        if (seekEpochAtomic.load() != epoch) return 0

        sink.write(
            data = feedScratch,
            offset = 0,
            length = n,
            seekEpoch = epoch,
            mediaByteOffset = cursor,
        )

        // Advance only if the cursor is still ours (seek replaces it explicitly).
        if (seekEpochAtomic.load() != epoch) return 0
        if (!readCursorAtomic.compareAndSet(cursor, cursor + n.toLong())) return 0

        maybeFinish()
        logger.trace { "feed wrote=$n cursor=${cursor + n} epoch=$epoch" }
        return n
    }

    /**
     * Feed until [PlaybackBufferConfig.devicePrimeDuration] of PCM has been written
     * (or underrun/EOS). Returns total bytes primed.
     */
    fun prime(): Int {
        val target = buffer.format.bytesForDuration(config.devicePrimeDuration)
            .coerceAtLeast(buffer.format.bytesPerFrame.toLong())
        var total = 0
        while (total < target) {
            val n = feed()
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun applySeek(target: Long) {
        readCursorAtomic.store(target)
        val epoch = seekEpochAtomic.addAndFetch(1L)
        finishedAtomic.store(false)
        sink.flush(epoch)
        logger.debug { "applySeek cursor=$target epoch=$epoch" }
    }

    private fun clampCursorIfEvicted() {
        val range = buffer.bufferedRange
        if (range.isEmpty()) return
        // Called from both the ingest and the feed thread: only ever move the
        // cursor forward (CAS), or a stale read here would rewind it past audio
        // the feed thread already sent and replay it.
        while (true) {
            val cursor = readCursorAtomic.load()
            if (cursor >= range.first) return
            if (readCursorAtomic.compareAndSet(cursor, range.first)) {
                underrunCountAtomic.fetchAndIncrement()
                logger.debug {
                    "cursor clamped from $cursor to ${range.first} (ring eviction underrun)"
                }
                return
            }
        }
    }

    private fun maybeFinish() {
        if (!ingestCompleteAtomic.load()) return
        if (readCursorAtomic.load() >= buffer.writePosition) {
            finishedAtomic.store(true)
        }
    }

    private companion object {
        fun frameAlignedByteOffset(
            position: Duration,
            format: space.kodio.core.AudioFormat,
            knownByteCount: Long?,
        ): Long {
            val bpf = format.bytesPerFrame
            if (bpf <= 0) return 0L
            // Prefer Long path; when we have a known total use it as clamp ceiling.
            val ceiling = knownByteCount ?: Long.MAX_VALUE
            return pcmSeekByteOffset(position, format, ceiling)
        }

        fun frameAlignDown(bytes: Long, bytesPerFrame: Int): Long {
            if (bytesPerFrame <= 0) return bytes
            val bpf = bytesPerFrame.toLong()
            return (bytes / bpf) * bpf
        }
    }
}
