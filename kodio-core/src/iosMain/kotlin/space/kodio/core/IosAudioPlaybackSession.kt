package space.kodio.core

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.currentRoute
import platform.AVFAudio.outputNumberOfChannels
import platform.AVFAudio.sampleRate
import space.kodio.core.util.namedLogger

private val log = namedLogger("IosAudioPlayback")

/**
 * IOS implementation for [AVAudioPlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosAudioPlaybackSession() : AVAudioPlaybackSession() {

    override fun configureAudioSession() {
        log.info { "configureAudioSession() - setting playback category" }
        AVAudioSession.sharedInstance().configureCategoryPlayback()
        val session = AVAudioSession.sharedInstance()
        log.info {
            "Audio session after playback config: category=${session.category}, " +
                "sampleRate=${session.sampleRate}, " +
                "outputChannels=${session.outputNumberOfChannels}, " +
                "currentRoute.outputs=${session.currentRoute.outputs}"
        }
    }
}