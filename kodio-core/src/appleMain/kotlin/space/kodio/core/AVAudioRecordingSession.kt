package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.SendChannel
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioEngine
import space.kodio.core.io.convert
import space.kodio.core.io.toByteArray
import space.kodio.core.util.namedLogger

private val avRecordingLogger = namedLogger("AVAudioRecording")

abstract class AVAudioRecordingSession(
    private val format: AudioFormat = DefaultAppleRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private val audioEngine = AVAudioEngine()
    private lateinit var resolvedAVFormat: platform.AVFAudio.AVAudioFormat
    private lateinit var converter: AVAudioConverter

    abstract fun prepareAudioSession()

    override suspend fun prepareRecording(): AudioFormat {
        prepareAudioSession()

        resolvedAVFormat = try {
            format.toAVAudioFormat()
        } catch (e: Exception) {
            avRecordingLogger.warn(e) {
                "Requested format $format is not supported by AVAudioFormat / converter; " +
                    "using ${DefaultAppleRecordingAudioFormat}"
            }
            DefaultAppleRecordingAudioFormat.toAVAudioFormat()
        }

        val hardwareIosAudioFormat = audioEngine.inputNode.outputFormatForBus(0u)
        converter = AVAudioConverter(hardwareIosAudioFormat, resolvedAVFormat)
        audioEngine.prepare()
        return resolvedAVFormat.toCommonAudioFormat()
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val inputNode = audioEngine.inputNode
        val hardwareIosAudioFormat = inputNode.outputFormatForBus(0u)
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u,
            format = hardwareIosAudioFormat
        ) { buffer, _ ->
            if (buffer == null) return@installTapOnBus
            val bufferInTargetFormat = converter.convert(buffer, resolvedAVFormat)
            val bufferData = bufferInTargetFormat.toByteArray()
            if (bufferData != null)
                channel.trySend(bufferData)
        }
        runErrorCatching {
            audioEngine.startAndReturnError(it)
        }.onFailure {
            throw AVAudioEngineException.FailedToStart(it.message ?: "Unknown error")
        }
    }

    override fun cleanup() {
        if (audioEngine.isRunning())
            audioEngine.stop()
        audioEngine.inputNode.removeTapOnBus(0u)
        audioEngine.reset()
    }
}