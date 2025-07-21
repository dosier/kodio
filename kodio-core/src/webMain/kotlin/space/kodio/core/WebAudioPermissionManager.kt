package space.kodio.core

import space.kodio.core.security.AudioPermissionManager
import web.events.Event
import web.events.EventHandler
import web.navigator.navigator
import web.permissions.*

/**
 * TODO: use permissions api, for some reason PermissionName.microphone is not added yet?
 */
open class WebAudioPermissionManager : AudioPermissionManager() {

    override suspend fun requestPermission() {
        queryPerms()
    }

    private suspend fun queryPerms(): State {
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
        return toState(status)
    }

    override fun requestRedirectToSettings() {
        throw Error.SettingsRedirectionNotSupported
    }

    override suspend fun checkState(): State =
        queryPerms()

    private fun toState(result: PermissionStatus): State = when (result.state) {
        PermissionState.denied -> State.Denied
        PermissionState.granted -> State.Granted
        PermissionState.prompt -> State.Requesting
    }
}
