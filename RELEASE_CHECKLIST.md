# Release Checklist

Single source of truth for publishing a new Kodio release. Follow these steps in order.

## Overview

Releases are tag-driven. Pushing a `v*` tag to `master` triggers `.github/workflows/publish.yml`, which:

1. Runs the full CI matrix (`.github/workflows/gradle.yml`)
2. Creates a GitHub Release with auto-generated changelog
3. Publishes all library artifacts to Maven Central

## Prerequisites

### Required GitHub Secrets

| Secret | Purpose |
|--------|---------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Central Portal username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype Central Portal password/token |
| `SIGNING_KEY_ID` | GPG key ID for artifact signing |
| `SIGNING_PASSWORD` | GPG key passphrase |
| `GPG_KEY_CONTENTS` | Base64-encoded GPG private key |

### Local Tools

- Git with push access to `master`
- GitHub CLI (`gh`) for monitoring workflows and verifying releases

## Pre-release

- [ ] Confirm CI is green on `master`:
      `gh run list --branch master --limit 3`
- [ ] Run any release-specific manual smoke tests (sample apps, platform checks).
## Version Bump

Update all version references to `X.Y.Z`:

- [ ] `gradle.properties`: set `kodio.version=X.Y.Z`
- [ ] `kodio-docs/v.list`: set `<var name="kodio-version" value="X.Y.Z"/>`
- [ ] `README.md`: update each `implementation(...)` line in the Installation section
- [ ] Commit and push to `master`:
      `git commit -m "chore: bump version to X.Y.Z" && git push origin master`

## CI Matrix

The CI workflow (`.github/workflows/gradle.yml`) runs these matrix jobs. All must pass before tagging:

| Job ID | Runner | Gradle tasks |
|--------|--------|--------------|
| `linux-jvm` | ubuntu-latest | `jvmTest` |
| `android-unit` | ubuntu-latest | `testAndroidHostTest` |
| `web` | ubuntu-latest | `jsBrowserTest wasmJsBrowserTest` |
| `macos-native` | macos-latest | `macosArm64Test` |
| `ios-sim` | macos-latest | `iosSimulatorArm64Test` |
| `compile-samples` | ubuntu-latest | `:kodio-sample-app:compileKotlinDesktop :kodio-sample-app:compileAndroidMain :kodio-benchmark:compileKotlinJvm` |

- [ ] After the version bump, confirm the full matrix is green on `master`.

## Tag and Publish

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

**Tag format:**
- Stable releases: `v1.0.0`, `v0.2.0`
- Pre-releases: `v1.0.0-alpha`, `v1.0.0-beta.1`, `v1.0.0-rc.1` (auto-marked as prerelease on GitHub)

## Monitor the Pipeline

`publish.yml` runs three sequential jobs:

| Job | Runner | What it does |
|-----|--------|-------------|
| `test` | matrix (all CI jobs) | Reuses `.github/workflows/gradle.yml` |
| `create-release` | ubuntu-latest | Generates changelog via git-cliff, creates GitHub Release |
| `publish` | macos-latest | Runs `./gradlew publishToMavenCentral` (macOS needed for Apple targets) |

```bash
gh run list --limit 5
gh run watch <run-id>
gh run view <run-id> --log-failed   # if a job fails
```

## Verify Artifacts

- [ ] GitHub Release exists: `gh release view vX.Y.Z`
- [ ] All artifacts appear on Maven Central (may take 15 to 30 minutes):
      - `space.kodio:core:X.Y.Z`
      - `space.kodio.extensions:compose:X.Y.Z`
      - `space.kodio.extensions:compose-material3:X.Y.Z`
      - `space.kodio.extensions:transcription:X.Y.Z`
      - `space.kodio.extensions:ktor:X.Y.Z`

## Post-release (optional)

- [ ] Bump `gradle.properties` to the next SNAPSHOT for local development:
      `kodio.version=X.Y.Z+1-SNAPSHOT`
- [ ] Add migration notes to `kodio-docs/topics/Migration.md` if the release includes breaking changes.

## Cleaning Up a Failed Release

```bash
gh release delete vX.Y.Z --yes
git push origin :refs/tags/vX.Y.Z
git tag -d vX.Y.Z
```

Fix the issue, then re-tag and push.

## Manual Publishing

For publishing without the CI pipeline:

```bash
export ORG_GRADLE_PROJECT_mavenCentralUsername=<username>
export ORG_GRADLE_PROJECT_mavenCentralPassword=<password>
export ORG_GRADLE_PROJECT_signingInMemoryKeyId=<gpg-key-id>
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=<gpg-password>
export ORG_GRADLE_PROJECT_signingInMemoryKey=<base64-gpg-key>

./gradlew publishToMavenCentral --no-configuration-cache -Pkodio.version=X.Y.Z
```

## Changelog

Release notes are generated from conventional commit messages by git-cliff during the publish workflow (`cliff.toml`).

## Workflow Files Reference

| File | Purpose |
|------|---------|
| `.github/workflows/publish.yml` | Tag-triggered release pipeline |
| `.github/workflows/gradle.yml` | CI matrix (reusable) |
| `cliff.toml` | git-cliff configuration for release notes |
| `build-logic/src/main/kotlin/kodio-publish-convention.gradle.kts` | Maven publishing convention plugin |
