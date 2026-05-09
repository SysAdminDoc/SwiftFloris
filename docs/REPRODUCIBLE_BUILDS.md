# SwiftFloris Reproducible Builds

**Status:** Toolchain pinned, awaiting F-Droid submission for verified ✔ badge.
**Roadmap §:** 6 N6.3.

This document explains how SwiftFloris pins every reproducible-build input so a
third party can rebuild the published APK byte-for-byte (modulo signing) and
verify it matches the official Release artifact.

---

## Pinned toolchain inputs

| Input | Pin location | Current version | SHA-256 / verification |
|---|---|---|---|
| Gradle distribution | `gradle/wrapper/gradle-wrapper.properties` | 9.4.1 | `distributionSha256Sum=2ab2958f...` |
| Android Gradle Plugin | `gradle/libs.versions.toml` `[versions] android-gradle-plugin` | 9.0.0 | resolves to a fixed Maven artifact |
| Kotlin | `gradle/libs.versions.toml` `[versions] kotlin` | 2.3.20 | fixed Maven artifact |
| KSP | `gradle/libs.versions.toml` `[versions] ksp` | matches Kotlin compiler | fixed Maven artifact |
| Build Tools | `gradle/tools.versions.toml` `buildTools` | 36.0.0 | fixed Android SDK component |
| NDK | `gradle/tools.versions.toml` `ndk` | 29.0.14206865 | fixed Android SDK component |
| JDK | `gradle/tools.versions.toml` `jdk` | 17 | enforced by `compileOptions { sourceCompatibility = JavaVersion.VERSION_11 }` and `kotlin { compilerOptions { jvmTarget = JVM_11 } }`; build container uses `setup-java@v4 java-version: 17 distribution: temurin` |
| CMake | `gradle/tools.versions.toml` `cmake` | 4.1.2 | fixed |
| cmdline tools | `gradle/tools.versions.toml` `cmdlineTools` + `cmdlineToolsChecksum` | 14742923 | SHA-256 pinned |
| Rust toolchain | `gradle/tools.versions.toml` `rustToolchain` | 1.93.0 | only used by `lib/native` (currently disabled in `settings.gradle.kts`) |

All Compose / library dependencies live behind `gradle/libs.versions.toml` version refs — no transitive `+` or `latest.release` selectors.

## Build environment

The release CI pipeline runs in `ubuntu-latest` (`.github/workflows/release.yml`)
with `actions/setup-java@v4` pinning `java-version: 17 distribution: temurin`.
Gradle caching is wired but doesn't affect output bytes (cache hits restore
identical artifacts).

## What can still drift?

- **Fastlane metadata**: not part of the APK; doesn't affect reproducibility.
- **APK signing**: SwiftFloris's release keystore is private; F-Droid will
  resign with their key for the F-Droid track. Reproducibility verification
  compares *unsigned* APK bytes via `apksigner verify --print-certs`.
- **Build timestamps**: Gradle and AGP both default to deterministic timestamps
  for AAB / APK ZIP entries when `org.gradle.caching` is on and the AGP
  reproducibility flags are honored. SwiftFloris does not currently override
  these defaults (good).

## How to verify locally

```bash
# 1. Clone at the exact tag you want to reproduce
git clone --branch v1.7.3 --depth 1 https://github.com/SysAdminDoc/SwiftFloris.git
cd SwiftFloris

# 2. Build the release APK (debug-signed fallback fine for byte comparison)
./gradlew :app:assembleRelease

# 3. Compare against the published APK (after stripping signatures)
APK_LOCAL=app/build/outputs/apk/release/app-release.apk
APK_PUBLISHED=app-release-v1.7.3.apk

apkdiff() {
  unzip -p "$1" classes.dex | sha256sum
  unzip -p "$1" resources.arsc | sha256sum
  unzip -l "$1" | sort
}

apkdiff "$APK_LOCAL"     > local.txt
apkdiff "$APK_PUBLISHED" > published.txt
diff local.txt published.txt && echo "Reproducible ✔"
```

## F-Droid submission

To complete N6.3, submit the following `Builds:` stanza to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata):

```yaml
Builds:
  - versionName: "1.7.3"
    versionCode: 173
    commit: v1.7.3
    sudo:
      - apt-get update
      - apt-get install -y openjdk-17-jdk-headless
      - update-alternatives --auto java
    gradle:
      - yes
    androidupdate:
      - no
    output: app/build/outputs/apk/release/app-release.apk
    binary: https://github.com/SysAdminDoc/SwiftFloris/releases/download/v%v/app-release.apk

ArchivePolicy: 6 versions
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: "1.7.3"
CurrentVersionCode: 173
```

The F-Droid build server will then attempt a deterministic rebuild and compare
SHA-256 — passing earns the verified ✔ badge alongside HeliBoard, Fossify,
FlickBoard, and Thumb-Key.
