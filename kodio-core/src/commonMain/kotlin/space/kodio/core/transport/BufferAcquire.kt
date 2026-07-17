package space.kodio.core.transport

/**
 * Reusable acquire slot for miniaudio-style SPSC acquire/commit against a
 * [PlaybackBuffer] slab. Callers should keep one instance per writer/reader
 * and reuse it so hot paths avoid allocating descriptors.
 */
internal class BufferAcquire {
    var slab: ByteArray = Empty
        private set
    var offset: Int = 0
        private set
    /** Contiguous bytes available in this acquire (may be less than requested on wrap). */
    var length: Int = 0
        private set

    fun set(slab: ByteArray, offset: Int, length: Int) {
        this.slab = slab
        this.offset = offset
        this.length = length
    }

    fun clear() {
        slab = Empty
        offset = 0
        length = 0
    }

    private companion object {
        val Empty = ByteArray(0)
    }
}
