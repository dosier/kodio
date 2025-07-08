fun getMediaConstraints(device: AudioDevice, format: AudioFormat): MediaStreamConstraints = MediaStreamConstraints(
    audio = MediaTrackConstraints(
        deviceId = device.id.toJsString(),
        sampleRate = format.sampleRate.toJsNumber(),
        sampleSize = format.bitDepth.value.toJsNumber(),
        channelCount = format.channels.count.toJsNumber()
    )
)