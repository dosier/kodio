package space.kodio.core

internal val DefaultWebRecordingAudioFormat = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmFloat(
        precision = FloatPrecision.F32,
        layout = SampleLayout.Interleaved
    )
)