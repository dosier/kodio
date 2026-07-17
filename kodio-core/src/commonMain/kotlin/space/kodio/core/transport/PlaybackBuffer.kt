@file:OptIn(ExperimentalAtomicApi::class)

package space.kodio.core.transport

import space.kodio.core.AudioFormat
import space.kodio.core.PlaybackBufferConfig
import space.kodio.core.util.namedLogger
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.min

private val logger = namedLogger("PlaybackBuffer")

/**
 * Internal seekable PCM store shared by ingest (writer) and transport (reader).
 *
 * Absolute offsets start at 0 for the first ingested byte. [bufferedRange] is
 * end-exclusive (`start until writePosition`). [append] always copies into slab
 * storage (never retains caller arrays).
 *
 * Concurrency: one writer, one reader (SPSC). Acquire/commit avoids per-byte
 * allocations; growable resize uses a short critical section.
 */
internal interface PlaybackBuffer {
    val format: AudioFormat
    val mode: PlaybackBufferMode

    /** Retained absolute byte offsets (`start until writePosition`). */
    val bufferedRange: LongRange

    /** Next append offset (absolute, never decreases). */
    val writePosition: Long

    /** Always accepts the whole chunk: growable grows, ring evicts oldest. */
    fun append(chunk: ByteArray)

    /**
     * Copies up to [length] retained bytes at absolute [at] into [dst].
     * Returns bytes copied (`0` if none retained there).
     */
    fun read(at: Long, dst: ByteArray, offset: Int, length: Int): Int

    fun contains(byteOffset: Long): Boolean

    /**
     * Acquire a contiguous writable region of up to [requested] bytes into [out].
     * Returns acquired length (`0` if [requested] ≤ 0). On a ring wrap, only the
     * first contiguous segment is returned — call again after [commitWrite].
     */
    fun acquireWrite(requested: Int, out: BufferAcquire): Int

    /** Publishes [written] bytes previously acquired via [acquireWrite]. */
    fun commitWrite(written: Int)

    /**
     * Acquire a contiguous readable region at absolute [at] into [out].
     * Returns acquired length (`0` if none retained there / wrap needs another call).
     */
    fun acquireRead(at: Long, requested: Int, out: BufferAcquire): Int
}

/**
 * Creates a [PlaybackBuffer] using the normative retention policy for
 * [knownByteCount] (converted-flow size hint).
 */
internal fun PlaybackBuffer(
    format: AudioFormat,
    knownByteCount: Long?,
    config: PlaybackBufferConfig = PlaybackBufferConfig(),
): PlaybackBuffer {
    val mode = resolvePlaybackBufferMode(knownByteCount, format, config)
    logger.debug {
        "create PlaybackBuffer mode=$mode formatRate=${format.sampleRate} " +
            "knownByteCount=$knownByteCount maxBytes=${config.maxBytes}"
    }
    return when (mode) {
        is PlaybackBufferMode.Ring -> RingPlaybackBuffer(format, mode.capacityBytes)
        is PlaybackBufferMode.Growable -> GrowablePlaybackBuffer(format, mode.reserveBytes)
    }
}

internal class RingPlaybackBuffer(
    override val format: AudioFormat,
    capacityBytes: Long,
) : PlaybackBuffer {

    init {
        require(capacityBytes > 0) { "ring capacity must be positive" }
        require(capacityBytes <= Int.MAX_VALUE) {
            "ring capacity $capacityBytes exceeds Int.MAX_VALUE slab limit"
        }
    }

    override val mode: PlaybackBufferMode = PlaybackBufferMode.Ring(capacityBytes)

    private val capacity: Int = capacityBytes.toInt()
    private val slab = ByteArray(capacity)
    private val writePos = AtomicLong(0L)
    private var pendingWriteAcquired: Int = 0

    override val writePosition: Long
        get() = writePos.load()

    override val bufferedRange: LongRange
        get() {
            val wp = writePos.load()
            val start = if (wp > capacity) wp - capacity else 0L
            return start until wp
        }

    override fun contains(byteOffset: Long): Boolean = byteOffset in bufferedRange

    override fun append(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        val acquire = BufferAcquire()
        var src = 0
        while (src < chunk.size) {
            val n = acquireWrite(chunk.size - src, acquire)
            if (n <= 0) break
            chunk.copyInto(acquire.slab, acquire.offset, src, src + n)
            commitWrite(n)
            src += n
        }
    }

    override fun read(at: Long, dst: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= dst.size) {
            "dst bounds offset=$offset length=$length size=${dst.size}"
        }
        if (length == 0) return 0
        val acquire = BufferAcquire()
        var written = 0
        var cursor = at
        while (written < length) {
            val n = acquireRead(cursor, length - written, acquire)
            if (n <= 0) break
            acquire.slab.copyInto(dst, offset + written, acquire.offset, acquire.offset + n)
            written += n
            cursor += n
        }
        return written
    }

    override fun acquireWrite(requested: Int, out: BufferAcquire): Int {
        if (requested <= 0) {
            out.clear()
            pendingWriteAcquired = 0
            return 0
        }
        val wp = writePos.load()
        val phys = (wp % capacity).toInt()
        val contiguous = min(requested, capacity - phys)
        out.set(slab, phys, contiguous)
        pendingWriteAcquired = contiguous
        return contiguous
    }

    override fun commitWrite(written: Int) {
        require(written >= 0) { "written must be non-negative" }
        require(written <= pendingWriteAcquired) {
            "commitWrite($written) exceeds acquired $pendingWriteAcquired"
        }
        if (written > 0) writePos.addAndFetch(written.toLong())
        pendingWriteAcquired = 0
    }

    override fun acquireRead(at: Long, requested: Int, out: BufferAcquire): Int {
        if (requested <= 0) {
            out.clear()
            return 0
        }
        val wp = writePos.load()
        val start = if (wp > capacity) wp - capacity else 0L
        if (at < start || at >= wp) {
            out.clear()
            return 0
        }
        val available = (wp - at).coerceAtMost(requested.toLong()).toInt()
        val phys = (at % capacity).toInt()
        val contiguous = min(available, capacity - phys)
        out.set(slab, phys, contiguous)
        return contiguous
    }
}

