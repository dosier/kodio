@file:OptIn(ExperimentalWasmJsInterop::class)

package space.kodio.core

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.toJsString
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeShortLe
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.audio.AudioWorkletNode
import web.audio.AudioWorkletProcessorName
import web.audio.BaseAudioContext
import web.blob.Blob
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.permissions.PermissionDescriptor
import web.url.URL
import kotlin.math.max
import kotlin.math.min

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

actual fun <B : ArrayBufferLike> Float32Array<B>.encodeAs16BitPcmByteArray(): ByteArray {
    val buffer = Buffer()
    for (i in 0 until this.length) {
        val s = max(-1.0f, min(1.0f, get(i).toDouble().toFloat()))
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

actual fun createMediaTrackConstraints(deviceId: String?, sampleRate: Int, sampleSize: Int, channelCount: Int): MediaTrackConstraints =
    newMediaTrackConstraints(deviceId?.toJsString(), sampleRate.toJsNumber(), sampleSize.toJsNumber(), channelCount.toJsNumber())
@Suppress("unused")
private fun newMediaTrackConstraints(deviceId: JsAny?, sampleRate: JsAny?, sampleSize: JsAny?, channelCount: JsAny?): MediaTrackConstraints {
    js("return { deviceId, sampleRate, sampleSize, channelCount };")
}
actual val microphonePermissionDescriptor: PermissionDescriptor =
    js("({name: 'microphone'})")

internal actual fun FloatArray.toJsFloat32Array(): Float32Array<ArrayBuffer> {
    val out = newJsFloat32Array(size)
    for (i in 0 until size) {
        out[i] = this[i].toDouble().toJsNumber()
    }
    return out
}

@Suppress("unused")
private fun newJsFloat32Array(length: Int): Float32Array<ArrayBuffer> =
    js("new Float32Array(length)")

actual fun <T : JsAny?> JsArray<T>.toList(): List<T> =
    this.toArray().toList()

actual fun createAudioWorkletNode(context: BaseAudioContext, name: String): AudioWorkletNode =
    AudioWorkletNode(context, name.toJsString().unsafeCast<AudioWorkletProcessorName>())