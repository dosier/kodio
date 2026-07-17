package space.kodio.core.io.files.metadata

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import space.kodio.core.Channels
import space.kodio.core.IntBitDepth
import space.kodio.core.fmtInt
import space.kodio.core.io.files.AudioFileReader
import space.kodio.core.io.files.aiff.readAiffMetadata
import space.kodio.core.io.files.mp3.id3.Id3Parser
import space.kodio.core.io.files.mp3.id3.Id3v1Parser
import space.kodio.core.io.files.mp3.id3.Id3v2Parser
import space.kodio.core.io.files.wav.readWavMetadata
import space.kodio.core.io.writeExtended80BitFloat
import space.kodio.core.io.writeIntBe
import space.kodio.core.io.writeUInt16Be
import space.kodio.core.rampBytes

class AudioMetadataTest {

    @Test
    fun `readWavMetadata parses LIST INFO tags`() {
        val pcm = rampBytes(32)
        val wavBytes = buildWavWithInfo(
            pcm = pcm,
            info = mapOf(
                "INAM" to "Test Title",
                "IART" to "Test Artist",
                "IPRD" to "Test Album",
                "ICMT" to "A comment",
                "ICRD" to "2020",
                "ITRK" to "3/12",
                "IGNR" to "Rock",
                "ISFT" to "Kodio Test",
            ),
        )

        val metadata = readWavMetadata(Buffer().apply { write(wavBytes) })

        assertEquals("Test Title", metadata.tags.title)
        assertEquals("Test Artist", metadata.tags.artist)
        assertEquals("Test Album", metadata.tags.album)
        assertEquals("A comment", metadata.tags.comment)
        assertEquals(2020, metadata.tags.year)
        assertEquals(3, metadata.tags.trackNumber)
        assertEquals("Rock", metadata.tags.genre)
        assertEquals("Kodio Test", metadata.tags.raw["ISFT"])
        assertEquals(44100, metadata.sampleRate)
        assertEquals(Channels.Mono, metadata.channels)
    }

    @Test
    fun `AudioFileReader readMetadata reads WAV INFO from bytes`() {
        val pcm = rampBytes(16)
        val wavBytes = buildWavWithInfo(pcm, mapOf("INAM" to "Byte Title"))
        val metadata = AudioFileReader.readMetadata(wavBytes, "clip.wav")
        assertEquals("Byte Title", metadata.tags.title)
    }

    @Test
    fun `readAiffMetadata parses NAME AUTH ANNO text chunks`() {
        val pcm = rampBytes(32)
        val aiffBytes = buildAiffWithText(
            pcm = pcm,
            name = "AIFF Title",
            author = "AIFF Artist",
            annotation = "AIFF comment",
        )

        val metadata = readAiffMetadata(Buffer().apply { write(aiffBytes) })

        assertEquals("AIFF Title", metadata.tags.title)
        assertEquals("AIFF Artist", metadata.tags.artist)
        assertEquals("AIFF comment", metadata.tags.comment)
        assertEquals("AIFF Title", metadata.tags.raw["NAME"])
        assertEquals(44100, metadata.sampleRate)
    }

    @Test
    fun `Id3v1Parser parses fixed-width tag block`() {
        val tag = buildId3v1Tag(
            title = "Song",
            artist = "Singer",
            album = "Record",
            year = "1999",
            comment = "Nice",
            track = 7,
            genre = 1,
        )

        val tags = Id3v1Parser.parseTag(tag)
        assertNotNull(tags)
        assertEquals("Song", tags.title)
        assertEquals("Singer", tags.artist)
        assertEquals("Record", tags.album)
        assertEquals(1999, tags.year)
        assertEquals("Nice", tags.comment)
        assertEquals(7, tags.trackNumber)
        assertEquals("Classic Rock", tags.genre)
    }

