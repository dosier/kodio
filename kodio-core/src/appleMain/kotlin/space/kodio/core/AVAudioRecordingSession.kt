package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.SendChannel
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioEngine
import space.kodio.core.io.convert
import space.kodio.core.io.toByteArray
import space.kodio.core.util.namedLogger

private val log = namedLogger("AVAudioRecording")

abstract class AVAudioRecordingSession(
    private val format: AudioFormat = DefaultAppleRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private val audioEngine = AVAudioEngine()
    private lateinit var resolvedAVFormat: platform.AVFAudio.AVAudioFormat
    private lateinit var converter: AVAudioConverter

    abstract fun prepareAudioSession()

    override suspend fun prepareRecording(): AudioFormat {
        log.info { "prepareRecording() called with requested format: $format" }
        prepareAudioSession()

        resolvedAVFormat = try {
            format.toAVAudioFormat()
        } catch (e: Exception) {
            log.warn(e) {
                "Requested format $format is not supported by AVAudioFormat / converter; " +
                    "using ${DefaultAppleRecordingAudioFormat}"
            }
            DefaultAppleRecordingAudioFormat.toAVAudioFormat()
        }
        log.info {
            "Resolved AVAudioFormat: sampleRate=${resolvedAVFormat.sampleRate}, " +
                "channels=${resolvedAVFormat.channelCount}, commonFormat=${resolvedAVFormat.commonFormat}, " +
                "interleaved=${resolvedAVFormat.isInterleaved()}"
        }

        val hardwareIosAudioFormat = audioEngine.inputNode.outputFormatForBus(0u)
        log.info {
            "Hardware input format (inputNode.outputFormatForBus(0)): " +
                "sampleRate=${hardwareIosAudioFormat.sampleRate}, " +
                "channels=${hardwareIosAudioFormat.channelCount}, " +
                "commonFormat=${hardwareIosAudioFormat.commonFormat}, " +
                "interleaved=${hardwareIosAudioFormat.isInterleaved()}"
        }

        converter = AVAudioConverter(hardwareIosAudioFormat, resolvedAVFormat)
        log.info { "Created AVAudioConverter: hardware -> resolved" }

        audioEngine.prepare()
        val resultFormat = resolvedAVFormat.toCommonAudioFormat()
        log.info { "Engine prepared, returning format: $resultFormat" }
        return resultFormat
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val inputNode = audioEngine.inputNode
        val hardwareIosAudioFormat = inputNode.outputFormatForBus(0u)
        log.info {
            "startRecording(): hardware format sampleRate=${hardwareIosAudioFormat.sampleRate}, " +
                "channels=${hardwareIosAudioFormat.channelCount}, " +
                "commonFormat=${hardwareIosAudioFormat.commonFormat}"
        }

        log.info { "Installing tap on bus 0 with bufferSize=1024" }
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

        log.info { "Starting audio engine" }
        runErrorCatching {
            audioEngine.startAndReturnError(it)
        }.onFailure {
            log.error(it) { "Engine failed to start: ${it.message}" }
            throw AVAudioEngineException.FailedToStart(it.message ?: "Unknown error")
        }
        log.info { "Audio engine started successfully" }
    }

    override fun cleanup() {
        log.info { "cleanup(): isRunning=${audioEngine.isRunning()}" }
        if (audioEngine.isRunning())
            audioEngine.stop()
        audioEngine.inputNode.removeTapOnBus(0u)
        audioEngine.reset()
        log.info { "cleanup() complete" }
    }
}