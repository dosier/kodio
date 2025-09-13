package space.kodio.core

import platform.AVFAudio.AVAudioSession

/**
 * IOS implementation for [AVAudioPlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosAudioPlaybackSession() : AVAudioPlaybackSession() {

    override fun configureAudioSession() {
        AVAudioSession.sharedInstance().configureCategoryPlayback()
    }
}