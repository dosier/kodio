package space.kodio.core.io.files.mp3

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.Channels
import space.kodio.core.IntBitDepth
import space.kodio.core.SampleEncoding
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReader
import space.kodio.core.io.files.AudioFileWriteError
import space.kodio.core.io.files.metadata.AudioEncodingInfo

@OptIn(ExperimentalEncodingApi::class)
class Mp3CodecTest {

    private fun fixtureMp3(): ByteArray = Base64.decode(
        "//tQxAAAAAAAAAAAAAAAAAAAAAAASW5mbwAAAA8AAAAFAAAE5AAzMzMzMzMzMzMzMzMzMzMzMzMzZmZm" +
        "ZmZmZmZmZmZmZmZmZmZmZmaZmZmZmZmZmZmZmZmZmZmZmZmZmczMzMzMzMzMzMzMzMzMzMzMzMzM////" +
        "//////////////////////8AAAA5TEFNRTMuMTAwAaUAAAAALlEAABRAJAMGQgAAQAAABOS4zXdIAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/7UMQAAApURzw1pIARfBWt9zLQAh+NWrNSlM+VGhYA" +
        "LGqdHEjHEcGoJA4uX/LLlmy8aRax2vu+GAoCAIAgGBQgYbXRo0aNuZoEAQBB0uH+IN3L9IY4DficENQD" +
        "P5MEOAz+kEOXf3dIAAG/oGAwHAwHAwHA4ESJSNEIHHSV7klB5QJ50nGacPJX2TbC2gtvgFULcC3fhPhw" +
        "iYiM/4kwWoJ8O0Rn/x6mReLxiXf/x6mQ9h7GJdLv9QNCUJA1/xKEgaEoSPf+WKowIADXMCKA1zD/+1LE" +
        "BIMJcDsQHfEAATOFIcXf9Eh6AjUwGAHuMD5Cpjf6/Vs2SA7fMfCE/zDxArcwckJAMILB/jCNwbQwL8Cs" +
        "RGW+mDFZUMY38H9H2/f/8nnP//2///6YCMLQ1MnisNL1TONq0MUXIQzhIPx04FkhJMUwC/DnOATVVeTT" +
        "ZUjTWPTMMszCgDkAbOHbfyUW3Lw3uxdfR1/k/3+v/0f93/9drv/9fdUyLJoyYM8zRUQ1gnEwykeCNUY6" +
        "RjUix1gwuYIbNdo4ygYzaKwOGWE1oaQESv/7UsQWA8hIIxIO/4JBL4Vhwd/0SE1XWnRcK9SPT9n/r///" +
        "//+3/+j+LfsMNgqMpyjNOFEORabMU0H9Dh3/MY4RAf8MVLC6Tnh/DVBfDS9WTSiSjLotjCcEUHGduw/9" +
        "PYzspMe31t7LvHfR9n7P////0/2d1HSLKjIonTJU0jMhSzVGgDDBh681EfuHNO+HhTCxQiw1MnzLZWNs" +
        "Kc4nUDXJkARNTWdWeFDvX9H2///7v/////+n//6KWI4wAoAXMA9AkzBIwPkwPwJqMKKFBjZL//tSxCyD" +
        "CBAjEg7/gkFWhSLKvjAAINk1fUrLMXTEDzDFQXMwO8HbMGpBfzBmQX0wFkCBBwA+r9rEslgRHLzNfCfq" +
        "66dMVb//r7///l8fG///9FUAAACFGQCjOlIZCMaCsJgBCUY2ixjQnGSOAYODJgMiCbebAAyOY8ExmEzI" +
        "8mOwDGjLxWMdBBmr6o9gBPkLurYjJDDTA0CEEJRQ9W49ND8DrZ45blKhpo//9lOdVlsJwxK5PQXv//7I" +
        "h6Hs6GKxW2tb2///6EFzUbOoHlLZevb/+1LEPwATRPNH+ceAARsGIg+wkAAT5i////8+NXf3ePIlavbA" +
        "0JTvwfAgYP9QVBURAAFt2oIjSw2mL9L5Lkr9LzGUysKlReJXUvayzl3byoVBEMs0DQ4Gg7EXEp0qdUDS" +
        "galTpV0jZ/6j3BWSqPK///waprO8RSJMQU1FMy4xMDCqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq" +
        "qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqg=="
    )

    private fun fixtureMp3WithId3(): ByteArray = Base64.decode(
        "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" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAP8="
    )

    @Test
    fun matchesMagic_acceptsId3Prefix() {
        assertTrue(Mp3Codec.matchesMagic(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())))
    }

    @Test
    fun matchesMagic_acceptsValidMpegFrame() {
        val mp3 = fixtureMp3()
        assertTrue(Mp3Codec.matchesMagic(mp3.copyOf(minOf(64, mp3.size))))
    }

    @Test
    fun detect_recognizesMp3Bytes() {
        assertEquals(AudioFileFormat.Mp3, AudioFileFormat.detect(fixtureMp3()))
    }

    @Test
    fun decode_producesNonEmptyPcm() {
        val source = decodeMp3Bytes(fixtureMp3())
        assertEquals(44100, source.format.sampleRate)
        assertEquals(Channels.Mono, source.format.channels)
        val enc = source.format.encoding
        assertTrue(enc is SampleEncoding.PcmInt)
        assertEquals(IntBitDepth.Sixteen, enc.bitDepth)
        val pcm = source.source.readByteArray()
        assertTrue(pcm.size >= 2, "expected non-empty PCM, got ${pcm.size} bytes")
        assertEquals(0, pcm.size % 2)
    }

    @Test
    fun audioFileReader_decodesMp3ByExtension() {
        val recording = AudioFileReader.decodeBytes(fixtureMp3(), "tone.mp3", explicit = null)
        assertEquals(44100, recording.format.sampleRate)
        assertTrue(recording.sizeInBytes > 0)
    }

    @Test
    fun readMetadata_parsesId3AndTechnicalFields() {
        val metadata = readMp3MetadataBytes(fixtureMp3WithId3())
        assertEquals(AudioFileFormat.Mp3, metadata.container)
        assertEquals(44100, metadata.sampleRate)
        assertEquals(Channels.Mono, metadata.channels)
        val enc = metadata.encoding
        assertTrue(enc is AudioEncodingInfo.Compressed.Mp3)
        assertEquals(3, enc.layer)
        assertEquals("Fixture Title", metadata.tags.title)
        assertEquals("Fixture Artist", metadata.tags.artist)
        assertEquals("Fixture Album", metadata.tags.album)
        assertNotNull(metadata.duration)
        assertTrue(metadata.duration!!.inWholeMilliseconds > 0)
    }

    @Test
    fun encode_throwsUnsupportedFormat() {
        val source = decodeMp3Bytes(fixtureMp3())
        assertFailsWith<AudioFileWriteError.UnsupportedFormat> {
            Mp3Codec.encode(source, Buffer())
        }
    }
}
