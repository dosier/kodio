# 0.1.4 Pre-release manual test checklist

Run through these on each platform before tagging `v0.1.4`. Each section
links the GitHub issue it covers. The Kodio sample app
(`./gradlew :kodio-sample-app:run` for desktop, `iosApp`/Android module for
mobile, `:kodio-sample-app:jsBrowserDevelopmentRun` for the browser) is the
test rig for most items.

## Recording / pause-resume ([#22](https://github.com/dosier/kodio/issues/22), [#24](https://github.com/dosier/kodio/issues/24))

The Recording tab in the sample app exposes a Record / Pause / Resume / Stop
button row. Use it to verify:

- [ ] **JVM (desktop sample, JavaSound path with `-Dkodio.useJavaSound=true`)**:
      record ~3s, press Pause, wait ~2s, press Resume, record another ~3s,
      Stop. Play back the resulting clip — it should be one continuous
      recording with no glitch / silence at the pause boundary.
- [ ] **JVM (desktop sample, native macOS path — default on macOS)**: same
      flow as above. Verify the FFI bridge no longer throws
      `UnsupportedOperationException: Pause is not supported by this
      AudioRecordingSession` when Pause is pressed.
- [ ] **Android (physical device)**: same flow as JVM. Additionally select
      `AudioQuality.Voice` before recording and confirm the produced clip's
      sample rate is 16 kHz (not 48 kHz) — closes [#22](https://github.com/dosier/kodio/issues/22).
- [ ] **iOS simulator**: same flow as JVM. AVAudioEngine should not crash
      on resume (regression check for the `-10868` we previously fought).
- [ ] **macOS native (Kotlin/Native sample binary)**: same flow as JVM.
      AudioQueue should resume without dropping the buffer.
- [ ] **Web (Chrome)**: same flow as JVM. The `AudioWorklet` reconnects
      cleanly on Resume; recorded audio still includes the chunks captured
      before pause.
- [ ] **Footgun guard**: on any platform, record → Stop → press Record
      again. Expect the app to surface an
      `IllegalStateException: Recorder has already produced a recording...`
      pointing at `reset()` / `pause` / `concat` (closes [#24](https://github.com/dosier/kodio/issues/24)
      short-term fix). Pressing Record again after the error should still
      work because `RecorderState` `reset()`s on the next `start()`.
- [ ] **Selector gating**: while recording or paused, the Input Device and
      Audio Quality selectors are disabled.

## Stitching ([#24](https://github.com/dosier/kodio/issues/24) follow-up)

The recordings list in the Recording tab now has per-item checkboxes and a
"Stitch selected (N)" action.

- [ ] Record 2–3 short clips (≤2s each), tick all of them, press
      "Stitch selected". A new combined recording appears in the list. Play
      it: audio runs end-to-end with no audible artifacts at the seams.
- [ ] Record clips at different qualities (e.g. one at `Voice`, one at
      `High`), stitch them, verify the stitched clip plays correctly. The
      stitched format should match the first selected clip's format
      (`AudioRecording.concat` runs the others through `convertAudio`).
- [ ] Try stitching a single recording (button disabled when fewer than 2
      are selected — verify it stays disabled).

## Device selection ([#3](https://github.com/dosier/kodio/issues/3))

- [ ] **Web (Chrome)**: open the Recording tab, pick a non-default input
      device from the dropdown, then press Record. The recorder should
      surface `AudioError.DeviceSelectionUnsupported` (visible in the
      "Error: ..." status line) instead of silently ignoring the selection.
      Re-selecting "System Default" should let recording proceed.
- [ ] **macOS / JVM**: pick a non-default input. The recorded audio comes
      from that device (e.g. plug in headphones with a mic, pick them, see
      that the visible waveform reacts to the headphone mic).

## Web transcription via proxy ([#16](https://github.com/dosier/kodio/issues/16))

- [ ] Stand up the Ktor server proxy from
      [`kodio-docs/topics/Transcription-Web.md`](kodio-docs/topics/Transcription-Web.md)
      and confirm it forwards multipart `POST` requests to OpenAI.
- [ ] In a quick scratch JS/WasmJS app (or a temporary tweak to
      `TranscriptionShowcase`), instantiate
      `OpenAIWhisperEngine(apiKey = "", endpointUrl = "http://localhost:8080/api/transcribe")`
      and run a live recording transcription. Verify chunks land at the
      proxy server and transcription text returns. No CORS errors should
      appear in the browser console.

## Ktor extension ([#6](https://github.com/dosier/kodio/issues/6))

- [ ] `./gradlew :kodio-extensions:ktor:jvmTest` — both
      `AudioFlowKtorWebSocketTest` cases pass.
- [ ] In a scratch project (or repl), run a small Ktor server with
      `WebSockets` installed, plug `WebSocketSession.receiveAudioFlow()`
      into a `webSocket("/audio") { ... }` route, and call
      `AudioFlow.sendOverWebSocket(client, "ws://localhost:8080/audio")`
      from a JVM client. The byte stream collected on the server should
      match what the client sent (verify with a hash if paranoid).

## JVM warmup ([#5](https://github.com/dosier/kodio/issues/5))

- [ ] On macOS desktop sample, with `kodio.useJavaSound=true` (forces the
      JavaSound path), record a short clip starting with a clap. The clap
      should be audible without a leading ~200 ms silence.
- [ ] Re-run with `-Dkodio.jvm.recording.warmupMillis=0` and confirm the
      symptom returns (silence at the beginning) — this proves the warmup
      drain is doing the work.
- [ ] Re-run with `-Dkodio.jvm.recording.warmupMillis=400` and confirm
      the user audio is not eaten (clip starts cleanly with the clap).

## Android emulator note ([#1](https://github.com/dosier/kodio/issues/1))

- [ ] On an Android emulator (macOS host, M-class), record at the default
      `AudioQuality.Default`. Note the noise level.
- [ ] Switch to `AudioQuality.High` and re-record. Noise should be
      noticeably reduced because the emulator no longer needs to resample.
- [ ] Restart the emulator with `-audio-backend coreaudio`
      (`emulator -avd <name> -audio-backend coreaudio`) and re-test.
      Confirm the docs steps in `Platform-Setup.md#android-emulator` match
      observed behaviour.

## CI matrix ([#11](https://github.com/dosier/kodio/issues/11), [#12](https://github.com/dosier/kodio/issues/12), [#13](https://github.com/dosier/kodio/issues/13), [#14](https://github.com/dosier/kodio/issues/14), [#15](https://github.com/dosier/kodio/issues/15))

- [ ] Push a no-op branch (or open a small PR) and confirm all 5 matrix
      jobs run: `linux-jvm`, `android-unit`, `web`, `macos-native`,
      `ios-sim`.
- [ ] All 5 cells are green (or the failing cell is a pre-existing flake
      tracked elsewhere, not a regression introduced by this release).

## Versioning / artifact

- [ ] Bump `kodio.version` in `gradle.properties` from
      `0.1.4-SNAPSHOT` to `0.1.4`.
- [ ] Commit the version bump (and any other uncommitted working-tree
      changes you intend to ship — `git status` should be clean afterwards).
- [ ] `git push origin master`.
- [ ] `git tag v0.1.4 && git push origin v0.1.4` — `publish.yml` runs CI,
      then publishes to Maven Central.
- [ ] Once `publish` is green, verify the artifacts appear on Maven
      Central: `space.kodio:core:0.1.4`,
      `space.kodio.extensions:compose:0.1.4`,
      `space.kodio.extensions:compose-material3:0.1.4`,
      `space.kodio.extensions:transcription:0.1.4`,
      `space.kodio.extensions:ktor:0.1.4`.
- [ ] Promote the auto-generated GitHub Release draft for `v0.1.4` to a
      published release; mention the closed issues
      (#1, #3, #5, #11, #12, #13, #14, #15, #16, #22, #24) and link to the
      Ktor extension docs.
