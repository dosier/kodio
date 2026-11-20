package space.kodio.sample

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSProcessInfo
import space.kodio.core.AudioFormat
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.IntBitDepth
import space.kodio.core.Kodio
import space.kodio.core.Player
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import space.kodio.core.logging.KodioLogWriter
import space.kodio.core.logging.LogLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Scripted on-device check of record/playback timing, for testing on a real
 * iPhone without tapping through the UI. Enabled by launching with the
 * environment variable `KODIO_SELFTEST=1`; results are printed to stdout as
 * `SELFTEST ...` lines (visible with `devicectl device process launch --console`).
 */
internal fun selfTestRequested(): Boolean =
    NSProcessInfo.processInfo.environment["KODIO_SELFTEST"] != null

/** Mirrors Kodio logs to stdout (NSLog only reaches the device's system log). */
internal object StdoutLogWriter : KodioLogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        println("[$level] $tag: $message" + (throwable?.let { " ($it)" } ?: ""))
    }
}

internal fun launchDeviceSelfTest() {
    CoroutineScope(Dispatchers.Default).launch {
        delay(1.seconds) // let the UI come up first
        report("start")
        runCatching { runSelfTest() }
            .onFailure { report("CRASH $it") }
        report("done")
    }
}

private fun report(line: String) = println("SELFTEST $line")

private fun check(name: String, ok: Boolean, detail: String) =
    report("${if (ok) "PASS" else "FAIL"} $name: $detail")

private suspend fun runSelfTest() {
    // 1. Record from the default mic.
    val permission = Kodio.microphonePermission.refresh()
    report("mic permission=$permission")
    if (permission.toString() != "Granted") Kodio.microphonePermission.request()
    val mic = withTimeoutOrNull(15.seconds) { Kodio.record(duration = 4.seconds) }
    if (mic == null) {
        check("record", false, "timed out")
    } else {
        val bytes = mic.toByteArray()
        check(
            "record",
            bytes.isNotEmpty(),
            "format=${mic.format.sampleRate}Hz/${mic.format.channels} duration=${mic.duration} " +
                "bytes=${bytes.size} peak=${peak16(bytes)}",
        )
    }

    // 2. Playback timing on a known 3 s tone (deterministic, no mic dependency).
    val tone = tone(3.seconds)
    val toneDur = 3.seconds

    timed("play to end", expected = toneDur) { player ->
        player.start()
        delay(1.seconds)
        val at1 = player.position.value
        delay(1.seconds)
        val at2 = player.position.value
        // Position drives the UI progress bar; it must advance during playback.
        check("position advances", at1 > 600.milliseconds && at2 > at1 + 600.milliseconds, "at1s=$at1 at2s=$at2")
        player.awaitComplete()
    }

    timed("pause 1s then resume", expected = toneDur + 1.seconds) { player ->
        player.start()
        delay(1.seconds)
        player.pause()
        delay(1.seconds)
        player.resume()
        player.awaitComplete()
    }

    timed("seek to 0.5s while paused", expected = 1.seconds + 1.seconds + (toneDur - 500.milliseconds)) { player ->
        player.start()
        delay(1.seconds)
        player.pause()
        report("  seek while paused -> ${player.seekTo(500.milliseconds)}")
        delay(1.seconds)
        report("  position while paused=${player.position.value} state=${player.stateFlow.value}")
        player.resume()
        player.awaitComplete()
    }

    timed("seek back to 0 while playing", expected = 1.seconds + toneDur) { player ->
        player.start()
        delay(1.seconds)
        report("  seek while playing -> ${player.seekTo(Duration.ZERO)}")
        player.awaitComplete()
    }

    timed("replay after finish", expected = toneDur * 2) { player ->
        player.start()
        player.awaitComplete()
        player.start()
        player.awaitComplete()
    }

    // 3. Kodio.play helper: must not return (and release) before the audio ends.
    val t0 = TimeSource.Monotonic.markNow()
    withTimeoutOrNull(15.seconds) { Kodio.play(tone) }
    val took = t0.elapsedNow()
    check("Kodio.play(recording)", near(took, toneDur), "took=$took expected≈$toneDur")

}

private suspend fun timed(
    name: String,
    expected: Duration,
    block: suspend (Player) -> Unit,
) {
    val player = Kodio.player()
    try {
        player.load(tone(3.seconds))
        val t0 = TimeSource.Monotonic.markNow()
        val finished = withTimeoutOrNull(expected + 10.seconds) { block(player) } != null
        val took = t0.elapsedNow()
        check(
            name,
            finished && near(took, expected),
            "took=$took expected≈$expected finalState=${player.stateFlow.value} " +
                "finalPosition=${player.position.value}" + if (!finished) " (TIMED OUT)" else "",
        )
    } finally {
        player.release()
    }
}

/** Device drain adds a little output latency; allow -150 ms / +600 ms. */
private fun near(actual: Duration, expected: Duration): Boolean =
    actual >= expected - 150.milliseconds && actual <= expected + 600.milliseconds

private val toneFormat = AudioFormat(
    sampleRate = 48_000,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(
        bitDepth = IntBitDepth.Sixteen,
        endianness = Endianness.Little,
        layout = SampleLayout.Interleaved,
        signed = true,
    ),
)

private fun tone(length: Duration): AudioRecording {
    val frames = (toneFormat.sampleRate * length.inWholeMilliseconds / 1000).toInt()
    val pcm = ByteArray(frames * 2)
    for (i in 0 until frames) {
        val s = (sin(2 * PI * 440 * i / toneFormat.sampleRate) * 6000).toInt()
        pcm[i * 2] = (s and 0xFF).toByte()
        pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return AudioRecording.fromPcmBytes(toneFormat, pcm)
}

private fun peak16(bytes: ByteArray): Int {
    var peak = 0
    var i = 0
    while (i + 1 < bytes.size) {
        val s = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt()
        if (abs(s) > peak) peak = abs(s)
        i += 2
    }
    return peak
}
