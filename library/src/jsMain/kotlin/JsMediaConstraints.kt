import web.media.streams.MediaStreamConstraints
import web.media.streams.MediaTrackConstraints

fun getMediaConstraints(device: AudioDevice, format: AudioFormat): MediaStreamConstraints = MediaStreamConstraints(
    audio = MediaTrackConstraints(
        deviceId = device.id,
        sampleRate = format.sampleRate,
        sampleSize = format.bitDepth,
        channelCount = format.channels
    )
)