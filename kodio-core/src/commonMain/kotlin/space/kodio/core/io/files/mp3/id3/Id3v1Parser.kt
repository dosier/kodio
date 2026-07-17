package space.kodio.core.io.files.mp3.id3

import space.kodio.core.io.files.metadata.AudioTags

private val ID3V1_GENRES = listOf(
    "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop",
    "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae",
    "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks",
    "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion",
    "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip",
    "Gospel", "Noise", "Alt Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
    "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
    "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock",
    "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
    "Native American", "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes",
    "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro",
    "Musical", "Rock & Roll", "Hard Rock",
)

/**
 * Parses a classic ID3v1 tag from the tail of an audio file.
 */
internal object Id3v1Parser {
    fun parseAtEnd(bytes: ByteArray): AudioTags? {
        if (bytes.size < ID3V1_TAG_SIZE) return null
        val offset = bytes.size - ID3V1_TAG_SIZE
        return parseTag(bytes, offset)
    }

    fun parseTag(bytes: ByteArray, offset: Int = 0): AudioTags? {
        if (offset + ID3V1_TAG_SIZE > bytes.size) return null
        if (bytes[offset] != 'T'.code.toByte() ||
            bytes[offset + 1] != 'A'.code.toByte() ||
            bytes[offset + 2] != 'G'.code.toByte()
        ) {
            return null
        }

        val title = trimId3Field(readFixedField(bytes, offset + 3, 30))
        val artist = trimId3Field(readFixedField(bytes, offset + 33, 30))
        val album = trimId3Field(readFixedField(bytes, offset + 63, 30))
        val year = trimId3Field(readFixedField(bytes, offset + 93, 4))?.let(::parseYear)
        val commentBytes = bytes.copyOfRange(offset + 97, offset + 127)
        val trackNumber = if (commentBytes[28] != 0.toByte() && commentBytes[29] == 0.toByte()) {
            (commentBytes[28].toInt() and 0xFF).takeIf { it != 0 }
        } else {
            null
        }
        val commentEnd = if (trackNumber != null) 28 else 30
        val comment = trimId3Field(readFixedField(bytes, offset + 97, commentEnd))
        val genreCode = bytes[offset + 127].toInt() and 0xFF
        val genre = if (genreCode < ID3V1_GENRES.size) ID3V1_GENRES[genreCode] else genreCode.toString()

        val raw = buildMap {
            title?.let { put("TITLE", it) }
            artist?.let { put("ARTIST", it) }
            album?.let { put("ALBUM", it) }
            year?.let { put("YEAR", it.toString()) }
            comment?.let { put("COMMENT", it) }
            trackNumber?.let { put("TRACK", it.toString()) }
            if (genreCode != 255) put("GENRE", genre)
        }

        if (raw.isEmpty()) return null

        return AudioTags(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            year = year,
            genre = if (genreCode != 255) genre else null,
            comment = comment,
            raw = raw,
        )
    }

    private fun readFixedField(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.decodeToString(offset, offset + length)
}
