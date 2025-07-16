package space.kodio.core

import web.media.streams.MediaStreamConstraints
import web.media.streams.MediaTrackConstraints

internal fun getMediaConstraints(device: AudioDevice, format: AudioFormat): MediaStreamConstraints = MediaStreamConstraints(
    audio = MediaTrackConstraints(
        deviceId = device.id,
        sampleRate = format.sampleRate,
        sampleSize = format.bitDepth,
        channelCount = format.channels
    )
)