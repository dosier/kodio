package space.kodio.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioFormat as AndroidAudioFormat

internal class AndroidAudioPlaybackSession(
    private val context: Context,
    private val requestedDevice: AudioDevice.Output?
) : BaseAudioPlaybackSession() {

    private var audioTrack: AudioTrack? = null
    private lateinit var preparedFormat: AudioFormat
    private var androidEncoding: Int = AndroidAudioFormat.ENCODING_INVALID
    private var androidChannelMask: Int = 0

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        ensureInterleaved(format) // AudioTrack expects interleaved frames

        // Derive Android constants
        androidChannelMask = format.channels.toAndroidChannelOutMask()
        androidEncoding = format.toAndroidEncoding()

        if (androidEncoding == AndroidAudioFormat.ENCODING_INVALID)
            error("$format is not supported by the device")

        // Min buffer size
        val minBufferSize = AudioTrack.getMinBufferSize(
            /* sampleRateInHz = */ format.sampleRate,
            /* channelConfig  = */ androidChannelMask,
            /* audioFormat    = */ androidEncoding
        )
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) error("$format is not supported by the device")
        if (minBufferSize == AudioRecord.ERROR) error("Failed to get min buffer size")

        val playbackBufferSize = minBufferSize * 4 // a bit of headroom
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

        // Keep old behavior: align playback rate to stream sample rate
        // (For modern apps, PlaybackParams is preferred; this preserves your logic.)
        track.playbackRate = format.sampleRate
        track.setVolume(AudioTrack.getMaxVolume())

        this.audioTrack = track
        this.preparedFormat = format
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        val track = audioTrack ?: return
        track.play()

        when (preparedFormat.encoding) {
            is SampleEncoding.PcmInt -> {
                // Bytes are already in target PCM-int format; write directly.
                audioFlow.collect { chunk ->
                    // For 24-bit packed/32-bit, write(byte[]) also works on modern APIs.
                    track.write(chunk, 0, chunk.size)
                }
            }
            is SampleEncoding.PcmFloat -> {
                // Convert LE IEEE-754 bytes to float[] and use float write().
                // If upstream already produces float[], you can adapt the flow to emit float[] instead.
                require(androidEncoding == AndroidAudioFormat.ENCODING_PCM_FLOAT)
                audioFlow.collect { chunk ->
                    val floats = bytesToFloatArrayLE(chunk)
                    // WRITE_BLOCKING to keep behavior similar to your original
                    track.write(floats, 0, floats.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    override fun onPause() {
        audioTrack?.pause()
    }

    override fun onResume() {
        audioTrack?.play()
    }

    override fun onStop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

/* -------------------- Helpers used above -------------------- */

private fun ensureInterleaved(fmt: AudioFormat) {
    val ok = when (val e = fmt.encoding) {
        is SampleEncoding.PcmInt   -> e.layout == SampleLayout.Interleaved
        is SampleEncoding.PcmFloat -> e.layout == SampleLayout.Interleaved
    }
    require(ok) { "Android AudioTrack requires interleaved frames." }
}


private fun setPreferredDevice(context: Context, requestedDevice: AudioDevice.Output, audioTrack: AudioTrack) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val selectedDevice = devices.firstOrNull { it.id.toString() == requestedDevice.id }
    if (selectedDevice != null) audioTrack.preferredDevice = selectedDevice
}

/** Convert little-endian IEEE-754 Float32 bytes to a FloatArray. */
private fun bytesToFloatArrayLE(bytes: ByteArray): FloatArray {
    require(bytes.size % 4 == 0) { "PCM Float32 byte length must be multiple of 4." }
    val out = FloatArray(bytes.size / 4)
    var i = 0
    var j = 0
    while (i < bytes.size) {
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