package space.kodio.core

import js.array.JsArray
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.core.JsPrimitives.toFloat
import js.core.JsPrimitives.toJsString
import js.typedarrays.Float32Array
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeShortLe
import web.audio.AudioBuffer
import web.audio.AudioContext
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.blob.Blob
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.permissions.PermissionDescriptor
import web.url.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

actual fun createCodeBlobUrl(code: String): String {
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val blob = Blob(
        blobParts = arrayOf(AUDIO_PROCESSOR_CODE.toJsString()).toJsArray(),
        options = BlobPropertyBag(type = "application/javascript")
    )
    return URL.createObjectURL(blob)
}

@Suppress("unused")
private fun BlobPropertyBag(type: String): web.blob.BlobPropertyBag {
    js("return { type };")
}

@OptIn(UnsafeWasmMemoryApi::class)
actual fun <B : ArrayBufferLike> Float32Array<B>.encodeAs16BitPcmByteArray(): ByteArray {
    val buffer = Buffer()
    for (i in 0 until this.length) {
        val s = max(-1.0f, min(1.0f, get(i).toFloat()))
        val value = (s * 32767.0f).toInt().toShort()
        buffer.writeShortLe(value)
    }
    return buffer.readByteArray()
}

actual fun createAudioContextOptions(latencyHint: AudioContextLatencyCategory, sampleRate: Int): AudioContextOptions {
    js("return { latencyHint, sampleRate };")
}

actual fun createMediaStreamConstraints(audio: MediaTrackConstraints): MediaStreamConstraints {
    js("return { audio };")
}

actual fun createMediaTrackConstraints(deviceId: String, sampleRate: Int, sampleSize: Int, channelCount: Int): MediaTrackConstraints =
    newMediaTrackConstraints(deviceId.toJsString(), sampleRate.toJsNumber(), sampleSize.toJsNumber(), channelCount.toJsNumber())
@Suppress("unused")
private fun newMediaTrackConstraints(deviceId: JsAny?, sampleRate: JsAny?, sampleSize: JsAny?, channelCount: JsAny?): MediaTrackConstraints {
    js("return { deviceId, sampleRate, sampleSize, channelCount };")
}
@OptIn(UnsafeWasmMemoryApi::class)
internal fun ByteArray.encodeAsPcm32(): Float32Array<ArrayBuffer> {

    val allocatedMemorySize = size * 2 // we need to convert our 16 bit samples to 32 bit floats
    val elementCount = size / 2
    return withScopedMemoryAllocator {
        val pointer = it.allocate(allocatedMemorySize)
        var offset = 0
        for (i in 0 until this.size step 2) {
            val value = getShort(i).toFloat() / 32767f
            pointer.plus(offset).storeInt(value.toBits())
            offset += 4
        }
        Float32Array(pointer.address.toInt(), elementCount)
    }
}

private fun ByteArray.getShort(index: Int): Short =
    ((this[index].toInt() and 0xFF) or (this[index + 1].toInt() shl 8)).toShort()

@Suppress("unused")
private fun Float32Array(address: Int, length: Int): js.typedarrays.Float32Array<ArrayBuffer> =
    js("new Float32Array(wasmExports.memory.buffer, address, length)")

actual val microphonePermissionDescriptor: PermissionDescriptor =
    js("({name: 'microphone'})")

actual fun AudioContext.createBufferFrom(
    format: AudioFormat,
    data: ByteArray
): AudioBuffer {
    val pcm32Data = data.encodeAsPcm32()
    val buffer = createBuffer(
        numberOfChannels = format.channels.count,
        length = pcm32Data.length,
        sampleRate = format.sampleRate.toFloat()
    )
    buffer.copyToChannel(pcm32Data, 0) // Assuming mono audio for simplicity
    return buffer
}

actual fun <T : js.core.JsAny?> JsArray<T>.toList(): List<T> =
    this.toArray().toList()