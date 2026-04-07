package space.kodio.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.inputNumberOfChannels
import platform.AVFAudio.outputNumberOfChannels
import platform.AVFAudio.sampleRate
import platform.AVFAudio.setActive
import space.kodio.core.util.namedLogger

private val log = namedLogger("AVAudioSessionExt")

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun AVAudioSession.configureCategoryRecord() {
    log.info { "configureCategoryRecord(): options=MAX_VALUE" }
    runErrorCatching { errorPtr ->
        setCategory(
            category = AVAudioSessionCategoryRecord,
            withOptions = AVAudioSessionCategoryOptions.MAX_VALUE,
            error = errorPtr
        )
    }.onFailure {
        log.error(it) { "Failed to set record category: ${it.message}" }
        throw AVAudioSessionException.FailedToSetCategory(it.message ?: "Unknown error")
    }
    log.info { "Record category set, resulting category=${category}" }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun AVAudioSession.configureCategoryPlayback() {
    log.info { "configureCategoryPlayback(): options=MAX_VALUE" }
    runErrorCatching { errorPtr ->
        setCategory(
            category = AVAudioSessionCategoryPlayback,
            withOptions = AVAudioSessionCategoryOptions.MAX_VALUE,
            error = errorPtr
        )
    }.onFailure {
        log.error(it) { "Failed to set playback category: ${it.message}" }
        throw AVAudioSessionException.FailedToSetCategory(it.message ?: "Unknown error")
    }
    log.info { "Playback category set, resulting category=${category}" }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun AVAudioSession.activate() {
    log.info { "activate()" }
    runErrorCatching { errorPtr ->
        setActive(true, error = errorPtr)
    }.onFailure {
        log.error(it) { "Failed to activate session: ${it.message}" }
        throw AVAudioSessionException.FailedToActivate(it.message ?: "Unknown error")
    }
    log.info {
        "Session activated: sampleRate=$sampleRate, " +
            "inputChannels=$inputNumberOfChannels, " +
            "outputChannels=$outputNumberOfChannels"
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun AVAudioSession.setPreferredInput(device: AudioDevice.Input) {
    log.info { "setPreferredInput(): device=${device.id} (${device.name})" }
    val portDescription = availableInputs
        ?.filterIsInstance<AVAudioSessionPortDescription>()
        ?.firstOrNull { it.UID == device.id }
    if (portDescription != null) {
        log.info { "Found matching port: ${portDescription.portName} (${portDescription.UID})" }
        runErrorCatching { errorVar ->
            setPreferredInput(portDescription, error = errorVar)
        }.onFailure {
            log.error(it) { "Failed to set preferred input: ${it.message}" }
            throw AVAudioSessionException.FailedToSetPreferredInput(device, it.message ?: "Unknown error")
        }
        log.info { "Preferred input set successfully" }
    } else {
        val available = availableInputs
            ?.filterIsInstance<AVAudioSessionPortDescription>()
            ?.map { "${it.portName} (${it.UID})" }
        log.error { "Input not found: ${device.id}. Available: $available" }
        throw AVAudioSessionException.InputNotFound(device)
    }
}
