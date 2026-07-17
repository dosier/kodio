package space.kodio.core.io.files.wav

import space.kodio.core.io.files.metadata.AudioTags
import space.kodio.core.io.files.mp3.id3.parseTrackNumber
import space.kodio.core.io.files.mp3.id3.parseYear
import space.kodio.core.io.files.mp3.id3.trimId3Field

internal fun parseWavListInfo(listData: ByteArray): AudioTags {
    if (listData.size < 4 || listData.decodeToString(0, 4) != "INFO") {
        return AudioTags.Empty
    }

    val raw = mutableMapOf<String, String>()
    var offset = 4
    while (offset + 8 <= listData.size) {
        val id = listData.decodeToString(offset, offset + 4)
        val size = readIntLe(listData, offset + 4)
        offset += 8
        if (size < 0 || offset + size > listData.size) break

        val text = trimId3Field(wavInfoText(listData, offset, size))
        if (text != null) {
            raw[id] = text
        }
        offset += size
        if (size and 1 == 1) offset++
    }

    return wavInfoToTags(raw)
}

private fun wavInfoText(data: ByteArray, offset: Int, size: Int): String {
    if (size <= 0) return ""
    val end = offset + size
    var textEnd = end
    for (i in offset until end) {
        if (data[i] == 0.toByte()) {
            textEnd = i
            break
        }
    }
    return data.decodeToString(offset, textEnd)
}

private fun wavInfoToTags(raw: Map<String, String>): AudioTags {
    if (raw.isEmpty()) return AudioTags.Empty
    return AudioTags(
        title = raw["INAM"],
        artist = raw["IART"],
        album = raw["IPRD"],
        trackNumber = raw["ITRK"]?.let(::parseTrackNumber),
        year = raw["ICRD"]?.let(::parseYear),
        genre = raw["IGNR"],
        comment = raw["ICMT"],
        raw = raw,
    )
}

private fun readIntLe(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)
