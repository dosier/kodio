actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {
    override suspend fun listInputDevices(): List<AudioDevice.Input> = emptyList()
    override suspend fun listOutputDevices(): List<AudioDevice.Output> = emptyList()
    override fun createRecordingSession(device: AudioDevice.Input): RecordingSession {
        TODO("Not yet implemented")
    }
    override fun createPlaybackSession(device: AudioDevice.Output): PlaybackSession {
        TODO("Not yet implemented")
    }
}