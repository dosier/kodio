package space.kodio.core.io.files.mp3.id3

import space.kodio.core.io.files.metadata.AudioTags

/**
 * Combines ID3v2 (header) and ID3v1 (footer) tag parsing for MP3 files.
 * ID3v2 values take precedence; ID3v1 fills in missing fields.
 */
internal object Id3Parser {
    fun parse(bytes: ByteArray): AudioTags {
        val v2 = Id3v2Parser.parseAtStart(bytes)
        val v1 = Id3v1Parser.parseAtEnd(bytes)
        return mergeTags(v2, v1)
    }

    internal fun mergeTags(primary: AudioTags?, fallback: AudioTags?): AudioTags {
        if (primary == null && fallback == null) return AudioTags.Empty
        if (primary == null) return fallback ?: AudioTags.Empty
        if (fallback == null) return primary

        val raw = fallback.raw + primary.raw
        return AudioTags(
            title = primary.title ?: fallback.title,
            artist = primary.artist ?: fallback.artist,
            album = primary.album ?: fallback.album,
            trackNumber = primary.trackNumber ?: fallback.trackNumber,
            year = primary.year ?: fallback.year,
            genre = primary.genre ?: fallback.genre,
            comment = primary.comment ?: fallback.comment,
            raw = raw,
        )
    }
}
