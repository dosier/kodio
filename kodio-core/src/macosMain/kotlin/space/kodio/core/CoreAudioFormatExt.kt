package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.alloc
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsBigEndian
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsNonInterleaved
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatFlagsNativeEndian
import platform.CoreAudioTypes.kAudioFormatLinearPCM

@OptIn(ExperimentalForeignApi::class)
fun NativePlacement.allocASBD(format: AudioFormat): AudioStreamBasicDescription {
    val asbd = alloc<AudioStreamBasicDescription>()
    asbd.mSampleRate = format.sampleRate.toDouble()
    asbd.mFormatID = when (format.encoding) {
        is SampleEncoding.PcmInt,
        is SampleEncoding.PcmFloat -> kAudioFormatLinearPCM
    }
    when (val e = format.encoding) {
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
            asbd.mChannelsPerFrame = format.channels.count.toUInt()
            val bytesPerSample = e.bitDepth.bits / 8
            val bytesPerFrame = if (e.layout == SampleLayout.Interleaved)
                bytesPerSample * format.channels.count else bytesPerSample
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
            asbd.mChannelsPerFrame = format.channels.count.toUInt()
            val bytesPerSample = when (e.precision) {
                FloatPrecision.F32 -> 4
                FloatPrecision.F64 -> 8
            }
            val bytesPerFrame = if (e.layout == SampleLayout.Interleaved)
                bytesPerSample * format.channels.count else bytesPerSample
            asbd.mBytesPerFrame = bytesPerFrame.toUInt()
            asbd.mBytesPerPacket = asbd.mBytesPerFrame
        }
    }
    return asbd
}


/**
 * Map an ASBD to your AudioFormat, or null if it can't be represented.
 * Supports Linear PCM (int/float), mono or stereo, interleaved or planar.
 */
internal fun toCommonAudioFormat(asbd: AudioStreamBasicDescription): AudioFormat? {
    // Only handle Linear PCM
    if (asbd.mFormatID != kAudioFormatLinearPCM) return null

    val flags: UInt = asbd.mFormatFlags
    val isFloat        = (flags and kAudioFormatFlagIsFloat) != 0u
    val isSignedInt    = (flags and kAudioFormatFlagIsSignedInteger) != 0u
    val isBigEndian    = (flags and kAudioFormatFlagIsBigEndian) != 0u
    val isNonInterleaved = (flags and kAudioFormatFlagIsNonInterleaved) != 0u
    val bits           = asbd.mBitsPerChannel.toInt()
    val chCount        = asbd.mChannelsPerFrame.toInt()

    // Channels (adjust if your Channels supports more than Mono/Stereo)
    val channels = when (chCount) {
        1 -> Channels.Mono
        2 -> Channels.Stereo
        else -> return null
    }

    val endianness = if (isBigEndian) Endianness.Big else Endianness.Little
    val layout     = if (isNonInterleaved) SampleLayout.Planar else SampleLayout.Interleaved

    val sampleRate = (asbd.mSampleRate.takeIf { it > 0.0 } ?: 44100.0).toInt()

    val encoding: SampleEncoding = if (isFloat) {
        // Float PCM
        val precision = when (bits) {
            32 -> FloatPrecision.F32
            64 -> FloatPrecision.F64
            else -> return null
        }
        SampleEncoding.PcmFloat(
            precision = precision,
            layout = layout
        )
    } else {
        // Integer PCM
        val depth = when (bits) {
            8  -> IntBitDepth.Eight
            16 -> IntBitDepth.Sixteen
            24 -> IntBitDepth.TwentyFour
            32 -> IntBitDepth.ThirtyTwo
            else -> return null
        }
        SampleEncoding.PcmInt(
            bitDepth = depth,
            endianness = endianness,
            layout = layout,
            signed = isSignedInt,
            packed = ((flags and kAudioFormatFlagIsPacked) != 0u)
        )
    }

    return AudioFormat(
        sampleRate = sampleRate,
        channels = channels,
        encoding = encoding
    )
}
