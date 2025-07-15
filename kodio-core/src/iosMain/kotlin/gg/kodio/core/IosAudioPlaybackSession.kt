package gg.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession

/**
 * IOS implementation for [AudioPlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosAudioPlaybackSession() : BaseAudioPlaybackSession() {

    private val engine = AVAudioEngine()
    private val mixer = AVAudioMixerNode()
    private val player = AVAudioPlayerNode()

    init {
        engine.attachNode(mixer)
        engine.attachNode(player)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {

        engine.connect(player, mixer, format.toIosAudioFormat())
        engine.connect(player, engine.mainMixerNode, null)

        AVAudioSession.sharedInstance().configureCategoryPlayback()

        runIosCatching { errorVar ->
            engine.startAndReturnError(errorVar) // TODO: catch error
        }.onFailure {
            throw IosAudioEngineException.FailedToStart(it.message ?: "Unknown error")
        }
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        player.play()
        val iosAudioFormat = audioFlow.format.toIosAudioFormat()
        val lastCompletable = audioFlow.map { bytes ->
            val iosAudioBuffer = bytes.toIosAudioBuffer(iosAudioFormat)
            val iosAudioBufferFinishedIndicator = CompletableDeferred<Unit>()
            player.scheduleBuffer(iosAudioBuffer) {
                // somehow indicate that the buffer has finished playing
                iosAudioBufferFinishedIndicator.complete(Unit)
            }
            iosAudioBufferFinishedIndicator
        }.lastOrNull()
        lastCompletable?.await()
    }

    override fun onPause() {
        if (player.isPlaying())
            player.pause()
    }

    override fun onResume() {
        player.play()
    }

    override fun onStop() {
        if (player.isPlaying())
            player.stop()
        engine.stop()
        engine.disconnectNodeOutput(player)
        engine.disconnectNodeOutput(mixer)
    }
}