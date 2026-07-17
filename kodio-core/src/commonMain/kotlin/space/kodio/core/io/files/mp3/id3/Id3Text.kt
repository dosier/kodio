package space.kodio.core.io.files.mp3.id3

internal fun readSyncsafeInt(bytes: ByteArray, offset: Int = 0): Int {
    require(bytes.size >= offset + 4)
    return ((bytes[offset].toInt() and 0x7F) shl 21) or
        ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
        ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
        (bytes[offset + 3].toInt() and 0x7F)
}

internal fun decodeId3Text(data: ByteArray): String {
    if (data.isEmpty()) return ""
    return when (val encoding = data[0].toInt() and 0xFF) {
        0 -> decodeLatin1(data, 1)
        3 -> data.decodeToString(1).trimEnd('\u0000')
        1 -> decodeId3Utf16(data, 1, hasBom = true)
        2 -> decodeId3Utf16(data, 1, hasBom = false, bigEndian = true)
        else -> decodeLatin1(data, 1)
    }
}

internal fun decodeId3CommText(data: ByteArray): String {
    if (data.isEmpty()) return ""
    val encoding = data[0].toInt() and 0xFF
    var offset = 1
    if (offset + 3 > data.size) return ""
    offset += 3 // language
    offset = skipId3EncodedTerminator(data, offset, encoding)
    if (offset >= data.size) return ""
    return when (encoding) {
        0 -> decodeLatin1(data, offset)
        3 -> data.decodeToString(offset).trimEnd('\u0000')
        1 -> decodeId3Utf16(data, offset, hasBom = true)
        2 -> decodeId3Utf16(data, offset, hasBom = false, bigEndian = true)
        else -> decodeLatin1(data, offset)
    }
}

private fun decodeLatin1(data: ByteArray, start: Int): String {
    if (start >= data.size) return ""
    var end = data.size
    for (i in start until data.size) {
        if (data[i] == 0.toByte()) {
            end = i
            break
        }
    }
    return data.decodeToString(start, end)
}

private fun skipId3EncodedTerminator(data: ByteArray, start: Int, encoding: Int): Int {
    return when (encoding) {
        1, 2 -> {
            var i = start
            while (i + 1 < data.size) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i + 2
                i++
            }
            data.size
        }
        else -> {
            var i = start
            while (i < data.size) {
                if (data[i] == 0.toByte()) return i + 1
                i++
            }
            data.size
        }
    }
}

private fun decodeId3Utf16(
    data: ByteArray,
    start: Int,
    hasBom: Boolean,
    bigEndian: Boolean = false,
): String {
    var offset = start
    var le = !bigEndian
    if (hasBom) {
        if (offset + 2 > data.size) return ""
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        le = b0 == 0xFF && b1 == 0xFE
        offset += 2
    }
    val chars = StringBuilder()
    var i = offset
    while (i + 1 < data.size) {
        val codeUnit = if (le) {
            (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
        } else {
            (data[i].toInt() shl 8) or (data[i + 1].toInt() and 0xFF)
        }
        if (codeUnit == 0) break
        chars.append(codeUnit.toChar())
        i += 2
    }
    return chars.toString()
}

internal fun parseTrackNumber(text: String): Int? {
    val token = text.substringBefore('/').trim()
    return token.toIntOrNull()
}

internal fun parseYear(text: String): Int? {
    val match = Regex("""(\d{4})""").find(text.trim()) ?: return null
    return match.groupValues[1].toIntOrNull()
}

internal fun trimId3Field(value: String): String? =
    value.trim().trimEnd('\u0000').takeIf { it.isNotEmpty() }
