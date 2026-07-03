package space.kodio.core

import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import space.kodio.core.io.readInt
import space.kodio.core.io.readLong
import space.kodio.core.io.readShort
import space.kodio.core.io.readUtf8
import space.kodio.core.io.writeInt
import space.kodio.core.io.writeLong
import space.kodio.core.io.writeShort
import space.kodio.core.io.writeUtf8

internal class BufferExtTest {

    @Test
    fun `writeUtf8 and readUtf8 round-trip`() {
        val strings = listOf("", "hello", "café ☕")
        for (original in strings) {
            val buffer = Buffer()
            buffer.writeUtf8(original)
            assertEquals(original, buffer.readUtf8())
        }
    }

    @Test
    fun `writeInt and readInt round-trip Little endian`() {
        roundTripInts(Endianness.Little, listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE))
    }

    @Test
    fun `writeInt and readInt round-trip Big endian`() {
        roundTripInts(Endianness.Big, listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE))
    }

    @Test
    fun `writeShort and readShort round-trip Little endian`() {
        roundTripShorts(Endianness.Little, listOf(0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE))
    }

    @Test
    fun `writeShort and readShort round-trip Big endian`() {
        roundTripShorts(Endianness.Big, listOf(0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE))
    }

    @Test
    fun `writeLong and readLong round-trip Little endian`() {
        roundTripLongs(Endianness.Little, listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE))
    }

    @Test
    fun `writeLong and readLong round-trip Big endian`() {
        roundTripLongs(Endianness.Big, listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE))
    }

    private fun roundTripInts(endianness: Endianness, values: List<Int>) {
        val buffer = Buffer()
        for (value in values) {
            buffer.writeInt(endianness, value)
        }
        for (expected in values) {
            assertEquals(expected, buffer.readInt(endianness))
        }
    }

    private fun roundTripShorts(endianness: Endianness, values: List<Short>) {
        val buffer = Buffer()
        for (value in values) {
            buffer.writeShort(endianness, value)
        }
        for (expected in values) {
            assertEquals(expected, buffer.readShort(endianness))
        }
    }

    private fun roundTripLongs(endianness: Endianness, values: List<Long>) {
        val buffer = Buffer()
        for (value in values) {
            buffer.writeLong(endianness, value)
        }
        for (expected in values) {
            assertEquals(expected, buffer.readLong(endianness))
        }
    }
}
