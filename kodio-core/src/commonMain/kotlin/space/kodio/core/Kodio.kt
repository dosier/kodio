package space.kodio.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

/**
 * Main entry point for the Kodio audio library.
 * 
 * Provides a simplified, discoverable API for audio recording and playback
 * with sensible defaults and progressive disclosure of advanced options.
 * 
 * ## Quick Start
 * 
 * ### Recording Audio
 * ```kotlin
 * // Simple 5-second recording
 * val recording = Kodio.record(duration = 5.seconds)
 * 
 * // Manual control
 * Kodio.record { recorder ->
 *     recorder.start()
 *     // ... wait for user action
 *     recorder.stop()
 *     recorder.getRecording() // access the result
 * }
 * ```
 * 
 * ### Playing Audio
 * ```kotlin
 * // Play a recording
 * Kodio.play(recording)
 * 
 * // Play with control
 * Kodio.play(recording) { player ->
 *     player.start()
 *     delay(2.seconds)
 *     player.pause()
 *     // ...
 * }
 * ```
 * 
 * ### Platform Initialization
 * 
 * **Android** - In your Application class:
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Kodio.initialize(this)
 *     }
 * }
 * ```
 * 
 * Other platforms (JVM, iOS, JS) work without explicit initialization.
 */
object Kodio {

    // ==================== DEVICE LISTING ====================

    /**
     * Lists all available audio input devices (microphones).
     */
    suspend fun listInputDevices(): List<AudioDevice.Input> =
        SystemAudioSystem.listInputDevices()

    /**
     * Lists all available audio output devices (speakers).
     */
    suspend fun listOutputDevices(): List<AudioDevice.Output> =
        SystemAudioSystem.listOutputDevices()

    // ==================== PERMISSIONS ====================

    /**
     * The current state of the microphone permission.
     * This is a singleton instance for consistent state tracking.
     */
    val microphonePermission: MicrophonePermission by lazy {
        MicrophonePermission(SystemAudioSystem.permissionManager)
    }

    /**
     * Wrapper around the permission manager for a cleaner API.
     */
    class MicrophonePermission internal constructor(
        private val manager: space.kodio.core.security.AudioPermissionManager
    ) {
        /** Current permission state as a StateFlow */
        val state get() = manager.state

        /** Whether permission is currently granted */
        val isGranted: Boolean
            get() = manager.state.value == space.kodio.core.security.AudioPermissionManager.State.Granted

        /** Request microphone permission from the user */
        suspend fun request() = manager.requestPermission()

        /** Refresh and return the current permission state */
        suspend fun refresh() = manager.refreshState()
    }

    // ==================== RECORDER CREATION ====================

    /**
     * Creates a new [Recorder] instance for recording audio.
     * 
     * @param quality The audio quality preset (default: Standard)
     * @param device Optional specific input device to use
     * @return A new Recorder ready to start recording
     * @throws AudioError if session creation fails
     */
    suspend fun recorder(
        quality: AudioQuality = AudioQuality.Default,
        device: AudioDevice.Input? = null
    ): Recorder {
        return try {
            val session = SystemAudioSystem.createRecordingSession(device)
            Recorder(session, quality)
        } catch (e: Exception) {
            throw AudioError.from(e)
        }
    }

    /**
     * Creates a new [Recorder] with custom configuration.
     * 
     * @param config Configuration block for advanced options
     * @return A new Recorder ready to start recording
     * @throws AudioError if session creation fails
     */
    suspend fun recorder(config: RecorderConfig.() -> Unit): Recorder {
        val cfg = RecorderConfig().apply(config)
        return recorder(quality = cfg.quality, device = cfg.device)
    }

    // ==================== PLAYER CREATION ====================

    /**
     * Creates a new [Player] instance for audio playback.
     * 
     * @param device Optional specific output device to use
     * @return A new Player ready for playback
     * @throws AudioError if session creation fails
     */
    suspend fun player(device: AudioDevice.Output? = null): Player {
        return try {
            val session = SystemAudioSystem.createPlaybackSession(device)
            Player(session)
        } catch (e: Exception) {
            throw AudioError.from(e)
        }
    }

