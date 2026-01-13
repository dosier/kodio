@file:OptIn(ExperimentalWasmJsInterop::class)

package space.kodio.core

import js.array.JsArray
import kotlin.js.ExperimentalWasmJsInterop
import js.buffer.ArrayBufferLike
import js.core.JsAny
import js.typedarrays.Float32Array
import web.audio.AudioBuffer
import web.audio.AudioContext
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.permissions.PermissionDescriptor

expect val microphonePermissionDescriptor: PermissionDescriptor

expect fun AudioContext.createBufferFrom(format: AudioFormat, data: ByteArray): AudioBuffer

expect fun<T : JsAny?> JsArray<T>.toList(): List<T>

expect fun createCodeBlobUrl(code: String): String
expect fun<B : ArrayBufferLike> Float32Array<B>.encodeAs16BitPcmByteArray(): ByteArray

expect fun createAudioContextOptions(latencyHint: AudioContextLatencyCategory, sampleRate: Int): AudioContextOptions
expect fun createMediaStreamConstraints(audio: MediaTrackConstraints): MediaStreamConstraints
expect fun createMediaTrackConstraints(deviceId: String?, sampleRate: Int, sampleSize: Int, channelCount: Int): MediaTrackConstraints