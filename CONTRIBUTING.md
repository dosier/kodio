# Contributing to Kodio

Thank you for your interest in contributing to Kodio! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Building the Project](#building-the-project)
- [Publishing](#publishing)
- [Forking & Custom Versions](#forking--custom-versions)
- [Pull Request Process](#pull-request-process)

## Development Setup

### Prerequisites

- **JDK 21** or newer
- **Android SDK** (for Android targets)
- **Xcode** (for iOS/macOS targets, macOS only)
- **Gradle 8.x** (wrapper included)

### Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/dosier/kodio.git
   cd kodio
   ```

2. Create `local.properties` with your SDK paths:
   ```properties
   sdk.dir=/path/to/android/sdk
   ```

3. Build the project:
   ```bash
   ./gradlew build
   ```

4. Run the sample app (Desktop):
   ```bash
   ./gradlew :kodio-sample-app:desktopRun
   ```

## Project Structure

```
kodio/
├── build-logic/              # Shared Gradle convention plugins
│   └── src/main/kotlin/      # Convention plugin sources
├── kodio-core/               # Core audio library (recording, playback, formats)
├── kodio-extensions/
│   ├── compose/              # Compose UI components
│   ├── compose-material3/    # Material 3 themed components
│   └── transcription/        # Audio transcription (OpenAI Whisper)
├── kodio-native/             # Native platform code (permissions, processing)
├── kodio-sample-app/         # Demo application
└── kodio-docs/               # Documentation (Writerside)
```

## Building the Project

### Common Tasks

| Task | Command |
|------|---------|
| Build all modules | `./gradlew build` |
| Run tests | `./gradlew check` |
| Build JVM JAR | `./gradlew :kodio-core:jvmJar` |
| Run desktop sample | `./gradlew :kodio-sample-app:desktopRun` |
| Clean build | `./gradlew clean build` |

### Platform-Specific Builds

```bash
# Android
./gradlew :kodio-core:assembleRelease

# iOS (requires macOS)
./gradlew :kodio-core:linkReleaseFrameworkIosArm64

# macOS
./gradlew :kodio-core:macosArm64Binaries

# Web (JS)
./gradlew :kodio-core:jsBrowserProductionWebpack

# Web (WASM)
./gradlew :kodio-core:wasmJsBrowserProductionWebpack
```

## Publishing

Kodio uses [Vanniktech Maven Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) with a shared convention plugin for consistent configuration across all modules.

### Published Artifacts

| Module | Maven Coordinates |
|--------|-------------------|
| Core | `space.kodio:core` |
| Compose | `space.kodio.extensions:compose` |
| Compose Material3 | `space.kodio.extensions:compose-material3` |
| Transcription | `space.kodio.extensions:transcription` |

### Version Management

The version for all modules is defined in a single place:

```properties
# gradle.properties
kodio.version=0.1.0-SNAPSHOT
```

To publish with a custom version:
```bash
./gradlew publishToMavenCentral -Pkodio.version=1.0.0-custom
```

### Creating a Release (Tag-Based)

Releases are automated via GitHub Actions when you push a version tag:

```bash
# 1. Make sure all changes are committed and pushed to main
git checkout main
git pull origin main

# 2. Create and push a version tag
git tag v0.1.0
git push origin v0.1.0
```

This will automatically:
1. Run the full test suite on all platforms
2. Create a GitHub Release with auto-generated changelog
3. Publish all artifacts to Maven Central

**Tag Format:**
- Release versions: `v1.0.0`, `v0.2.0`, `v1.2.3`
- Pre-releases: `v1.0.0-alpha`, `v1.0.0-beta.1`, `v1.0.0-rc.1` (automatically marked as prerelease)

### Release Drafter (Auto Changelog)

The repository uses [Release Drafter](https://github.com/release-drafter/release-drafter) to automatically draft release notes as PRs are merged. This creates a draft release that accumulates changes until you're ready to publish.

**How it works:**
1. Every merged PR is automatically categorized based on labels
2. Draft release notes are updated with each merge
3. When ready to release, you can review the draft in GitHub Releases
4. Push a tag to finalize and publish

**PR Labels for Changelog Categories:**

| Label | Category | Version Bump |
|-------|----------|--------------|
| `feature`, `enhancement` | Features | Minor |
| `fix`, `bugfix`, `bug` | Bug Fixes | Patch |
| `breaking`, `breaking-change` | Breaking Changes | Major |
| `chore`, `maintenance`, `dependencies` | Maintenance | Patch |
| `documentation`, `docs` | Documentation | Patch |
| `skip-changelog` | (excluded) | - |

**Auto-labeling:** PRs are automatically labeled based on branch names:
- `feature/*` → `feature`
- `fix/*`, `bugfix/*` → `fix`
- `chore/*` → `chore`
- `docs/*` → `documentation`

### Manual Publishing (Maintainers)

For manual publishing without the GitHub Actions workflow:

1. Set up credentials as environment variables:
   ```bash
   export ORG_GRADLE_PROJECT_mavenCentralUsername=<your-username>
   export ORG_GRADLE_PROJECT_mavenCentralPassword=<your-password>
   export ORG_GRADLE_PROJECT_signingInMemoryKeyId=<gpg-key-id>
   export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=<gpg-password>
   export ORG_GRADLE_PROJECT_signingInMemoryKey=<base64-gpg-key>
   ```

2. Run publish:
   ```bash
   ./gradlew publishToMavenCentral --no-configuration-cache -Pkodio.version=0.1.0
   ```

### Publishing to Maven Local (for testing)

```bash
./gradlew publishToMavenLocal
```

Then use in other projects:
```kotlin
repositories {
    mavenLocal()
}
dependencies {
    implementation("space.kodio:core:0.1.0-SNAPSHOT")
}
```

## Forking & Custom Versions

If you need to fork Kodio and publish your own version:

### 1. Update Version

Edit `gradle.properties`:
```properties
kodio.version=1.0.0-yourcompany
```

Or use command line override:
```bash
./gradlew publish -Pkodio.version=1.0.0-yourcompany
```

### 2. Update Group ID (Optional)

To avoid conflicts with the official artifacts, update the group in each module's `build.gradle.kts`:

```kotlin
// kodio-core/build.gradle.kts
group = "com.yourcompany.kodio"

kodioPublishing {
    artifactId = "core"
    description = "Your custom Kodio build"
}
```

### 3. Update POM Information

Modify `build-logic/src/main/kotlin/kodio-publish-convention.gradle.kts` to update:
- Developer information
- SCM URLs
- License (if different)

### 4. Set Up Your Own Maven Repository

Options:
- **Maven Central**: Create account at [central.sonatype.com](https://central.sonatype.com)
- **GitHub Packages**: Free for public repos
- **JitPack**: Just tag and it builds automatically
- **Private Artifactory/Nexus**: For enterprise use

### Publishing to GitHub Packages

Add to your convention plugin or module:
```kotlin
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR-USERNAME/kodio")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### Publishing to JitPack

Simply tag your fork and use:
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
dependencies {
    implementation("com.github.YOUR-USERNAME:kodio:TAG")
}
```

## Pull Request Process

1. **Fork** the repository and create a feature branch:
   ```bash
   git checkout -b feature/my-awesome-feature
   # or: fix/bug-description, docs/update-readme, chore/update-deps
   ```
2. **Make changes** following the existing code style
3. **Add tests** for new functionality
4. **Update documentation** if needed
5. **Run checks** before submitting:
   ```bash
   ./gradlew check
   ```
6. **Submit PR** with a clear description of changes
7. **Add labels** to categorize your PR (or let auto-labeling handle it based on branch name)

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable/function names
- Add KDoc for public APIs
- Keep functions small and focused

### Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/) for clear changelogs:

```
feat: add transcription cost tracking
fix: handle empty audio chunks gracefully
docs: update publishing instructions
refactor: simplify WAV encoding logic
chore: update dependencies
```

**Prefixes:**
| Prefix | Description | Changelog Category |
|--------|-------------|--------------------|
| `feat:` | New feature | Features |
| `fix:` | Bug fix | Bug Fixes |
| `docs:` | Documentation only | Documentation |
| `refactor:` | Code refactoring | Maintenance |
| `chore:` | Maintenance tasks | Maintenance |
| `test:` | Adding tests | (excluded) |
| `ci:` | CI/CD changes | (excluded) |

**Breaking Changes:** Add `BREAKING CHANGE:` in the commit body or use `feat!:` / `fix!:` prefix.

## Questions?

- Open an [issue](https://github.com/dosier/kodio/issues) for bugs or feature requests
- Start a [discussion](https://github.com/dosier/kodio/discussions) for questions

Thank you for contributing! 🎉

