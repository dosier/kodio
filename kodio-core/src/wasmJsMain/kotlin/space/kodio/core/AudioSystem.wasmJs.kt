package space.kodio.core

/**
 * JS implementation of the AudioSystem using the Web Audio API.
 * This requires a secure context (HTTPS) to function in a browser.
 */
actual val SystemAudioSystem: AudioSystem = object : WebAudioSystem() {

}
