package space.kodio.core.io

import kotlinx.io.*
import space.kodio.core.Endianness

fun Buffer.writeLong(endianness: Endianness, long: Long) = when (endianness) {
    Endianness.Little -> writeLongLe(long)
    Endianness.Big -> writeLong(long)
}
fun Buffer.readLong(endianness: Endianness) = when (endianness) {
    Endianness.Little -> readLongLe()
    Endianness.Big -> readLong()
}

fun Buffer.writeShort(endianness: Endianness, short: Short) = when (endianness) {
    Endianness.Little -> writeShortLe(short)
    Endianness.Big -> writeShort(short)
}
fun Buffer.readShort(endianness: Endianness) = when (endianness) {
    Endianness.Little -> readShortLe()
    Endianness.Big -> readShort()
}

fun Buffer.writeInt(endianness: Endianness, int: Int) = when (endianness) {
    Endianness.Little -> writeIntLe(int)
    Endianness.Big -> writeInt(int)
}
fun Buffer.readInt(endianness: Endianness) = when (endianness) {
    Endianness.Little -> readIntLe()
    Endianness.Big -> readInt()
}

fun Buffer.writeUtf8(string: String) {
    val bytes = string.encodeToByteArray()
    writeInt(bytes.size)
    write(bytes)
}
fun Buffer.readUtf8(): String {
    val length = readInt()
    val bytes = readByteArray(length)
    return bytes.decodeToString()
}