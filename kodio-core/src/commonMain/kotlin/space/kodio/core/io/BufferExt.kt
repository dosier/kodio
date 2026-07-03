package space.kodio.core.io

import kotlinx.io.*
import space.kodio.core.Endianness

/** Writes [long] using [endianness] (little or big endian). */
fun Buffer.writeLong(endianness: Endianness, long: Long) = when (endianness) {
    Endianness.Little -> writeLongLe(long)
    Endianness.Big -> writeLong(long)
}
/** Reads a [Long] using [endianness] (little or big endian). */
fun Buffer.readLong(endianness: Endianness) = when (endianness) {
    Endianness.Little -> readLongLe()
    Endianness.Big -> readLong()
}

/** Writes [short] using [endianness] (little or big endian). */
fun Buffer.writeShort(endianness: Endianness, short: Short) = when (endianness) {
    Endianness.Little -> writeShortLe(short)
    Endianness.Big -> writeShort(short)
}
/** Reads a [Short] using [endianness] (little or big endian). */
fun Buffer.readShort(endianness: Endianness) = when (endianness) {
    Endianness.Little -> readShortLe()
    Endianness.Big -> readShort()
}

/** Writes [int] using [endianness] (little or big endian). */
fun Buffer.writeInt(endianness: Endianness, int: Int) = when (endianness) {
    Endianness.Little -> writeIntLe(int)
    Endianness.Big -> writeInt(int)
}
/** Reads an [Int] using [endianness] (little or big endian). */
fun Buffer.readInt(endianness: Endianness) = when (endianness) {
    Endianness.Little -> readIntLe()
    Endianness.Big -> readInt()
}

/**
 * Writes [string] as length-prefixed UTF-8: a big-endian 32-bit byte count
 * followed by the UTF-8 payload (same layout as [readUtf8]).
 */
fun Buffer.writeUtf8(string: String) {
    val bytes = string.encodeToByteArray()
    writeInt(bytes.size)
    write(bytes)
}
/**
 * Reads a length-prefixed UTF-8 string written by [writeUtf8]: a big-endian
 * 32-bit byte count followed by the UTF-8 payload.
 */
fun Buffer.readUtf8(): String {
    val length = readInt()
    val bytes = readByteArray(length)
    return bytes.decodeToString()
}