package space.kodio.sample

import space.kodio.core.AudioFlow
import space.kodio.core.io.files.AudioFileFormat

actual suspend fun saveWavFile(audioDataFlow: AudioFlow): Unit =
    TODO("Not yet implemented for MacOS")

actual suspend fun saveAudioWithFilePicker(
    audioFlow: AudioFlow,
    fileFormat: AudioFileFormat,
    suggestedName: String,
): Unit = TODO("Not yet implemented for MacOS")