internal class GrowablePlaybackBuffer(
    override val format: AudioFormat,
    reserveBytes: Long,
) : PlaybackBuffer {

    override val mode: PlaybackBufferMode = PlaybackBufferMode.Growable(reserveBytes)

    private val lock = SpinLock()
    private val storage = AtomicReference(
        ByteArray(initialCapacity(reserveBytes)),
    )
    private val writePos = AtomicLong(0L)
    private var pendingWriteAcquired: Int = 0
    private var pendingWriteOffset: Int = 0

    override val writePosition: Long
        get() = writePos.load()

    override val bufferedRange: LongRange
        get() {
            val wp = writePos.load()
            return 0L until wp
        }

    override fun contains(byteOffset: Long): Boolean = byteOffset in bufferedRange

    override fun append(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        lock.withLock {
            ensureCapacity(writePos.load() + chunk.size)
            val slab = storage.load()
            val wp = writePos.load().toInt()
            chunk.copyInto(slab, wp)
            writePos.addAndFetch(chunk.size.toLong())
        }
    }

    override fun read(at: Long, dst: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= dst.size) {
            "dst bounds offset=$offset length=$length size=${dst.size}"
        }
        if (length == 0) return 0
        return lock.withLock {
            val wp = writePos.load()
            if (at < 0L || at >= wp) return@withLock 0
            val n = min(length.toLong(), wp - at).toInt()
            val slab = storage.load()
            slab.copyInto(dst, offset, at.toInt(), at.toInt() + n)
            n
        }
    }

    override fun acquireWrite(requested: Int, out: BufferAcquire): Int {
        if (requested <= 0) {
            out.clear()
            pendingWriteAcquired = 0
            return 0
        }
        return lock.withLock {
            val wp = writePos.load()
            ensureCapacity(wp + requested)
            val slab = storage.load()
            val offset = wp.toInt()
            out.set(slab, offset, requested)
            pendingWriteAcquired = requested
            pendingWriteOffset = offset
            requested
        }
    }

    override fun commitWrite(written: Int) {
        require(written >= 0) { "written must be non-negative" }
        lock.withLock {
            require(written <= pendingWriteAcquired) {
                "commitWrite($written) exceeds acquired $pendingWriteAcquired"
            }
            if (written > 0) writePos.addAndFetch(written.toLong())
            pendingWriteAcquired = 0
            pendingWriteOffset = 0
        }
    }

    override fun acquireRead(at: Long, requested: Int, out: BufferAcquire): Int {
        if (requested <= 0) {
            out.clear()
            return 0
        }
        return lock.withLock {
            val wp = writePos.load()
            if (at < 0L || at >= wp) {
                out.clear()
                return@withLock 0
            }
            val n = min(requested.toLong(), wp - at).toInt()
            out.set(storage.load(), at.toInt(), n)
            n
        }
    }

    private fun ensureCapacity(needed: Long) {
        require(needed <= Int.MAX_VALUE) {
            "growable buffer exceeds Int.MAX_VALUE ($needed)"
        }
        val slab = storage.load()
        if (needed <= slab.size) return
        var newSize = slab.size.coerceAtLeast(1)
        while (newSize < needed) {
            val doubled = newSize.toLong() * 2
            newSize = if (doubled > Int.MAX_VALUE) Int.MAX_VALUE else doubled.toInt()
        }
        val grown = slab.copyOf(newSize)
        storage.store(grown)
        logger.debug { "growable resize ${slab.size} -> $newSize (needed=$needed)" }
    }

    private companion object {
        fun initialCapacity(reserveBytes: Long): Int {
            val hint = reserveBytes.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            return hint.coerceAtLeast(256)
        }
    }
}

/**
 * Tiny spinlock for short growable critical sections in commonMain
 * (no platform Lock API). Fine for SPSC + occasional seeker; not for RT callbacks.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SpinLock {
    private val state = kotlin.concurrent.atomics.AtomicInt(0)

    inline fun <T> withLock(block: () -> T): T {
        while (!state.compareAndSet(0, 1)) {
            // spin
        }
        try {
            return block()
        } finally {
            state.store(0)
        }
    }
}
