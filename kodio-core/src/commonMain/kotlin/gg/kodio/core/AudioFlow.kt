package gg.kodio.core

import kotlinx.coroutines.flow.Flow

class AudioFlow(
    val format: AudioFormat,
    data: Flow<ByteArray>
) : Flow<ByteArray> by data