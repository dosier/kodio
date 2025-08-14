package space.kodio.core

/**
 * IOS implementation for [AppleAudioPlaybackSession].
 *
 * In IOS, we cannot control the output device, so we ignore it.
 */
class IosAudioPlaybackSession() : AppleAudioPlaybackSession()