package space.kodio.core

import js.array.JsArray
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.core.JsPrimitives.toFloat
import js.typedarrays.Float32Array
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaStreamTrack
import web.mediastreams.MediaTrackConstraints
import kotlin.math.max
import kotlin.math.min
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

actual fun AudioFlow.transformToPcm32(): Flow<Float32Array<ArrayBuffer>> {
    return map { it.encodeAsPcm32() }
}

actual fun createCodeBlobUrl(code: String): String {
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val blob = Blob(
        blobParts = arrayOf(AUDIO_PROCESSOR_CODE.toJsString() as JsAny?).toJsArray(),
        options = BlobPropertyBag(type = "application/javascript")
    )
    return URL.createObjectURL(blob)
}

actual fun JsArray<out MediaStreamTrack>.first(): MediaStreamTrack? =
    get(0)

actual fun JsArray<out MediaStreamTrack>.forEach(action: (MediaStreamTrack) -> Unit) {
    for (i in 0 until length) {
        val track = get(i)!!
        action(track)
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
actual fun <B : ArrayBufferLike> Float32Array<B>.to16BitPcmByteArray(): ByteArray {
    val buffer = Buffer()
    for (i in 0 until this.length) {
        val s = max(-1.0f, min(1.0f, get(i).toFloat()))
        val value = (s * 32767.0f).toInt().toShort()
        buffer.writeShort(value)
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
private fun ByteArray.encodeAsPcm32(): Float32Array<ArrayBuffer> {
    return withScopedMemoryAllocator {
        val outBuffer = it.allocate(size * 4)
        for (i in 0 until this.size step 2) {
            val value = getShortLE(i) / 32767.0f
            outBuffer.storeInt(value.toBits())
        }
        importArrayFromMem(outBuffer.address.toInt(), size * 4)
    }
}

private fun ByteArray.getShort(index: Int): Short =
    ((this[index].toInt() and 0xFF) or (this[index + 1].toInt() shl 8)).toShort()

private fun ByteArray.getShortLE(index: Int): Short =
    ((this[index].toInt() shl 8) or (this[index + 1].toInt() and 0xFF)).toShort()

@Suppress("unused")
private fun importArrayFromMem(address: Int, length: Int): Float32Array<ArrayBuffer> =
    js("new Float32Array(wasmExports.memory.buffer, address, length)")