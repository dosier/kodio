package space.kodio.core.security


import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Abstract class for managing audio permission on a platform.
 */
abstract class AudioPermissionManager {

    private val _state = MutableStateFlow(State.Unknown)

    val state: StateFlow<State> = _state.asStateFlow()

    abstract suspend fun requestPermission()

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
    protected abstract suspend fun checkState() : State

    protected fun setState(state: State) {
        _state.value = state
    }

    suspend fun refreshState(): State =
        checkState().also(::setState)

    /**
     * Executes the [block] if the microphone permission is granted,
     * otherwise throws an [AudioPermissionDeniedException].
     */
    @Throws(AudioPermissionDeniedException::class, CancellationException::class)
    suspend fun <T> withMicrophonePermission(block: suspend () -> T): T {
        val newState = refreshState()
        return if (newState == State.Unknown) {
            setState(State.Requesting)
            coroutineScope {
                launch {
                    requestPermission()
                }
                launch {
                    state.first { it != State.Requesting }
                }
            }
            if (state.value == State.Granted)
                block()
            else
                throw AudioPermissionDeniedException
        } else
            block()
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
