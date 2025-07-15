package gg.kodio.core

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.SendChannel
import platform.AVFAudio.*
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.Foundation.NSError

/**
 * IOS implementation for [AudioRecordingSession].
 *
 * @param device The input device to record from.
 */
class IosAudioRecordingSession(
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultIosRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private val audioEngine = AVAudioEngine()
    private val targetIosAudioFormat get() = format.toIosAudioFormat()

    private lateinit var converter: AVAudioConverter

    override suspend fun prepareRecording(): AudioFormat {
        val audioSession = AVAudioSession.Companion.sharedInstance()
        audioSession.configureCategoryRecord()
        audioSession.activate()
        audioSession.setPreferredInput(device)
        val hardwareIosAudioFormat = audioEngine.inputNode.outputFormatForBus(0u)
        converter = AVAudioConverter(hardwareIosAudioFormat, targetIosAudioFormat)
        audioEngine.prepare()
        return targetIosAudioFormat.toCommonAudioFormat()
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val inputNode = audioEngine.inputNode
        val hardwareIosAudioFormat = inputNode.outputFormatForBus(0u)
        println("HW_IN:")
        println(inputNode.inputFormatForBus(0u).toCommonAudioFormat())
        println("HW_OUT:")
        println(hardwareIosAudioFormat.toCommonAudioFormat())
        println("TARGET:")
        println(targetIosAudioFormat.toCommonAudioFormat())
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u, // A common buffer size
            format = hardwareIosAudioFormat // Tap in the hardware's native format
        ) { buffer, _ ->
            if (buffer == null) return@installTapOnBus
            try {
                println(buffer?.toByteArray()?.average())
                val bufferInTargetFormat = converter.convert(buffer, targetIosAudioFormat)
                val bufferData = bufferInTargetFormat.toByteArray()
                println("cc: "+bufferData?.average())
                if (bufferData != null)
                    channel.trySend(bufferData)
                else
                    println("Buffer data is null?")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        runIosCatching {
            audioEngine.startAndReturnError(it)
        }.onFailure {
            throw IosAudioEngineException.FailedToStart(it.message ?: "Unknown error")
        }
    }

    override fun cleanup() {

        if (audioEngine.isRunning())
            audioEngine.stop()
        audioEngine.inputNode.removeTapOnBus(0u)
        audioEngine.reset()
    }
}


@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun AVAudioSession.configureCategoryRecord() {
    runIosCatching { errorPtr ->
        setCategory(
            category = AVAudioSessionCategoryRecord,
            withOptions = AVAudioSessionCategoryOptions.MAX_VALUE,
            error = errorPtr
        )
    }.onFailure {
        throw IosAudioSessionException.FailedToSetCategory(it.message ?: "Unknown error")
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun AVAudioSession.activate() {
    runIosCatching { errorPtr ->
        setActive(true, error = errorPtr)
    }.onFailure {
        throw IosAudioSessionException.FailedToActivate(it.message ?: "Unknown error")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVAudioSession.setPreferredInput(device: AudioDevice.Input) {
    // Find the port description matching our device
    val portDescription = availableInputs
        ?.filterIsInstance<AVAudioSessionPortDescription>()
        ?.firstOrNull { it.UID == device.id }
    if (portDescription != null) {
        runIosCatching { errorVar ->
            setPreferredInput(portDescription, error = null)
        }.onFailure {
            throw IosAudioSessionException.FailedToSetPreferredInput(device, it.message ?: "Unknown error")
        }
    } else
        throw IosAudioSessionException.InputNotFound(device)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun <T> runIosCatching(block: (CPointer<ObjCObjectVar<NSError?>>) -> T): Result<T> {
    memScoped {
        val errorVar = alloc<ObjCObjectVar<NSError?>>()
        val result = block(errorVar.ptr)
        val errorValue = errorVar.value
        return if (errorValue != null)
            Result.failure(IosException(errorValue))
        else
            Result.success(result)
    }
}

private class IosException(error: NSError) : Exception(error.localizedDescription)