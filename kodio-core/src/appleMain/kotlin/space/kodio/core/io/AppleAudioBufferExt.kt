package space.kodio.core.io

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Foundation.NSError
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toIosAudioBuffer(isoAudioFormat: AVAudioFormat): AVAudioPCMBuffer {
    val streamDescription = isoAudioFormat.streamDescription?.pointed
        ?: throw AppleAudioBufferException.MissingStreamDescription()
    if (streamDescription.mBytesPerFrame <= 0u)
        throw AppleAudioBufferException.InvalidStreamDescription(streamDescription)
    val buffer = AVAudioPCMBuffer(
        pCMFormat = isoAudioFormat,
        frameCapacity = size.toUInt() / streamDescription.mBytesPerFrame
    )
    val audioBufferList = buffer.audioBufferList?.pointed
        ?: throw AppleAudioBufferException.MissingAudioBufferList()
    buffer.frameLength = buffer.frameCapacity
    usePinned { pinned ->
        memcpy(audioBufferList.mBuffers.pointed.mData, pinned.addressOf(0), size.toULong())
    }
    return buffer
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun AVAudioConverter.convert(buffer: AVAudioPCMBuffer, targetFormat: AVAudioFormat): AVAudioPCMBuffer {
    // Calculate the expected output buffer size based on the sample rate ratio
    val sampleRateRatio = targetFormat.sampleRate / buffer.format.sampleRate
    val outputFrameCapacity = (buffer.frameCapacity.toDouble() * sampleRateRatio).toUInt()
    // Create the buffer that will hold the converted audio
    val outputBuffer = AVAudioPCMBuffer(targetFormat, outputFrameCapacity)
    return memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        // This block provides the original audio buffer to the converter when asked.

        // Perform the conversion from the hardware format to the target format
        val status = convertToBuffer(outputBuffer, errorPtr.ptr) { _, outStatusPtr ->
            outStatusPtr?.pointed?.value = AVAudioConverterInputStatus_HaveData
            buffer
        }
        if (status != AVAudioConverterInputStatus_HaveData)
            error("AVAudioConverter status: $status (error = ${errorPtr.value?.localizedDescription})")
        outputBuffer
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun AVAudioPCMBuffer.toByteArray(): ByteArray? {
    val buffer = audioBufferList?.pointed?.mBuffers?.pointed
    val dataSize = frameLength.toInt() * format.streamDescription!!.pointed.mBytesPerFrame.toInt()
    val data = buffer?.mData?.readBytes(dataSize)
    return data
}