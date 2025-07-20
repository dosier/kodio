package space.kodio.core

import js.array.JsArray
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import kotlinx.coroutines.flow.Flow
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaStreamTrack
import web.mediastreams.MediaTrackConstraints

expect fun AudioFlow.transformToPcm32(): Flow<Float32Array<ArrayBuffer>>
expect fun createCodeBlobUrl(code: String): String
expect fun JsArray<out MediaStreamTrack>.first(): MediaStreamTrack?
expect fun JsArray<out MediaStreamTrack>.forEach(action: (MediaStreamTrack) -> Unit)
expect fun<B : ArrayBufferLike> Float32Array<B>.to16BitPcmByteArray(): ByteArray

expect fun createAudioContextOptions(latencyHint: AudioContextLatencyCategory, sampleRate: Int): AudioContextOptions
expect fun createMediaStreamConstraints(audio: MediaTrackConstraints): MediaStreamConstraints
expect fun createMediaTrackConstraints(deviceId: String, sampleRate: Int, sampleSize: Int, channelCount: Int): MediaTrackConstraints