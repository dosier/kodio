package space.kodio.core.io.files.aiff

import space.kodio.core.io.files.metadata.AudioTags

internal fun parseAiffTextChunk(data: ByteArray): String {
    if (data.isEmpty()) return ""
    val length = data[0].toInt() and 0xFF
    if (length == 0) return ""
    val end = minOf(1 + length, data.size)
    return data.decodeToString(1, end).trimEnd('\u0000')
}

internal fun aiffTextToTags(chunks: Map<String, String>): AudioTags {
    if (chunks.isEmpty()) return AudioTags.Empty
    return AudioTags(
        title = chunks["NAME"],
        artist = chunks["AUTH"],
        comment = chunks["ANNO"],
        raw = chunks,
    )
}
