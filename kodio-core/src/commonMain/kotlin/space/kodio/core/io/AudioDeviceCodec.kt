package space.kodio.core.io

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioDevice

private const val INPUT_AUDIO_DEVICE_FLAG = 1.toByte()
private const val OUTPUT_AUDIO_DEVICE_FLAG = 2.toByte()

fun AudioDevice.encodeAsByteArray(): ByteArray {
    val buffer = Buffer()
    buffer.writeAudioDevice(this)
    return buffer.readByteArray()
}

fun ByteArray.decodeAsAudioDevice(): AudioDevice {
    val buffer = Buffer()
    buffer.write(this)
    return buffer.readAudioDevice()
}

fun Buffer.writeAudioDevice(audioDevice: AudioDevice) {
    when(audioDevice) {
        is AudioDevice.Input -> writeByte(INPUT_AUDIO_DEVICE_FLAG)
        is AudioDevice.Output -> writeByte(OUTPUT_AUDIO_DEVICE_FLAG)
    }
    writeUtf8(audioDevice.id)
    writeUtf8(audioDevice.name)
    writeAudioFormatSupport(audioDevice.formatSupport)
}

fun Buffer.readAudioDevice(): AudioDevice {
    val flag = readByte()
    val id = readUtf8()
    val name = readUtf8()
    val formatSupport = readAudioFormatSupport()
    return when(flag) {
        INPUT_AUDIO_DEVICE_FLAG -> AudioDevice.Input(id, name, formatSupport)
        OUTPUT_AUDIO_DEVICE_FLAG -> AudioDevice.Output(id, name, formatSupport)
        else -> error("Invalid flag: $flag")
    }
}