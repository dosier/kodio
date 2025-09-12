package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import platform.AVFAudio.AVAudioCommonFormat
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatFloat64
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsNonInterleaved
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatFlagsNativeEndian
import platform.CoreAudioTypes.kAudioFormatLinearPCM

val DefaultAppleRecordingAudioFormat = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen, Endianness.Little, SampleLayout.Interleaved, signed = true)
)

typealias AppleAudioFormat = AVAudioFormat


@OptIn(ExperimentalForeignApi::class)
val AudioFormat.streamDescription: AudioStreamBasicDescription
    get() = toAppleAudioFormat().streamDescription()?.pointed
        ?: throw AppleAudioFormatException.NoStreamDescription(this)

/**
 * Converts this common [AudioFormat] to an [AppleAudioFormat] instance.
 *
 * @throws AppleAudioFormatException if conversion is not supported.
 */
@Throws(AppleAudioFormatException::class)
fun AudioFormat.toAppleAudioFormat(): AppleAudioFormat {
    val (commonFormat, interleaved) = when (val e = encoding) {
        is SampleEncoding.PcmInt -> when (e.bitDepth) {
            IntBitDepth.Sixteen -> AVAudioPCMFormatInt16 to (e.layout == SampleLayout.Interleaved)
            // If you really want 24-bit int via AVAudioFormat, you’ll need a custom asbd; AVAudioCommonFormat doesn’t have 24-bit.
            IntBitDepth.Eight, IntBitDepth.TwentyFour, IntBitDepth.ThirtyTwo ->
                throw IllegalArgumentException("Use explicit ASBD for ${e.bitDepth.bits}-bit PCM Int on CoreAudio.")
        }
        is SampleEncoding.PcmFloat -> when (e.precision) {
            FloatPrecision.F32 -> AVAudioPCMFormatFloat32 to (e.layout == SampleLayout.Interleaved)
            FloatPrecision.F64 -> AVAudioPCMFormatFloat64 to (e.layout == SampleLayout.Interleaved)
        }
    }

    return AVAudioFormat(
        commonFormat = commonFormat,
        sampleRate = sampleRate.toDouble(),
        channels = channels.count.toUInt(),
        interleaved = interleaved
    )
}

/**
 * Converts this [AppleAudioFormat] to a common [AudioFormat] instance.
 *
 * @throws AppleAudioFormatException if conversion is not supported.
 */
@Throws(AppleAudioFormatException::class)
fun AppleAudioFormat.toCommonAudioFormat(): AudioFormat {
    return AudioFormat(
        sampleRate = sampleRate.toInt(),
        channels = Channels.fromInt(channelCount.toInt()),
        encoding = commonFormat.toCommonEncoding(),
    )
}

private fun AVAudioCommonFormat.toCommonEncoding(): SampleEncoding = when (this) {
    AVAudioPCMFormatInt16 -> SampleEncoding.PcmInt(IntBitDepth.Sixteen, Endianness.Little, SampleLayout.Interleaved, true)
    AVAudioPCMFormatFloat32 -> SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
    AVAudioPCMFormatFloat64 -> SampleEncoding.PcmFloat(FloatPrecision.F64, SampleLayout.Interleaved)
    else -> throw AppleAudioFormatException.UnknownEncodingForCommonFormat(this)
}

@OptIn(ExperimentalForeignApi::class)
fun AudioFormat.toASBD(): AudioStreamBasicDescription = memScoped {
    val asbd = alloc<AudioStreamBasicDescription>()
    asbd.mSampleRate = sampleRate.toDouble()
    asbd.mFormatID = kAudioFormatLinearPCM

    when (val e = encoding) {
        is SampleEncoding.PcmInt -> {
            val isLE = (e.endianness == Endianness.Little)
            val flags =
                (if (e.signed) kAudioFormatFlagIsSignedInteger else 0u) or
                (if (e.packed) kAudioFormatFlagIsPacked else 0u) or
                (if (isLE) kAudioFormatFlagsNativeEndian else 0u) or
                (if (e.layout == SampleLayout.Planar) kAudioFormatFlagIsNonInterleaved else 0u)

            asbd.mFormatFlags = flags
            asbd.mBitsPerChannel = e.bitDepth.bits.toUInt()
            asbd.mFramesPerPacket = 1u
            asbd.mChannelsPerFrame = channels.count.toUInt()
            val bytesPerSample = e.bitDepth.bits / 8
            val bytesPerFrame = if (e.layout == SampleLayout.Interleaved)
                bytesPerSample * channels.count else bytesPerSample
            asbd.mBytesPerFrame = bytesPerFrame.toUInt()
            asbd.mBytesPerPacket = asbd.mBytesPerFrame
        }

        is SampleEncoding.PcmFloat -> {
            val flags =
                kAudioFormatFlagIsFloat or
                kAudioFormatFlagIsPacked or
                kAudioFormatFlagsNativeEndian or
                (if (e.layout == SampleLayout.Planar) kAudioFormatFlagIsNonInterleaved else 0u)
            asbd.mFormatFlags = flags
            asbd.mBitsPerChannel = when (e.precision) {
                FloatPrecision.F32 -> 32u
                FloatPrecision.F64 -> 64u
            }
            asbd.mFramesPerPacket = 1u
            asbd.mChannelsPerFrame = channels.count.toUInt()
            val bytesPerSample = when (e.precision) { FloatPrecision.F32 -> 4; FloatPrecision.F64 -> 8 }
            val bytesPerFrame = if (e.layout == SampleLayout.Interleaved)
                bytesPerSample * channels.count else bytesPerSample
            asbd.mBytesPerFrame = bytesPerFrame.toUInt()
            asbd.mBytesPerPacket = asbd.mBytesPerFrame
        }
    }

    asbd
}