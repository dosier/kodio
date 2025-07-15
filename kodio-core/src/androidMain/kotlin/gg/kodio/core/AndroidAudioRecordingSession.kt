package gg.kodio.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@SuppressLint("MissingPermission")
class AndroidAudioRecordingSession(
    private val context: Context,
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultAndroidRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private var audioRecord: AudioRecord? = null

    override suspend fun prepareRecording(): AudioFormat {
        val bufferSize = AudioRecord.getMinBufferSize(
            /* sampleRateInHz = */ format.sampleRate,
            /* channelConfig = */ format.channels.toAndroidChannelInMask(),
            /* audioFormat = */ format.bitDepth.toAndroidFormatEncoding()
        )
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format.toAndroidAudioFormat())
            .setBufferSizeInBytes(bufferSize)
            .build()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val selectedDevice = devices.firstOrNull { it.id.toString() == device.id }
        if (selectedDevice != null)
            record.preferredDevice = selectedDevice
        this.audioRecord = record
        record.startRecording()
        return format
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val record = audioRecord ?: return
        val buffer = ByteArray(record.bufferSizeInFrames / 5) // Read in chunks

        while (coroutineContext.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val bytesRead = record.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                channel.send(buffer.copyOf(bytesRead))
            }
        }
    }

    override fun cleanup() {
        audioRecord?.let {
            if (it.state == AudioRecord.STATE_INITIALIZED) {
                it.stop()
                it.release()
            }
        }
        audioRecord = null
    }
}