package space.kodio.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.setActive


@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun AVAudioSession.configureCategoryRecord() {
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
internal fun AVAudioSession.configureCategoryPlayback() {
    runIosCatching { errorPtr ->
        setCategory(
            category = AVAudioSessionCategoryPlayback,
            withOptions = AVAudioSessionCategoryOptions.MAX_VALUE,
            error = errorPtr
        )
    }.onFailure {
        throw IosAudioSessionException.FailedToSetCategory(it.message ?: "Unknown error")
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun AVAudioSession.activate() {
    runIosCatching { errorPtr ->
        setActive(true, error = errorPtr)
    }.onFailure {
        throw IosAudioSessionException.FailedToActivate(it.message ?: "Unknown error")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun AVAudioSession.setPreferredInput(device: AudioDevice.Input) {
    // Find the port description matching our device
    val portDescription = availableInputs
        ?.filterIsInstance<AVAudioSessionPortDescription>()
        ?.firstOrNull { it.UID == device.id }
    if (portDescription != null) {
        runIosCatching { errorVar ->
            setPreferredInput(portDescription, error = errorVar)
        }.onFailure {
            throw IosAudioSessionException.FailedToSetPreferredInput(device, it.message ?: "Unknown error")
        }
    } else
        throw IosAudioSessionException.InputNotFound(device)
}
