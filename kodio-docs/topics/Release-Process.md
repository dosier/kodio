[//]: # (title: Release Process)

<show-structure for="chapter" depth="2"/>

Kodio releases are tag-driven and automated via GitHub Actions. The full step-by-step checklist lives in the repository root:

**[RELEASE_CHECKLIST.md](https://github.com/dosier/kodio/blob/master/RELEASE_CHECKLIST.md)**

## Summary

1. Verify CI is green on `master` (full matrix, not JVM only).
2. Bump version in `gradle.properties`, `kodio-docs/v.list`, and `README.md`.
3. Commit and push the version bump to `master`.
4. Create and push a `vX.Y.Z` tag.
5. Monitor `publish.yml` (CI matrix, GitHub Release, Maven Central publish).
6. Verify all five artifacts on Maven Central.

## Published Artifacts

| Module | Maven Coordinates |
|--------|-------------------|
| Core | `space.kodio:core` |
| Compose | `space.kodio.extensions:compose` |
| Compose Material3 | `space.kodio.extensions:compose-material3` |
| Transcription | `space.kodio.extensions:transcription` |
| Ktor | `space.kodio.extensions:ktor` |

## CI Matrix

The publish workflow reuses `.github/workflows/gradle.yml`, which runs six matrix jobs:

| Job ID | Gradle tasks |
|--------|--------------|
| `linux-jvm` | `jvmTest` |
| `android-unit` | `testAndroidHostTest` |
| `web` | `jsBrowserTest wasmJsBrowserTest` |
| `macos-native` | `macosArm64Test` |
| `ios-sim` | `iosSimulatorArm64Test` |
| `compile-samples` | `:kodio-sample-app:compileKotlinDesktop :kodio-sample-app:compileAndroidMain :kodio-benchmark:compileKotlinJvm` |

## Changelog

- **Release Drafter** drafts notes on every push to `master` (preview only).
- **GitHub auto-generated notes** produce the actual release changelog when the tag is pushed.

## See Also

<seealso style="cards">
    <category ref="getting-started">
        <a href="Installation.md">Installation</a>
    </category>
    <category ref="migration">
        <a href="Migration.md">Migration</a>
    </category>
</seealso>
