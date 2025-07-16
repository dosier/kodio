package space.kodio.core.io

import space.kodio.core.Endianness
import kotlinx.io.Buffer
import kotlinx.io.readIntLe
import kotlinx.io.readLongLe
import kotlinx.io.readShortLe
import kotlinx.io.writeIntLe
import kotlinx.io.writeLongLe
import kotlinx.io.writeShortLe

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