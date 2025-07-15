package gg.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioPlayerNode

/**
 * IOS implementation for [AudioPlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosAudioPlaybackSession() : BaseAudioPlaybackSession() {

    private val engine = AVAudioEngine()
    private val playerNode = AVAudioPlayerNode()
    private val formatConverterMixer = AVAudioMixerNode()

    init {
        engine.attachNode(playerNode)
        engine.attachNode(formatConverterMixer)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        val iosAudioFormat = format.toIosAudioFormat()

        engine.connect(playerNode, formatConverterMixer, iosAudioFormat)
        engine.connect(formatConverterMixer, engine.mainMixerNode, null)

        engine.prepare()
        engine.startAndReturnError(null) // TODO: catch error

        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        playerNode.play()
        val iosAudioFormat = audioFlow.format.toIosAudioFormat()
        val lastCompletable = audioFlow.map { bytes ->
            val iosAudioBuffer = bytes.toIosAudioBuffer(iosAudioFormat)
            val iosAudioBufferFinishedIndicator = CompletableDeferred<Unit>()
            playerNode.scheduleBuffer(iosAudioBuffer) {
                // somehow indicate that the buffer has finished playing
                iosAudioBufferFinishedIndicator.complete(Unit)
            }
            iosAudioBufferFinishedIndicator
        }.lastOrNull()
        lastCompletable?.await()
    }

    override fun onPause() {
        if (playerNode.isPlaying())
            playerNode.pause()
    }

    override fun onResume() {
        playerNode.play()
    }

    override fun onStop() {
        if (playerNode.isPlaying())
            playerNode.stop()
        engine.stop()
        engine.disconnectNodeOutput(playerNode)
        engine.disconnectNodeOutput(formatConverterMixer)
    }
}