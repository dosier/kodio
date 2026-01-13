package space.kodio.transcription.ondevice

import space.kodio.transcription.TranscriptionEngine

/**
 * Available Whisper model sizes for local inference.
 * 
 * Smaller models are faster but less accurate.
 * Larger models are more accurate but require more memory and processing power.
 */
enum class WhisperModel(
    val modelName: String,
    val approximateSizeMB: Int
) {
    /** Smallest model, fastest inference (~39MB) */
    TINY("tiny", 39),
    
    /** Small model, good balance (~74MB) */
    BASE("base", 74),
    
    /** Medium-small model (~244MB) */
    SMALL("small", 244),
    
    /** Medium model, good accuracy (~769MB) */
    MEDIUM("medium", 769),
    
    /** Largest model, best accuracy (~1550MB) */
    LARGE("large", 1550);
    
    /** English-only variants (faster for English) */
    val englishVariant: String get() = "${modelName}.en"
}

/**
 * Factory for creating local Whisper-based transcription engines.
 * 
 * Uses whisper.cpp or platform-equivalent for on-device inference.
 * Models must be downloaded before use.
 * 
 * ## Example
 * ```kotlin
 * val engine = WhisperLocalEngine.create(WhisperModel.BASE)
 * 
 * // Check if model needs to be downloaded
 * if (!engine.isModelLoaded) {
 *     engine.downloadModel() // or provide your own model file
 * }
 * 
 * if (engine.isAvailable) {
 *     audioFlow.transcribe(engine).collect { result ->
 *         println(result)
 *     }
 * }
 * ```
 * 
 * Note: Local Whisper support requires platform-specific native bindings
 * and is not yet implemented on all platforms.
 */
expect object WhisperLocalEngine {
    /**
     * Creates a local Whisper transcription engine.
     * 
     * @param model The Whisper model size to use
     * @param modelPath Optional path to a custom model file
     * @return A [TranscriptionEngine] using local Whisper inference,
     *         or a stub engine if not available on this platform.
     */
    fun create(
        model: WhisperModel = WhisperModel.BASE,
        modelPath: String? = null
    ): TranscriptionEngine
    
    /**
     * Whether local Whisper inference is supported on this platform.
     */
    val isSupported: Boolean
    
    /**
     * Downloads the specified Whisper model.
     * 
     * @param model The model to download
     * @param onProgress Callback for download progress (0.0 to 1.0)
     * @return True if download succeeded
     */
    suspend fun downloadModel(
        model: WhisperModel,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean
    
    /**
     * Checks if a model is already downloaded.
     */
    fun isModelDownloaded(model: WhisperModel): Boolean
}

