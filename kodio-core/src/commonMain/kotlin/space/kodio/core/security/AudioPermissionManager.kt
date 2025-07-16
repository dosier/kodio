package space.kodio.core.security


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstract class for managing audio permission on a platform.
 */
abstract class AudioPermissionManager {

    private val _state = MutableStateFlow(State.Unknown)

    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Request to open the microphone permission settings on the platform.
     *
     * @throws Error.SettingsRedirectionNotSupported if the platform does not support redirection.
     */
    @Throws(Error.SettingsRedirectionNotSupported::class)
    abstract fun requestRedirectToSettings()

    /**
     * Refresh the current state of the platform-specific audio permission,
     * updates the [state] flow, and returns the new state.
     */
    abstract fun refreshState() : State

    protected fun setState(state: State) {
        _state.value = state
    }

    sealed class Error(message: String, cause: Throwable? = null) : Throwable(message, cause) {
        object SettingsRedirectionNotSupported : Throwable("This platform does not support redirection to the settings.")
    }

    enum class State {
        Unknown,
        Requesting,
        Granted,
        Denied
    }
}
