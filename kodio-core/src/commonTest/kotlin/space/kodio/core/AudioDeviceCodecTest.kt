package space.kodio.core

import kotlinx.io.Buffer
import space.kodio.core.io.readAudioDevice
import space.kodio.core.io.writeAudioDevice
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AudioDeviceCodecTest {

    @Test
    fun testAudioDeviceCodec() {
        val buffer = Buffer()
        val expectedId = "this_is_a_id"
        val expectedName = "this_is_a_name"
        sequence {
            listOf(48000).forEach { sampleRate ->
                Channels.entries.forEach { channels ->
                    (generatePcmIntEncodings() + generatePcmFloatEncodings()).forEach {
                        yield(AudioFormat(sampleRate, channels, it))
                    }
                }
            }
        }.forEach { format ->
            val expectedDevice = AudioDevice.Input(
                id = expectedId,
                name = expectedName,
                formatSupport = AudioFormatSupport.Known(
                    defaultFormat = format,
                    supportedFormats = listOf(format)
                )
            )
            buffer.writeAudioDevice(expectedDevice)
            val actualDevice = buffer.readAudioDevice()
            assertEquals(
                expected = expectedDevice,
                actual = actualDevice
            )
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