package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioFormat
import space.kodio.core.io.AudioSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared encode/decode round-trip helper for PCM container codecs.
 */
internal fun assertContainerRoundTrip(
    format: AudioFormat,
    pcmData: ByteArray,
    encode: (AudioSource, Buffer) -> Unit,
    decode: (Buffer) -> AudioSource,
) {
    val encoded = Buffer().also { encode(AudioSource.of(format, *pcmData), it) }.readByteArray()
    val decoded = decode(Buffer().apply { write(encoded) })

    assertEquals(format.sampleRate, decoded.format.sampleRate, "sampleRate mismatch")
    assertEquals(format.channels, decoded.format.channels, "channels mismatch")
    assertEquals(format.encoding, decoded.format.encoding, "encoding mismatch")

    val payload = decoded.source.readByteArray()
    assertTrue(
        pcmData.contentEquals(payload),
        "Payload mismatch: expected ${pcmData.size} bytes, got ${payload.size} bytes",
    )
}