    /**
     * Creates a new [Player] with custom configuration.
     * 
     * @param config Configuration block for advanced options
     * @return A new Player ready for playback
     * @throws AudioError if session creation fails
     */
    suspend fun player(config: PlayerConfig.() -> Unit): Player {
        val cfg = PlayerConfig().apply(config)
        return player(device = cfg.device)
    }

    // ==================== CONVENIENCE METHODS ====================

    /**
     * Records audio for a specified duration.
     * 
     * This is a convenience method that handles session lifecycle automatically.
     * 
     * @param duration How long to record
     * @param quality The audio quality preset
     * @param device Optional specific input device
     * @return The recorded audio
     * @throws AudioError if recording fails
     */
    suspend fun record(
        duration: Duration,
        quality: AudioQuality = AudioQuality.Default,
        device: AudioDevice.Input? = null
    ): AudioRecording {
        return record(quality = quality, device = device) { recorder ->
            recorder.start()
            delay(duration)
            recorder.stop()
            recorder.getRecording() ?: throw AudioError.NoRecordingData()
        }
    }

    /**
     * Records audio with manual control via a lambda.
     * 
     * The recorder is automatically cleaned up when the block exits.
     * 
     * @param quality The audio quality preset
     * @param device Optional specific input device
     * @param block The recording logic
     * @return The result from the block
     * @throws AudioError if recording fails
     */
    suspend fun <T> record(
        quality: AudioQuality = AudioQuality.Default,
        device: AudioDevice.Input? = null,
        block: suspend (Recorder) -> T
    ): T {
        val recorder = recorder(quality = quality, device = device)
        return try {
            block(recorder)
        } catch (e: Exception) {
            throw AudioError.from(e)
        } finally {
            recorder.release()
        }
    }

    /**
     * Plays an [AudioRecording] to completion.
     * 
     * @param recording The audio to play
     * @param device Optional specific output device
     * @throws AudioError if playback fails
     */
    suspend fun play(
        recording: AudioRecording,
        device: AudioDevice.Output? = null
    ) {
        play(recording, device) { player ->
            player.start()
            player.awaitComplete()
        }
    }

    /**
     * Plays an [AudioRecording] with manual control via a lambda.
     * 
     * The player is automatically cleaned up when the block exits.
     * 
     * @param recording The audio to play
     * @param device Optional specific output device
     * @param block The playback control logic
     * @return The result from the block
     * @throws AudioError if playback fails
     */
    suspend fun <T> play(
        recording: AudioRecording,
        device: AudioDevice.Output? = null,
        block: suspend (Player) -> T
    ): T {
        val player = player(device)
        return try {
            player.load(recording)
            block(player)
        } catch (e: Exception) {
            throw AudioError.from(e)
        } finally {
            player.release()
        }
    }

    /**
     * Plays an [AudioFlow] to completion (legacy compatibility).
     * 
     * @param audioFlow The audio flow to play
     * @param device Optional specific output device
     * @throws AudioError if playback fails
     */
    suspend fun play(
        audioFlow: AudioFlow,
        device: AudioDevice.Output? = null
    ) {
        val player = player(device)
        try {
            player.loadAudioFlow(audioFlow)
            player.start()
            player.awaitComplete()
        } catch (e: Exception) {
            throw AudioError.from(e)
        } finally {
            player.release()
        }
    }
}

/**
 * Configuration for creating a Recorder.
 */
class RecorderConfig {
    /** Audio quality preset */
    var quality: AudioQuality = AudioQuality.Default

    /** Specific input device to use (null for default) */
    var device: AudioDevice.Input? = null
}

/**
 * Configuration for creating a Player.
 */
class PlayerConfig {
    /** Specific output device to use (null for default) */
    var device: AudioDevice.Output? = null
}
