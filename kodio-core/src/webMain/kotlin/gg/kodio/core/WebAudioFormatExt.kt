package gg.kodio.core

internal val DefaultWebRecordingAudioFormat = AudioFormat(
    sampleRate = 44100,
    bitDepth = BitDepth.Sixteen,
    channels = Channels.Mono,
    encoding = Encoding.Pcm.Signed,
    endianness = Endianness.Little
)