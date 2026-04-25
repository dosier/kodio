@file:OptIn(ExperimentalWasmJsInterop::class)

package space.kodio.core

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.typedarrays.Float32Array
import web.audio.AudioContext
import web.audio.AudioContextLatencyCategory
import web.audio.AudioContextOptions
import web.audio.AudioWorkletNode
import web.audio.BaseAudioContext
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.permissions.PermissionDescriptor

expect val microphonePermissionDescriptor: PermissionDescriptor

/**
 * Wraps a Kotlin [FloatArray] in a JS [Float32Array] view backed by an
 * [ArrayBuffer] - i.e. allocated in the JS heap, not in wasm linear memory.
 *
 * This is the only target-specific piece of the playback path; everything
 * else (PCM decoding, deinterleave, AudioBuffer construction) is shared in
 * `webMain`. See [createBufferFrom] for usage.
 */
internal expect fun FloatArray.toJsFloat32Array(): Float32Array<ArrayBuffer>

expect fun <T : JsAny?> JsArray<T>.toList(): List<T>

expect fun createCodeBlobUrl(code: String): String
expect fun <B : ArrayBufferLike> Float32Array<B>.encodeAs16BitPcmByteArray(): ByteArray

expect fun createAudioContextOptions(latencyHint: AudioContextLatencyCategory, sampleRate: Int): AudioContextOptions
expect fun createMediaStreamConstraints(audio: MediaTrackConstraints): MediaStreamConstraints
expect fun createMediaTrackConstraints(deviceId: String?, sampleRate: Int, sampleSize: Int, channelCount: Int): MediaTrackConstraints
expect fun createAudioWorkletNode(context: BaseAudioContext, name: String): AudioWorkletNode