package gg.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.SendChannel
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession

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
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u, // A common buffer size
            format = hardwareIosAudioFormat // Tap in the hardware's native format
        ) { buffer, _ ->
            if (buffer == null) return@installTapOnBus
            val bufferInTargetFormat = converter.convert(buffer, targetIosAudioFormat)
            val bufferData = bufferInTargetFormat.toByteArray()
            if (bufferData != null)
                channel.trySend(bufferData)
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