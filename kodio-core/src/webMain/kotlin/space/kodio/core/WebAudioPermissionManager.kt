package space.kodio.core

import space.kodio.core.security.AudioPermissionManager
import web.events.Event
import web.events.EventHandler
import web.mediadevices.getUserMedia
import web.navigator.navigator
import web.permissions.*

open class WebAudioPermissionManager : AudioPermissionManager() {

    override suspend fun requestPermission() {
        try {
            val constraints = createMediaStreamConstraints(
                audio = createMediaTrackConstraints(deviceId = null, sampleRate = 48000, sampleSize = 16, channelCount = 1)
            )
            val stream = navigator.mediaDevices.getUserMedia(constraints)
            stream.getTracks().toList().forEach { it.stop() }
            setState(State.Granted)
        } catch (_: Throwable) {
            setState(State.Denied)
        }
        registerOnChangeListener()
    }

    private suspend fun registerOnChangeListener() {
        val status = navigator.permissions.query(microphonePermissionDescriptor)
        status.onchange = EventHandler { a: Event ->
            runCatching {
                val currentState = a.currentTarget as PermissionStatus
                setState(toState(currentState))
            }.onFailure {
                it.printStackTrace()
                setState(State.Unknown)
            }
        }
    }

    override fun requestRedirectToSettings() {
        throw Error.SettingsRedirectionNotSupported
    }

    override suspend fun checkState(): State {
        val status = navigator.permissions.query(microphonePermissionDescriptor)
        return toState(status)
    }

    private fun toState(result: PermissionStatus): State = when (result.state) {
        PermissionState.denied -> State.Denied
        PermissionState.granted -> State.Granted
        PermissionState.prompt -> State.Unknown
    }
}
