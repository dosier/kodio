package space.kodio.core

import platform.AVFAudio.AVAudioSession

/**
 * IOS implementation for [AppleAudioPlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosAudioPlaybackSession() : AppleAudioPlaybackSession() {

    override fun configureAudioSession() {
        AVAudioSession.sharedInstance().configureCategoryPlayback()
    }
}