    @Test
    fun `Id3v2Parser parses v2_3 text frames`() {
        val tag = buildId3v2Tag(
            major = 3,
            frames = mapOf(
                "TIT2" to latin1Frame("Track Title"),
                "TPE1" to latin1Frame("Track Artist"),
                "TALB" to latin1Frame("Track Album"),
                "TRCK" to latin1Frame("2/10"),
                "TYER" to latin1Frame("2001"),
                "TCON" to latin1Frame("Pop"),
                "COMM" to commFrame("eng", "", "Hello"),
                "TXXX" to latin1Frame("custom"),
            ),
        )

        val tags = Id3v2Parser.parseAtStart(tag)
        assertNotNull(tags)
        assertEquals("Track Title", tags.title)
        assertEquals("Track Artist", tags.artist)
        assertEquals("Track Album", tags.album)
        assertEquals(2, tags.trackNumber)
        assertEquals(2001, tags.year)
        assertEquals("Pop", tags.genre)
        assertEquals("Hello", tags.comment)
        assertEquals("custom", tags.raw["TXXX"])
    }

    @Test
    fun `Id3v2Parser parses v2_4 text frames with syncsafe sizes`() {
        val tag = buildId3v2Tag(
            major = 4,
            frames = mapOf(
                "TIT2" to latin1Frame("V4 Title"),
                "TDRC" to latin1Frame("2015-06-01"),
            ),
        )

        val tags = Id3v2Parser.parseAtStart(tag)
        assertNotNull(tags)
        assertEquals("V4 Title", tags.title)
        assertEquals(2015, tags.year)
    }

    @Test
    fun `Id3Parser prefers v2 values and fills from v1`() {
        val v2 = buildId3v2Tag(
            major = 3,
            frames = mapOf("TIT2" to latin1Frame("V2 Title")),
        )
        val v1 = buildId3v1Tag(
            title = "V1 Title",
            artist = "V1 Artist",
            album = "V1 Album",
            year = "2000",
            comment = "V1 comment",
            track = 5,
            genre = 0,
        )
        val combined = v2 + v1.copyOfRange(v1.size - 128, v1.size)

        val tags = Id3Parser.parse(combined)
        assertEquals("V2 Title", tags.title)
        assertEquals("V1 Artist", tags.artist)
        assertEquals("V1 Album", tags.album)
        assertEquals(2000, tags.year)
        assertEquals("V1 comment", tags.comment)
        assertEquals(5, tags.trackNumber)
        assertEquals("Blues", tags.genre)
    }

    @Test
    fun `Id3v1Parser returns null for non-tag data`() {
        assertNull(Id3v1Parser.parseTag(ByteArray(128) { 0 }))
    }

    private fun buildWavWithInfo(pcm: ByteArray, info: Map<String, String>): ByteArray {
        val infoBody = Buffer().apply {
            writeString("INFO")
            for ((id, value) in info) {
                val textBytes = value.encodeToByteArray() + byteArrayOf(0)
                writeString(id)
                writeIntLe(textBytes.size)
                write(textBytes)
                if (textBytes.size and 1 == 1) write(byteArrayOf(0))
            }
        }.readByteArray()

        val listChunkTotal = 8 + infoBody.size
        val fmtChunkTotal = 8 + 16
        val dataChunkTotal = 8 + pcm.size
        val riffPayload = 4 + fmtChunkTotal + listChunkTotal + dataChunkTotal

        return Buffer().apply {
            writeString("RIFF")
            writeIntLe(riffPayload)
            writeString("WAVE")
            writeString("fmt ")
            writeIntLe(16)
            writeShortLe(1)
            writeShortLe(1)
            writeIntLe(44100)
            writeIntLe(44100 * 2)
            writeShortLe(2)
            writeShortLe(16)
            writeString("LIST")
            writeIntLe(infoBody.size)
            write(infoBody)
            writeString("data")
            writeIntLe(pcm.size)
            write(pcm)
        }.readByteArray()
    }

