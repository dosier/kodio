package space.kodio.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioFormat as AndroidAudioFormat
import space.kodio.core.transport.AudioSink
import space.kodio.core.transport.TransportController
import space.kodio.core.util.namedLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = namedLogger("AndroidPlayback")

/**
 * Android [AudioTrack] (MODE_STREAM) fed by [PlaybackBuffer] transport.
 *
 * Seek requires pause → flush → cursor → play; [AudioTrack.flush] is a no-op while
 * playing. Position uses unsigned 32-bit head delta from the post-flush baseline
 * (head resets on flush/stop, unlike JavaSound).
 */
internal class AndroidAudioPlaybackSession(
    private val context: Context,
    private val requestedDevice: AudioDevice.Output?
) : BaseAudioPlaybackSession() {

    private var audioTrack: AudioTrack? = null
    private lateinit var preparedFormat: AudioFormat
    private var androidEncoding: Int = AndroidAudioFormat.ENCODING_INVALID
    private var androidChannelMask: Int = 0

    /** Unsigned 32-bit playback head captured at last seek/flush baseline. */
    private var headBaseline: Long = 0L

    override fun useTransportPlayback(): Boolean = true

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        ensureInterleaved(format)

        androidChannelMask = format.channels.toAndroidChannelOutMask()
        androidEncoding = format.toAndroidEncoding()
        if (androidEncoding == AndroidAudioFormat.ENCODING_INVALID) {
            error("$format is not supported by the device")
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            format.sampleRate,
            androidChannelMask,
            androidEncoding,
        )
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) error("$format is not supported by the device")
        if (minBufferSize == AudioRecord.ERROR) error("Failed to get min buffer size")

        val bpf = format.bytesPerFrame
        val rawPlaybackBufferSize = minBufferSize * 4
        val playbackBufferSize = ((rawPlaybackBufferSize + bpf - 1) / bpf) * bpf
        releaseTrack()

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AndroidAudioFormat.Builder()
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(androidChannelMask)
                    .setEncoding(@SuppressLint("WrongConstant") androidEncoding)
                    .build()
            )
            .setBufferSizeInBytes(playbackBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (requestedDevice != null) setPreferredDevice(context, requestedDevice, track)

        track.playbackRate = format.sampleRate
        track.setVolume(AudioTrack.getMaxVolume())
        track.play()

        audioTrack = track
        preparedFormat = format
        headBaseline = unsignedHead(track)
        logger.debug { "preparePlayback: AudioTrack opened format=$format" }
        return format
    }

    override fun createAudioSink(format: AudioFormat): AudioSink = AndroidTrackSink()

    @Deprecated("Transport playback", level = DeprecationLevel.HIDDEN)
    override suspend fun playBlocking(audioFlow: AudioFlow) {
        error("AndroidAudioPlaybackSession uses transport playback")
    }

    override fun readDeviceMediaPosition(
        mediaBase: Duration,
        transport: TransportController,
    ): Duration {
        val track = audioTrack ?: return mediaBase
        val delta = unsignedDelta(unsignedHead(track), headBaseline)
        val rate = preparedFormat.sampleRate
        if (rate <= 0) return mediaBase
        return mediaBase + (delta.toDouble() / rate).seconds
    }

    override fun onTransportSeekApplied(position: Duration) {
        // flush already ran via sink; refresh baseline after pause→flush.
        audioTrack?.let { headBaseline = unsignedHead(it) }
        logger.debug { "onTransportSeekApplied pos=$position headBaseline=$headBaseline" }
    }

    override fun onTransportPaused() {
        audioTrack?.pause()
    }

    override fun onTransportResumed() {
        audioTrack?.play()
    }

    override fun onTransportStopped() {
        releaseTrack()
    }

    override suspend fun ensureDeviceReadyForTransport(format: AudioFormat): AudioFormat {
        val track = audioTrack
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING &&
                state.value is AudioPlaybackSession.State.Playing
            ) {
                track.play()
            }
            return preparedFormat
        }
        return preparePlayback(format)
    }

    override fun onPause() = onTransportPaused()
    override fun onResume() = onTransportResumed()
    override fun onStop() = onTransportStopped()

    private fun releaseTrack() {
        val track = audioTrack ?: return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
        audioTrack = null
        headBaseline = 0L
    }

    private inner class AndroidTrackSink : AudioSink {
        override fun write(
            data: ByteArray,
            offset: Int,
            length: Int,
            seekEpoch: Long,
            mediaByteOffset: Long,
        ) {
            val track = audioTrack ?: return
            fun epochStale(): Boolean {
                val transport = currentTransport()
                return transport != null && transport.seekEpoch != seekEpoch
            }
            when (preparedFormat.encoding) {
                is SampleEncoding.PcmInt -> {
                    var written = 0
                    while (written < length) {
                        if (epochStale()) return
                        val n = track.write(data, offset + written, length - written)
                        if (n <= 0) break
                        written += n
                    }
                }
                is SampleEncoding.PcmFloat -> {
                    if (epochStale()) return
                    require(androidEncoding == AndroidAudioFormat.ENCODING_PCM_FLOAT)
                    val floats = bytesToFloatArrayLE(data, offset, length)
                    if (epochStale()) return
                    track.write(floats, 0, floats.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }

        override fun flush(seekEpoch: Long) {
            val track = audioTrack ?: return
            // REQUIRED: flush is a no-op unless paused/stopped (MODE_STREAM).
            val wasPlaying = track.playState == AudioTrack.PLAYSTATE_PLAYING
            runCatching { track.pause() }
            runCatching { track.flush() }
            headBaseline = unsignedHead(track)
            if (wasPlaying && state.value is AudioPlaybackSession.State.Playing) {
                runCatching { track.play() }
            }
            logger.debug { "AndroidTrackSink.flush epoch=$seekEpoch wasPlaying=$wasPlaying" }
        }
    }
}

/* -------------------- Helpers -------------------- */

internal fun unsignedHead(track: AudioTrack): Long =
    track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

/** Unsigned 32-bit wrap-safe delta (now - baseline). */
internal fun unsignedDelta(now: Long, baseline: Long): Long {
    val a = now and 0xFFFF_FFFFL
    val b = baseline and 0xFFFF_FFFFL
    return (a - b) and 0xFFFF_FFFFL
}

private fun ensureInterleaved(fmt: AudioFormat) {
    val ok = when (val e = fmt.encoding) {
        is SampleEncoding.PcmInt -> e.layout == SampleLayout.Interleaved
        is SampleEncoding.PcmFloat -> e.layout == SampleLayout.Interleaved
    }
    require(ok) { "Android AudioTrack requires interleaved frames." }
}

private fun setPreferredDevice(
    context: Context,
    requestedDevice: AudioDevice.Output,
    audioTrack: AudioTrack,
) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val selectedDevice = devices.firstOrNull { it.id.toString() == requestedDevice.id }
    if (selectedDevice != null) audioTrack.preferredDevice = selectedDevice
}

private fun bytesToFloatArrayLE(bytes: ByteArray, offset: Int, length: Int): FloatArray {
    require(length % 4 == 0) { "PCM Float32 byte length must be multiple of 4." }
    val out = FloatArray(length / 4)
    var i = offset
    var j = 0
    val end = offset + length
    while (i < end) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = bytes[i + 1].toInt() and 0xFF
        val b2 = bytes[i + 2].toInt() and 0xFF
        val b3 = bytes[i + 3].toInt() and 0xFF
        val bits = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        out[j++] = Float.fromBits(bits)
        i += 4
    }
    return out
}
