package space.kodio.core.io.files.mp3.id3

/** Size of the ID3v2 header (before the tag body). */
internal const val ID3V2_HEADER_SIZE = 10

/** Fixed size of a classic ID3v1 tag at end-of-file. */
internal const val ID3V1_TAG_SIZE = 128

/**
 * Returns the byte offset of the first audio byte after a leading ID3v2 tag,
 * or 0 when none is present.
 */
internal fun skipId3v2Tag(bytes: ByteArray): Int {
    if (bytes.size < ID3V2_HEADER_SIZE) return 0
    if (bytes[0] != 'I'.code.toByte() ||
        bytes[1] != 'D'.code.toByte() ||
        bytes[2] != '3'.code.toByte()
    ) {
        return 0
    }
    val size = readSyncsafeInt(bytes, 6)
    return (ID3V2_HEADER_SIZE + size).coerceAtMost(bytes.size)
}

/**
 * Returns the exclusive end index of MPEG audio bytes, excluding a trailing
 * ID3v1 "TAG" footer when present.
 */
internal fun excludeTrailingId3v1(bytes: ByteArray, start: Int): Int {
    if (bytes.size - start < ID3V1_TAG_SIZE) return bytes.size
    val off = bytes.size - ID3V1_TAG_SIZE
    if (off < start) return bytes.size
    if (bytes[off] == 'T'.code.toByte() &&
        bytes[off + 1] == 'A'.code.toByte() &&
        bytes[off + 2] == 'G'.code.toByte()
    ) {
        return off
    }
    return bytes.size
}
