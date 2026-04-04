package space.kodio.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioFormat as AndroidAudioFormat
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@SuppressLint("MissingPermission")
class AndroidAudioRecordingSession(
    private val context: Context,
    private val requestedDevice: AudioDevice.Input?,
    private val format: AudioFormat = DefaultAndroidRecordingAudioFormat // e.g., 48k/Int16/mono/interleaved
) : BaseAudioRecordingSession() {

    private var audioRecord: AudioRecord? = null
    private lateinit var preparedFormat: AudioFormat
    private var androidEncoding: Int = AndroidAudioFormat.ENCODING_INVALID
    private var androidChannelMask: Int = 0

    override suspend fun prepareRecording(): AudioFormat {
        val effectiveFormat = tryPrepareWithFormat(format)
            ?: if (format != DefaultAndroidRecordingAudioFormat) {
                tryPrepareWithFormat(DefaultAndroidRecordingAudioFormat)
            } else null
            ?: error("No supported audio format found for this device")

        return effectiveFormat
    }

    private fun tryPrepareWithFormat(fmt: AudioFormat): AudioFormat? {
        val interleavedFmt = fmt.asInterleaved() ?: return null

        val encoding = interleavedFmt.toAndroidEncoding()
        if (encoding == AndroidAudioFormat.ENCODING_INVALID) return null

        val channelMask = interleavedFmt.channels.toAndroidChannelInMask()
        val minBufferSize = AudioRecord.getMinBufferSize(interleavedFmt.sampleRate, channelMask, encoding)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR)
            return null

        val bufferBytes = minBufferSize.coerceAtLeast(4096) * 2

        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(interleavedFmt.toAndroidInputAudioFormat())
            .setBufferSizeInBytes(bufferBytes)
            .build()

        if (requestedDevice != null) setPreferredDevice(context, requestedDevice, record)

        record.startRecording()
        audioRecord = record
        androidEncoding = encoding
        androidChannelMask = channelMask
        preparedFormat = interleavedFmt
        return interleavedFmt
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val record = audioRecord ?: return

        when (preparedFormat.encoding) {
            is SampleEncoding.PcmInt -> {
                // Read raw bytes directly
                val readBuf = ByteArray(record.bufferSizeInFrames.coerceAtLeast(1024) * preparedFormat.bytesPerFrame)
                while (coroutineContext.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val n = record.read(readBuf, 0, readBuf.size)
                    if (n > 0) channel.send(readBuf.copyOf(n))
                }
            }
            is SampleEncoding.PcmFloat -> {
                // Read float frames, convert to LE bytes for pipeline compatibility
                val chanCount = preparedFormat.channels.count
                // Choose a reasonable frame chunk; AudioRecord doesn't expose a direct "min float frames"
                val framesPerChunk = (record.bufferSizeInFrames.coerceAtLeast(1024) / 2).coerceAtLeast(256)
                val floatBuf = FloatArray(framesPerChunk * chanCount)

                while (coroutineContext.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val framesRead = record.read(floatBuf, 0, floatBuf.size, AudioRecord.READ_BLOCKING)
                    if (framesRead > 0) {
                        val byteChunk = floatArrayToLeBytes(floatBuf, framesRead)
                        channel.send(byteChunk)
                    }
                }
            }
        }
    }

    override fun cleanup() {
        audioRecord?.let { ar ->
            runCatching {
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    ar.stop()
                }
            }
            ar.release()
        }
        audioRecord = null
    }
}

/* -------------------- Helpers -------------------- */

private fun AudioFormat.asInterleaved(): AudioFormat? = when (val e = encoding) {
    is SampleEncoding.PcmInt ->
        if (e.layout == SampleLayout.Interleaved) this
        else copy(encoding = e.copy(layout = SampleLayout.Interleaved))
    is SampleEncoding.PcmFloat ->
        if (e.layout == SampleLayout.Interleaved) this
        else copy(encoding = e.copy(layout = SampleLayout.Interleaved))
}

/** Convert FloatArray (interleaved frames) to little-endian IEEE-754 bytes for the first `framesRead` frames. */
private fun floatArrayToLeBytes(src: FloatArray, framesRead: Int): ByteArray {
    val samples = framesRead // already includes channels in our read size
    val out = ByteArray(samples * 4)
    var o = 0
    var i = 0
    while (i < samples) {
        val bits = java.lang.Float.floatToRawIntBits(src[i].coerceIn(-1f, 1f))
        out[o++] = (bits and 0xFF).toByte()
        out[o++] = ((bits ushr 8) and 0xFF).toByte()
        out[o++] = ((bits ushr 16) and 0xFF).toByte()
        out[o++] = ((bits ushr 24) and 0xFF).toByte()
        i++
    }
    return out
}

private fun setPreferredDevice(context: Context, requestedDevice: AudioDevice.Input, record: AudioRecord) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
    val selectedDevice = devices.firstOrNull { it.id.toString() == requestedDevice.id }
    if (selectedDevice != null) record.preferredDevice = selectedDevice
}