    private fun buildAiffWithText(
        pcm: ByteArray,
        name: String,
        author: String,
        annotation: String,
    ): ByteArray {
        val format = fmtInt(44100, Channels.Mono, IntBitDepth.Sixteen)
        val numFrames = pcm.size / format.bytesPerFrame
        val ssndDataSize = 8 + pcm.size
        val ssndPad = ssndDataSize and 1

        val formBody = Buffer().apply {
            writeString("AIFF")
            writeString("COMM")
            writeIntBe(18)
            writeUInt16Be(format.channels.count)
            writeIntBe(numFrames)
            writeUInt16Be(16)
            write(writeExtended80BitFloat(format.sampleRate.toDouble()))
            write(aiffTextChunkPayload("NAME", name))
            write(aiffTextChunkPayload("AUTH", author))
            write(aiffTextChunkPayload("ANNO", annotation))
            writeString("SSND")
            writeIntBe(ssndDataSize)
            writeIntBe(0)
            writeIntBe(0)
            write(pcm.swapForAiff())
            if (ssndPad == 1) write(byteArrayOf(0))
        }.readByteArray()

        return Buffer().apply {
            writeString("FORM")
            writeIntBe(formBody.size)
            write(formBody)
        }.readByteArray()
    }

    private fun aiffTextChunkPayload(id: String, text: String): ByteArray {
        val payload = byteArrayOf(text.length.toByte()) + text.encodeToByteArray()
        return Buffer().apply {
            writeString(id)
            writeIntBe(payload.size)
            write(payload)
            if (payload.size and 1 == 1) write(byteArrayOf(0))
        }.readByteArray()
    }

    private fun ByteArray.swapForAiff(): ByteArray {
        val out = copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val tmp = out[i]
            out[i] = out[i + 1]
            out[i + 1] = tmp
            i += 2
        }
        return out
    }

    private fun buildId3v1Tag(
        title: String,
        artist: String,
        album: String,
        year: String,
        comment: String,
        track: Int,
        genre: Int,
    ): ByteArray {
        val tag = ByteArray(128)
        tag[0] = 'T'.code.toByte()
        tag[1] = 'A'.code.toByte()
        tag[2] = 'G'.code.toByte()
        copyFixed(tag, 3, title, 30)
        copyFixed(tag, 33, artist, 30)
        copyFixed(tag, 63, album, 30)
        copyFixed(tag, 93, year, 4)
        copyFixed(tag, 97, comment, 28)
        tag[125] = track.toByte()
        tag[127] = genre.toByte()
        return tag
    }

    private fun copyFixed(dest: ByteArray, offset: Int, value: String, length: Int) {
        val bytes = value.encodeToByteArray()
        val count = minOf(bytes.size, length)
        bytes.copyInto(dest, offset, 0, count)
    }

    private fun latin1Frame(text: String): ByteArray =
        byteArrayOf(0) + text.encodeToByteArray()

    private fun commFrame(language: String, description: String, text: String): ByteArray {
        return byteArrayOf(0) +
            language.encodeToByteArray() +
            description.encodeToByteArray() + byteArrayOf(0) +
            text.encodeToByteArray()
    }

    private fun buildId3v2Tag(major: Int, frames: Map<String, ByteArray>): ByteArray {
        val body = Buffer()
        for ((id, data) in frames) {
            body.writeString(id)
            if (major == 4) {
                body.write(syncsafe(data.size))
            } else {
                body.writeIntBe(data.size)
            }
            body.writeShortLe(0)
            body.write(data)
        }
        val bodyBytes = body.readByteArray()
        val header = Buffer().apply {
            writeString("ID3")
            writeByte(major.toByte())
            writeByte(0)
            writeByte(0)
            write(syncsafe(bodyBytes.size))
        }.readByteArray()
        return header + bodyBytes
    }

    private fun syncsafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )
}
