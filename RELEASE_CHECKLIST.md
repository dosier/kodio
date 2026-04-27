# 0.1.5 Pre-release manual test checklist

Run through these before tagging `v0.1.5`. This release ships the Android
compatibility fix from [PR #23](https://github.com/dosier/kodio/pull/23), which
keeps the Compose waveform code off `List.removeLast()` for Android API levels
below 35.

## Compose waveform compatibility

- [ ] `./gradlew :kodio-extensions:compose:jvmTest` passes.
- [ ] Run the sample app on an Android device or emulator with API < 35 and
      open the recording waveform. The waveform should render while recording
      without a `NoSuchMethodError` for `removeLast`.
- [ ] Repeat the same recording waveform smoke test on the desktop sample to
      verify the shared Compose path still behaves as expected.

## CI matrix

- [ ] Push the release commit to `master` and confirm the full CI matrix is
      green: `linux-jvm`, `android-unit`, `web`, `macos-native`, `ios-sim`.

## Versioning / artifact

- [ ] Bump `kodio.version` in `gradle.properties` to `0.1.5`.
- [ ] Update README and Writerside dependency examples to `0.1.5`.
- [ ] Commit the version bump and documentation updates.
- [ ] `git push origin master`.
- [ ] `git tag v0.1.5 && git push origin v0.1.5` — `publish.yml` runs CI,
      then publishes to Maven Central.
- [ ] Once `publish` is green, verify the artifacts appear on Maven Central:
      `space.kodio:core:0.1.5`,
      `space.kodio.extensions:compose:0.1.5`,
      `space.kodio.extensions:compose-material3:0.1.5`,
      `space.kodio.extensions:transcription:0.1.5`,
      `space.kodio.extensions:ktor:0.1.5`.
- [ ] Confirm the GitHub Release for `v0.1.5` mentions PR #23 and the Android
      API < 35 Compose waveform compatibility fix.
