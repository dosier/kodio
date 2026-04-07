package space.kodio.sample

import space.kodio.core.AudioFlow
import space.kodio.core.io.files.AudioFileFormat

expect suspend fun saveWavFile(audioDataFlow: AudioFlow)

expect suspend fun saveAudioWithFilePicker(
    audioFlow: AudioFlow,
    fileFormat: AudioFileFormat,
    suggestedName: String,
)