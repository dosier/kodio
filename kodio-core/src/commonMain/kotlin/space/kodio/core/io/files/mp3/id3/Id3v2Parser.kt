package space.kodio.core.io.files.mp3.id3

import kotlinx.io.Buffer
import kotlinx.io.Source
import space.kodio.core.io.files.metadata.AudioTags
import space.kodio.core.io.readExactly
import space.kodio.core.io.readIntBe

/**
 * Parses ID3v2.3 and ID3v2.4 tags from the start of an MP3 file (cold path; kotlinx-io).
 */
internal object Id3v2Parser {
    fun parseAtStart(bytes: ByteArray): AudioTags? {
        if (bytes.size < ID3V2_HEADER_SIZE) return null
        val buffer = Buffer().apply { write(bytes) }
        return parse(buffer)
    }

    fun parse(source: Source): AudioTags? {
        val header = try {
            source.readExactly(ID3V2_HEADER_SIZE)
        } catch (_: Exception) {
            return null
        }

        if (header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return null
        }

        val major = header[3].toInt() and 0xFF
        if (major != 3 && major != 4) return null

        val flags = header[5].toInt() and 0xFF
        val tagSize = readSyncsafeInt(header, 6)
        if (tagSize <= 0) return AudioTags.Empty

        var remaining = tagSize.toLong()
        if (flags and 0x40 != 0) {
            if (remaining < 4) return null
            val extSize = source.readIntBe()
            remaining -= 4
            val extDataSize = extSize.toLong() and 0xFFFFFFFFL
            if (extDataSize > remaining) return null
            source.skip(extDataSize)
            remaining -= extDataSize
        }

        val raw = mutableMapOf<String, String>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber: Int? = null
        var year: Int? = null
        var genre: String? = null
        var comment: String? = null

        while (remaining >= 10) {
            val frameHeader = try {
                source.readExactly(10)
            } catch (_: Exception) {
                break
            }
            remaining -= 10

            val frameId = frameHeader.decodeToString(0, 4)
            if (frameId.all { it == '\u0000' }) break

            val frameSize = if (major == 4) {
                readSyncsafeInt(frameHeader, 4)
            } else {
                ((frameHeader[4].toInt() and 0xFF) shl 24) or
                    ((frameHeader[5].toInt() and 0xFF) shl 16) or
                    ((frameHeader[6].toInt() and 0xFF) shl 8) or
                    (frameHeader[7].toInt() and 0xFF)
            }

            if (frameSize <= 0 || frameSize.toLong() > remaining) break

            val frameData = try {
                source.readExactly(frameSize)
            } catch (_: Exception) {
                break
            }
            remaining -= frameSize.toLong()

            val text = when (frameId) {
                "COMM" -> decodeId3CommText(frameData)
                else -> if (frameId.startsWith('T')) decodeId3Text(frameData) else null
            }?.let(::trimId3Field)

            if (text != null) {
                raw[frameId] = text
            }

            when (frameId) {
                "TIT2" -> if (title == null) title = text
                "TPE1" -> if (artist == null) artist = text
                "TALB" -> if (album == null) album = text
                "TRCK" -> if (trackNumber == null && text != null) trackNumber = parseTrackNumber(text)
                "TYER", "TDRC" -> if (year == null && text != null) year = parseYear(text)
                "TCON" -> if (genre == null) genre = text
                "COMM" -> if (comment == null) comment = text
            }
        }

        if (raw.isEmpty() &&
            title == null &&
            artist == null &&
            album == null &&
            trackNumber == null &&
            year == null &&
            genre == null &&
            comment == null
        ) {
            return null
        }

        return AudioTags(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            year = year,
            genre = genre,
            comment = comment,
            raw = raw,
        )
    }
}
