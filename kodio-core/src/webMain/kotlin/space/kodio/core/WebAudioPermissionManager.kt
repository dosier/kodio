package space.kodio.core

import space.kodio.core.security.AudioPermissionManager

/**
 * TODO: use permissions api, for some reason PermissionName.microphone is not added yet?
 */
abstract class WebAudioPermissionManager : AudioPermissionManager() {

    protected abstract suspend fun ensurePermissions(): Boolean

    override suspend fun requestPermission() {
        ensurePermissions()
    }

    override fun requestRedirectToSettings() {
        throw Error.SettingsRedirectionNotSupported
    }

    override suspend fun checkState(): State {
        return if (ensurePermissions())
            State.Granted
        else
            State.Denied
    }
}