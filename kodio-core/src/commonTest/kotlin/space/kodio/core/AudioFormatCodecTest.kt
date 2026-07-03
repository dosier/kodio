package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import space.kodio.core.io.decodeAsAudioFormat
import space.kodio.core.io.encodeToByteArray

internal class AudioFormatCodecTest {

    @Test
    fun testAudioFormatCodec() {
        val sampleRates = listOf(8000, 44100, 48000)
        val channels = listOf(Channels.Mono, Channels.Stereo)

        for (sampleRate in sampleRates) {
            for (channel in channels) {
                for (encoding in generatePcmIntEncodings() + generatePcmFloatEncodings()) {
                    val format = AudioFormat(sampleRate, channel, encoding)
                    val roundTripped = format.encodeToByteArray().decodeAsAudioFormat()
                    assertEquals(format, roundTripped)
                }
            }
        }
    }

    private fun generatePcmIntEncodings() =
        sequence {
            IntBitDepth.entries.forEach { bitDepth ->
                Endianness.entries.forEach { endianness ->
                    SampleLayout.entries.forEach { sampleLayout ->
                        yield(SampleEncoding.PcmInt(bitDepth, endianness, sampleLayout, signed = true, packed = true))
                        yield(SampleEncoding.PcmInt(bitDepth, endianness, sampleLayout, signed = true, packed = false))
                        yield(SampleEncoding.PcmInt(bitDepth, endianness, sampleLayout, signed = false, packed = true))
                        yield(SampleEncoding.PcmInt(bitDepth, endianness, sampleLayout, signed = false, packed = false))
                    }
                }
            }
        }

    private fun generatePcmFloatEncodings() =
        sequence {
            FloatPrecision.entries.forEach { precision ->
                SampleLayout.entries.forEach { sampleLayout ->
                    yield(SampleEncoding.PcmFloat(precision, sampleLayout))
                }
            }
        }
}
