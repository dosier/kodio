package space.kodio.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioFormat as AndroidAudioFormat

internal class AndroidAudioPlaybackSession(
    private val context: Context,
    private val device: AudioDevice.Output
) : BaseAudioPlaybackSession() {

    private var audioTrack: AudioTrack? = null

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        val minBufferSize = AudioTrack.getMinBufferSize(
            /* sampleRateInHz = */ format.sampleRate,
            /* channelConfig = */ format.channels.toAndroidChannelOutMask(),
            /* audioFormat = */ format.bitDepth.toAndroidFormatEncoding()
        )
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE)
            error("$format is not supported by the device")
        if (minBufferSize == AudioRecord.ERROR)
            error("Failed to get min buffer size")
        val playbackBufferSize = minBufferSize * 8
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AndroidAudioFormat.Builder()
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(format.channels.toAndroidChannelOutMask())
                    .setEncoding(format.bitDepth.toAndroidFormatEncoding())
                    .build()
            )
            .setBufferSizeInBytes(playbackBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val selectedDevice = devices.firstOrNull { it.id.toString() == device.id }
        if (selectedDevice != null)
            audioTrack.preferredDevice = selectedDevice
        audioTrack.playbackRate = format.sampleRate
        audioTrack.setVolume(AudioTrack.getMaxVolume())
        this.audioTrack = audioTrack
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        val audioTrack = audioTrack ?: return
        audioTrack.play()
        audioFlow.collect { chunk ->
            audioTrack.write(chunk, 0, chunk.size)
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