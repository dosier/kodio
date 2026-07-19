package space.kodio.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises the JVM → Panama → K/N AudioQueue playback path used by the desktop sample.
 */
class NativeMacosPlaybackPanamaTest {

    private fun isMacOS(): Boolean =
        System.getProperty("os.name").lowercase().contains("mac")

    private fun sinePcm16Mono(sampleRate: Int, seconds: Double): ByteArray {
        val frames = (sampleRate * seconds).toInt()
        val out = ByteArray(frames * 2)
        for (f in 0 until frames) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * f / sampleRate) * 16000).toInt()
            out[f * 2] = (s and 0xFF).toByte()
            out[f * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun sinePcm24Stereo(sampleRate: Int, seconds: Double): ByteArray {
        val frames = (sampleRate * seconds).toInt()
        val out = ByteArray(frames * 6)
        for (f in 0 until frames) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * f / sampleRate) * 4_000_000).toInt() and 0xFFFFFF
            val o = f * 6
            val b0 = (s and 0xFF).toByte()
            val b1 = ((s ushr 8) and 0xFF).toByte()
            val b2 = ((s ushr 16) and 0xFF).toByte()
            out[o] = b0; out[o + 1] = b1; out[o + 2] = b2
            out[o + 3] = b0; out[o + 4] = b1; out[o + 5] = b2
        }
        return out
    }

    private fun play(format: AudioFormat, pcm: ByteArray): AudioPlaybackSession.State = runBlocking {
        val session = SystemAudioSystem.createPlaybackSession(null)
        try {
            val flow = AudioFlow(format, flowOf(pcm), knownByteCount = pcm.size.toLong())
            session.load(flow)
            session.play()
            withTimeout(15.seconds) {
                session.state.first {
                    it is AudioPlaybackSession.State.Finished ||
                        it is AudioPlaybackSession.State.Error ||
                        it is AudioPlaybackSession.State.Idle
                }
            }
        } finally {
            runCatching { session.stop() }
        }
    }

    @Test
    fun panama_playStandardPcm16Mono() {
        if (!isMacOS() || !NativeMacosAudioSystem.isAvailable || isJavaSoundForced()) {
            println("Skipping: need native macOS backend")
            return
        }
        assertTrue(SystemAudioSystem === NativeMacosAudioSystem)
        val fmt = AudioQuality.Standard.format
        val state = play(fmt, sinePcm16Mono(fmt.sampleRate, 0.35))
        println("standard panama -> $state")
        assertIs<AudioPlaybackSession.State.Finished>(state)
    }

    @Test
    fun panama_playLosslessPcm24Stereo() {
        if (!isMacOS() || !NativeMacosAudioSystem.isAvailable || isJavaSoundForced()) {
            println("Skipping: need native macOS backend")
            return
        }
        val fmt = AudioQuality.Lossless.format
        val state = play(fmt, sinePcm24Stereo(fmt.sampleRate, 0.25))
        println("lossless panama -> $state")
        if (state is AudioPlaybackSession.State.Error) {
            println("ERROR cause=${state.error}")
            state.error.printStackTrace()
        }
        assertIs<AudioPlaybackSession.State.Finished>(state)
    }

    @Test
    fun panama_playLossless_afterSeekToZero() {
        if (!isMacOS() || !NativeMacosAudioSystem.isAvailable || isJavaSoundForced()) {
            println("Skipping: need native macOS backend")
            return
        }
        val fmt = AudioQuality.Lossless.format
        val pcm = sinePcm24Stereo(fmt.sampleRate, 0.3)
        val state = runBlocking {
            val session = SystemAudioSystem.createPlaybackSession(null)
            try {
                session.load(AudioFlow(fmt, flowOf(pcm), pcm.size.toLong()))
                val seek = session.seekTo(kotlin.time.Duration.ZERO)
                println("seek before play -> $seek")
                session.play()
                withTimeout(15.seconds) {
                    session.state.first {
                        it is AudioPlaybackSession.State.Finished ||
                            it is AudioPlaybackSession.State.Error ||
                            it is AudioPlaybackSession.State.Idle
                    }
                }
            } finally {
                runCatching { session.stop() }
            }
        }
        println("lossless after seek -> $state")
        if (state is AudioPlaybackSession.State.Error) {
            println("ERROR=${state.error.message}")
            state.error.printStackTrace()
        }
        assertIs<AudioPlaybackSession.State.Finished>(state)
    }

    @Test
    fun panama_playLossless_chunkedFlow() {
        if (!isMacOS() || !NativeMacosAudioSystem.isAvailable || isJavaSoundForced()) return
        val fmt = AudioQuality.Lossless.format
        val pcm = sinePcm24Stereo(fmt.sampleRate, 0.4)
        // Many small chunks like live ingest / recording capture
        val chunkSize = 512 // not multiple of 6 — misaligned on purpose
        val chunks = pcm.toList().chunked(chunkSize).map { it.toByteArray() }
        val state = runBlocking {
            val session = SystemAudioSystem.createPlaybackSession(null)
            try {
                val audioFlow = AudioFlow(fmt, flow {
                    for (c in chunks) emit(c)
                }, pcm.size.toLong())
                session.load(audioFlow)
                session.play()
                withTimeout(20.seconds) {
                    session.state.first {
                        it is AudioPlaybackSession.State.Finished ||
                            it is AudioPlaybackSession.State.Error
                    }
                }
            } finally {
                runCatching { session.stop() }
            }
        }
        println("lossless chunked(${chunks.size} chunks size=$chunkSize) -> $state")
        if (state is AudioPlaybackSession.State.Error) {
            println("ERROR=${state.error}")
            state.error.printStackTrace()
        }
        assertIs<AudioPlaybackSession.State.Finished>(state)
    }
}
