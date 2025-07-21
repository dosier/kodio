package space.kodio.core

import js.array.JsArray
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.core.JsAny
import js.typedarrays.Float32Array
import js.typedarrays.Int16Array
import js.typedarrays.Int8Array
import js.typedarrays.toUint8Array
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import web.audio.AudioBuffer
import web.audio.AudioContext
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.permissions.PermissionDescriptor
import web.permissions.PermissionName
import web.permissions.microphone
import kotlin.math.max
import kotlin.math.min

actual fun createCodeBlobUrl(code: String): String {
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val blob =  Blob(
        blobParts = arrayOf(AUDIO_PROCESSOR_CODE),
        options = BlobPropertyBag(type = "application/javascript")
    )
    return URL.createObjectURL(blob)
}

actual fun <B : ArrayBufferLike> Float32Array<B>.encodeAs16BitPcmByteArray(): ByteArray {
    val pcm16 = Int16Array<B>(this.length)
    for (i in 0 until this.length) {
        val s = max(-1.0f, min(1.0f, this[i]))
        pcm16[i] = (s * 32767.0f).toInt().toShort()
    }
    return pcm16.buffer.toByteArray()
}

private fun ArrayBufferLike.toByteArray(): ByteArray =
    Int8Array(this).unsafeCast<ByteArray>()

actual fun createAudioContextOptions(latencyHint: AudioContextLatencyCategory, sampleRate: Int): AudioContextOptions =
    AudioContextOptions(latencyHint = latencyHint, sampleRate = sampleRate.toFloat())

actual fun createMediaStreamConstraints(audio: MediaTrackConstraints): MediaStreamConstraints =
    MediaStreamConstraints(audio = audio)

actual fun createMediaTrackConstraints(deviceId: String, sampleRate: Int, sampleSize: Int, channelCount: Int): MediaTrackConstraints =
    MediaTrackConstraints(deviceId = deviceId, sampleRate = sampleRate, sampleSize = sampleSize, channelCount = channelCount)

actual val microphonePermissionDescriptor: PermissionDescriptor =
    PermissionDescriptor(PermissionName.microphone)

actual fun AudioContext.createBufferFrom(
    format: AudioFormat,
    data: ByteArray
): AudioBuffer {
    val pcm16Data = Int16Array(data.toJsArrayBuffer())
    val pcm32Data = pcm16Data.to32FloatArray()
    val buffer = createBuffer(
        numberOfChannels = format.channels.count,
        length = pcm32Data.length,
        sampleRate = format.sampleRate.toFloat()
    )
    buffer.copyToChannel(pcm32Data, 0) // Assuming mono audio for simplicity
    return buffer
}

actual fun <T : JsAny?> JsArray<T>.toList(): List<T> {
    return this.toMutableList().toList()
}

internal fun <B : ArrayBufferLike> Int16Array<B>.to32FloatArray(): Float32Array<B> {
    val floatArray = Float32Array<B>(this.length)
    for (i in 0 until this.length) {
        floatArray[i] = this[i] / 32767.0f
    }
    return floatArray
}

// Helper extension is needed to convert a ByteArray to an ArrayBuffer
internal fun ByteArray.toJsArrayBuffer(): ArrayBuffer {
    return toUint8Array().buffer
}