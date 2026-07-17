package space.kodio.compose

import space.kodio.compose.internal.peakAmplitudes
import space.kodio.core.AudioRecording

/**
 * Builds a static peak waveform for this recording.
 *
 * Peak extraction is encoding-aware (PCM int 8/16/24/32 with endianness,
 * and float32/64), so lossless / high bit-depth clips render correctly.
 *
 * @param barCount Number of amplitude bars to produce.
 * @return Amplitudes in `0f..1f`, normalized to the track peak.
 */
fun AudioRecording.waveformPeaks(barCount: Int = 96): List<Float> {
    if (barCount <= 0 || isEmpty) return emptyList()
    return peakAmplitudes(
        bytes = toByteArray(),
        format = format,
        barCount = barCount,
    )
}
