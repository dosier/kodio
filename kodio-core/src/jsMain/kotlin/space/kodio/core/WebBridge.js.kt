package space.kodio.core

import js.array.JsArray
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import js.typedarrays.Int16Array
import js.typedarrays.Int8Array
import kotlinx.coroutines.flow.Flow
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaStreamTrack
import web.mediastreams.MediaTrackConstraints
import kotlin.math.max
import kotlin.math.min

actual fun AudioFlow.transformToPcm32(): Flow<Float32Array<ArrayBuffer>> {
    TODO("Not yet implemented")
}

actual fun createCodeBlobUrl(code: String): String {
    TODO("Not yet implemented")
}

actual fun JsArray<out MediaStreamTrack>.first(): MediaStreamTrack? {
    TODO("Not yet implemented")
}

actual fun JsArray<out MediaStreamTrack>.forEach(action: (MediaStreamTrack) -> Unit) {
}

actual fun <B : ArrayBufferLike> Float32Array<B>.to16BitPcmByteArray(): ByteArray {
    val pcm16 = Int16Array<B>(this.length)
    for (i in 0 until this.length) {
        val s = max(-1.0f, min(1.0f, this[i]))
        pcm16[i] = (s * 32767.0f).toInt().toShort()
    }
    return pcm16.buffer.toByteArray()
}

private fun ArrayBufferLike.toByteArray(): ByteArray =
    Int8Array(this).unsafeCast<ByteArray>()

actual fun createAudioContextOptions(latencyHint: AudioContextLatencyCategory, sampleRate: Int): AudioContextOptions {
    TODO("Not yet implemented")
}

actual fun createMediaStreamConstraints(audio: MediaTrackConstraints): MediaStreamConstraints {
    TODO("Not yet implemented")
}

actual fun createMediaTrackConstraints(deviceId: String, sampleRate: Int, sampleSize: Int, channelCount: Int): MediaTrackConstraints {
    TODO("Not yet implemented")
}