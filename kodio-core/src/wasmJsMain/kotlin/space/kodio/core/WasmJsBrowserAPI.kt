@file:Suppress("FunctionName")

package space.kodio.core

import org.khronos.webgl.Float32Array
import org.w3c.dom.MessageEvent
import kotlin.js.Promise


external val navigator:Navigator

external class Navigator : JsAny {
    val mediaDevices: MediaDevices
}
open external class MediaDeviceInfo : JsAny {
    val kind: MediaDeviceKind
    val deviceId: String
    val label: String
}
external class MediaDevices : JsAny {
    fun getUserMedia(constraints: MediaStreamConstraints): Promise<MediaStream>
    fun enumerateDevices(): Promise<JsArray<MediaDeviceInfo>>
}
external interface MediaTrackSettings : JsAny {
    val sampleRate: Int?
}
open external class MediaStreamTrack : JsAny {
    fun getSettings(): MediaTrackSettings
    fun stop()
}
open external class MediaStream() : JsAny {
    fun getAudioTracks(): JsArray<MediaStreamTrack>
    fun getTracks(): JsArray<MediaStreamTrack>
}

open external class Worklet : JsAny {
    fun addModule(
        moduleURL: String,
        options: JsAny? = definedExternally,
    ) : Promise<Nothing?>
}

open external class AudioWorklet : Worklet, JsAny {
}

open external class BaseAudioContext : JsAny {
    val currentTime: Double
    val state: AudioContextState
    val audioWorklet: AudioWorklet
}

open external class AudioScheduledSourceNode : AudioNode, JsAny {
    fun start(`when`: Double = definedExternally)
    var onended: ((JsAny?) -> Unit)?
}
@Suppress("unused")
open external class AudioBufferSourceNode : AudioScheduledSourceNode, JsAny {
    var buffer: AudioBuffer?
}
@Suppress("unused")
external class AudioContext : BaseAudioContext, JsAny {

    constructor(contextOptions: AudioContextOptions = definedExternally)

    fun createMediaStreamSource(stream: MediaStream): MediaStreamAudioSourceNode
    fun createScriptProcessor(bufferSize: Int, inputChannels: Int, outputChannels: Int): ScriptProcessorNode
    fun createBufferSource(): AudioBufferSourceNode
    fun createBuffer(
        numberOfChannels: Int,
        length: Int,
        sampleRate: Float,
    ): AudioBuffer

    val destination: AudioNode
    val sampleRate: Float
    fun close(): Promise<Nothing?>

    fun suspend(): Promise<Nothing?>
    fun resume(): Promise<Nothing?>
}

external class MediaStreamAudioSourceNode : AudioNode, JsAny

open external class AudioNode : JsAny {
    fun connect(
        destinationNode: AudioNode,
        output: Int = definedExternally,
        input: Int = definedExternally,
    ): AudioNode

    fun disconnect()
}

external class ScriptProcessorNode : AudioNode, JsAny {
    var onaudioprocess: ((AudioProcessingEvent) -> Unit)?
}

external class AudioProcessingEvent : JsAny {
    val inputBuffer: AudioBuffer
    val outputBuffer: AudioBuffer
}

external interface AudioContextOptions : JsAny {
    val sampleRate: Float?
}
external interface MediaTrackConstraintSet : JsAny {
    var deviceId: String
    var sampleRate: Float
    var sampleSize: Int
    var channelCount: Int
}
external interface MediaTrackConstraints : MediaTrackConstraintSet, JsAny {

}
@Suppress("unused")
fun MediaTrackConstraints(deviceId: JsAny?, sampleRate: JsAny?, sampleSize: JsAny?, channelCount: JsAny?): MediaTrackConstraints {
    js("return { deviceId, sampleRate, sampleSize, channelCount };")
}
public external interface MediaStreamConstraints {
    val audio: MediaTrackConstraints
}
@Suppress("unused")
fun MediaStreamConstraints(audio: MediaTrackConstraints): MediaStreamConstraints {
    js("return { audio };")
}

internal val undefined: Nothing? = null
@Suppress("unused")
fun AudioContextOptions(latencyHint: JsAny? = undefined, sampleRate: JsAny? = undefined ): AudioContextOptions {
    js("return { latencyHint, sampleRate };")
}

external class AudioBuffer : JsAny {
    val duration: Double
    fun getChannelData(channel: Int): Float32Array
    fun copyToChannel(
        source: Float32Array,
        channelNumber: Int,
        bufferOffset: Int = definedExternally,
    )
}

external interface AudioNodeOptions : JsAny {

}
external interface AudioWorkletNodeOptions : AudioNodeOptions, JsAny {

}
open external class AudioWorkletNode(
    context: BaseAudioContext,
    name: String,
    options: AudioWorkletNodeOptions = definedExternally,
) : AudioNode, JsAny {

    val port: MessagePort
}
external interface MessageEventTarget : JsAny {

}
external class MessagePort : MessageEventTarget, JsAny {
    var onmessage: ((MessageEvent) -> Unit)?
    fun close()
}
sealed external interface AudioContextState : JsAny {

}

val AudioContextStateClosed: AudioContextState = js("'closed'")
val AudioContextStateRunning: AudioContextState = js("'running'")
val AudioContextStateSuspended: AudioContextState = js("'suspended'")

val MediaDeviceKindAudioInput: MediaDeviceKind = js("'audioinput'")
val MediaDeviceKindAudioOutput: MediaDeviceKind = js("'audiooutput'")
val MediaDeviceKindVideoInput: MediaDeviceKind = js("'videoinput'")

sealed external interface MediaDeviceKind : JsAny {

}
sealed external interface AudioContextLatencyCategory : JsAny {

}
val AudioContextLatencyCategoryBalanced: AudioContextLatencyCategory = js("'balanced'")
val AudioContextLatencyCategoryInteractive: AudioContextLatencyCategory = js("'interactive'")
val AudioContextLatencyCategoryPlayback: AudioContextLatencyCategory = js("'playback'")