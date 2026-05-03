package space.kodio.transcription.cloud

import io.ktor.client.HttpClient

internal actual fun createDefaultWhisperHttpClient(): HttpClient = HttpClient()
