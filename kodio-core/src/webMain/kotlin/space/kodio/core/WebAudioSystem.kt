package space.kodio.core

import js.array.toList
import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager
import space.kodio.core.util.namedLogger
import web.mediadevices.MediaDeviceInfo
import web.mediadevices.MediaDeviceKind
import web.mediadevices.audioinput
import web.mediadevices.audiooutput
import web.mediadevices.enumerateDevices
import web.navigator.navigator

private val logger = namedLogger("WebAudioSystem")

open class WebAudioSystem : SystemAudioSystemImpl() {

    override val permissionManager: AudioPermissionManager =
        WebAudioPermissionManager()

    override suspend fun listInputDevices(): List<AudioDevice.Input> =
        listDevices(MediaDeviceKind.audioinput)
            .map(::toInputDevice)
            .also { devices ->
                logger.debug {
                    "Enumerated ${devices.size} input device(s): " +
                        devices.joinToString { "${it.name.ifBlank { "<no-label>" }}(${it.id.take(12)})" }
                }
            }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> =
        listDevices(MediaDeviceKind.audiooutput)
            .map(::toOutputDevice)
            .also { devices ->
                logger.debug {
                    "Enumerated ${devices.size} output device(s): " +
                        devices.joinToString { "${it.name.ifBlank { "<no-label>" }}(${it.id.take(12)})" }
                }
            }

    override suspend fun createRecordingSession(
        requestedDevice: AudioDevice.Input?,
        requestedFormat: AudioFormat?,
    ): AudioRecordingSession =
        createRecordingSession(requestedDevice, requestedFormat, InputProcessing.PlatformDefault)

    override suspend fun createRecordingSession(
        requestedDevice: AudioDevice.Input?,
        requestedFormat: AudioFormat?,
        inputProcessing: InputProcessing,
    ): AudioRecordingSession {
        logger.debug {
            "createRecordingSession device=${requestedDevice?.let { "${it.name}(${it.id.take(12)})" } ?: "<system-default>"}, " +
                "format=${requestedFormat ?: DefaultWebRecordingAudioFormat}, inputProcessing=$inputProcessing"
        }
        return WebAudioRecordingSession(
            device = requestedDevice,
            format = requestedFormat ?: DefaultWebRecordingAudioFormat,
            inputProcessing = inputProcessing,
        )
    }

    override suspend fun createPlaybackSession(requestedDevice: AudioDevice.Output?): AudioPlaybackSession {
        // Output routing via AudioContext.setSinkId is Chrome/Edge-only; keep unsupported for now.
        if (requestedDevice != null) {
            throw AudioError.DeviceSelectionUnsupported(AudioError.DeviceSelectionUnsupported.Kind.Output)
        }
        return WebAudioPlaybackSession()
    }

    private suspend fun listDevices(type: MediaDeviceKind): List<MediaDeviceInfo> {
        return try {
            permissionManager.withMicrophonePermission {
                navigator
                    .mediaDevices
                    .enumerateDevices()
                    .toList()
                    .filter { it.kind == type }
                    .filter { it.deviceId.isNotEmpty() }
                    .also { devices ->
                        logger.trace {
                            "enumerateDevices($type): " +
                                devices.joinToString { "id=${it.deviceId.take(12)} label='${it.label}' group=${it.groupId.take(8)}" }
                        }
                    }
            }
        } catch (e: AudioPermissionDeniedException) {
            logger.warn(e) { "Cannot enumerate $type devices: microphone permission denied" }
            emptyList()
        }
    }
}

private fun toOutputDevice(info: MediaDeviceInfo): AudioDevice.Output = AudioDevice.Output(
    id = info.deviceId,
    name = info.label.ifBlank { "Audio output (${info.deviceId.take(8)})" },
    formatSupport = AudioFormatSupport.Unknown
)

private fun toInputDevice(info: MediaDeviceInfo): AudioDevice.Input = AudioDevice.Input(
    id = info.deviceId,
    name = info.label.ifBlank { "Microphone (${info.deviceId.take(8)})" },
    formatSupport = AudioFormatSupport.Unknown
)
