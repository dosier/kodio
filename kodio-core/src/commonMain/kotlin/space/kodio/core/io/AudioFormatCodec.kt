package space.kodio.core.io

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioFormat
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.FloatPrecision
import space.kodio.core.IntBitDepth
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout

private const val PCM_FLOAT_ENCODING_FLAG = 1.toByte()
private const val PCM_INT_ENCODING_FLAG = 2.toByte()

fun AudioFormat.encodeToByteArray(): ByteArray {
    val buffer = Buffer()
    buffer.writeAudioFormat(this)
    return buffer.readByteArray()
}

fun ByteArray.decodeAsAudioFormat(): AudioFormat {
    val buffer = Buffer()
    buffer.write(this)
    return buffer.readAudioFormat()
}

fun Buffer.writeAudioFormat(audioFormat: AudioFormat) {
    writeInt(audioFormat.sampleRate)
    writeByte(audioFormat.channels.count.toByte())
    when(val encoding = audioFormat.encoding) {
        is SampleEncoding.PcmFloat -> {
            writeByte(PCM_FLOAT_ENCODING_FLAG)
            writeByte(encoding.precision.ordinal.toByte())
            writeByte(encoding.layout.ordinal.toByte())
        }
        is SampleEncoding.PcmInt -> {
            writeByte(PCM_INT_ENCODING_FLAG)
            writeByte(encoding.bitDepth.ordinal.toByte())
            writeByte(encoding.endianness.ordinal.toByte())
            writeByte(encoding.layout.ordinal.toByte())
            writeByte(if (encoding.signed) 1.toByte() else 0.toByte())
            writeByte(if (encoding.packed) 1.toByte() else 0.toByte())
        }
    }
}

fun Buffer.readAudioFormat(): AudioFormat {
    val sampleRate = readInt()
    val channels = readByte().let { Channels.fromInt(it.toInt()) }
    val encoding = when (val encodingFlag = readByte()) {
        PCM_FLOAT_ENCODING_FLAG -> {
            val precision = readEnum<FloatPrecision>()
            val layout = readEnum<SampleLayout>()
            SampleEncoding.PcmFloat(precision, layout)
        }
        PCM_INT_ENCODING_FLAG -> {
            val bitDepth = readEnum<IntBitDepth>()
            val endianness = readEnum<Endianness>()
            val layout = readEnum<SampleLayout>()
            val signed = readByte() == 1.toByte()
            val packed = readByte() == 1.toByte()
            SampleEncoding.PcmInt(bitDepth, endianness, layout, signed, packed)
        }
        else -> error("Unsupported encoding flag: $encodingFlag")
    }
    return AudioFormat(sampleRate, channels, encoding)
}

private inline fun<reified E : Enum<E>> Buffer.readEnum(): E {
    val ordinal = readByte()
    return enumValues<E>()[ordinal.toInt()]
}