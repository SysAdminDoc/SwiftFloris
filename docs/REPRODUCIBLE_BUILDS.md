# SwiftFloris Reproducible Builds

**Status:** Toolchain pinned, local self-verification added, awaiting F-Droid
submission for verified ✔ badge.
**Roadmap §:** 6 N6.3 + addendum N12.5.

This document explains how SwiftFloris pins every reproducible-build input so a
third party can rebuild the published APK byte-for-byte (modulo signing) and
verify it matches the official Release artifact.

---

## Pinned toolchain inputs

| Input | Pin location | Current version | SHA-256 / verification |
|---|---|---|---|
| Gradle distribution | `gradle/wrapper/gradle-wrapper.properties` | 9.6.1 | `distributionSha256Sum=9c0f7fae...` |
| Android Gradle Plugin | `gradle/libs.versions.toml` `[versions] android-gradle-plugin` | 9.3.0 | resolves to a fixed Maven artifact |
| Kotlin | `gradle/libs.versions.toml` `[versions] kotlin` | 2.4.10 | fixed Maven artifact |
| KSP | `gradle/libs.versions.toml` `[versions] ksp` | 2.3.9 | fixed Maven artifact (KSP 2.x has its own release cadence; version no longer tracks Kotlin 1:1) |
| Build Tools | `gradle/tools.versions.toml` `buildTools` | 36.0.0 | fixed Android SDK component |
| NDK | `gradle/tools.versions.toml` `ndk` | 29.0.14206865 | fixed Android SDK component |
| JDK | `gradle/tools.versions.toml` `jdk` | 17 | enforced by `compileOptions { sourceCompatibility = JavaVersion.VERSION_11 }` and `kotlin { compilerOptions { jvmTarget = JVM_11 } }`; build container uses `setup-java@v4 java-version: 17 distribution: temurin` |
| CMake | `gradle/tools.versions.toml` `cmake` | 4.1.2 | fixed |
| cmdline tools | `gradle/tools.versions.toml` `cmdlineTools` + `cmdlineToolsChecksum` | 14742923 | SHA-256 pinned |
| Rust toolchain | `gradle/tools.versions.toml` `rustToolchain` | 1.93.0 | dormant pin retained for future out-of-tree native addons; `:app` ships no Rust today (the `:lib:native` placeholder was dropped in v1.8.185) |

All Compose / library dependencies live behind `gradle/libs.versions.toml` version refs — no transitive `+` or `latest.release` selectors.
`scripts/check-public-doc-version-pins.py` verifies this table plus the README and security version claims against
the Gradle catalog, wrapper, and project properties.

## Build environment

Release APKs are built locally from the tagged source with the pinned wrapper,
Android SDK, build tools, and dependency catalog. `scripts/release-evidence.ps1`
sets the maintainer host JDK path explicitly and records the exact command logs
under `build/release-evidence/<timestamp>/`.

The release-evidence Gradle gates run with
`--no-build-cache --rerun-tasks -Dorg.gradle.caching=false -Dkotlin.caching.enabled=false`
while SwiftFloris is on Kotlin `2.4.0`, and
`scripts/check-kotlin-build-cache-cve-guard.py` fails release/reproducible
invocations that drop those flags before the fix is available. This is
intentionally scoped to release evidence so normal developer builds keep their
existing `gradle.properties` cache behavior. Remove the mitigation only after
the repo adopts a final Kotlin `2.4.20+` compiler with the build-cache CVE fix
and a compatible KSP release.

## Local self-verification

`scripts/verify-reproducible-apk.sh` is the repository-local "build twice,
compare APK bytes" guard. It creates two detached Git worktrees at the same
commit, updates submodules, runs release assembly in both clean trees with
Gradle and Kotlin build caches disabled and tasks re-run, then compares the two
APKs byte-for-byte. The same Kotlin build-cache CVE guard runs before either
worktree build starts.

`scripts/release-evidence.ps1` runs this verifier before publication unless the
maintainer is doing a focused smoke run with `-SkipReproducibleApk`. On mismatch,
the script writes per-entry SHA-256 manifests excluding `META-INF/` so
maintainers can tell whether the drift is payload content or signing / ZIP
metadata.

## What can still drift?

- **Fastlane metadata**: not part of the APK; doesn't affect reproducibility.
- **APK signing**: SwiftFloris's release keystore is private; F-Droid will
  resign with their key for the F-Droid track. Reproducibility verification
  compares *unsigned* APK bytes via `apksigner verify --print-certs`.
- **Build timestamps**: Gradle and AGP both default to deterministic timestamps
  for AAB / APK ZIP entries when AGP reproducibility flags are honored.
  SwiftFloris does not currently override these defaults (good).

## How to verify locally

```bash
# 1. Clone at the exact tag you want to reproduce
git clone --branch v1.9.58 --depth 1 https://github.com/SysAdminDoc/SwiftFloris.git
cd SwiftFloris

# 2. Build the release APK (debug-signed fallback fine for byte comparison)
./gradlew :app:assembleRelease

# 3. Compare against the published APK (after stripping signatures)
APK_LOCAL=app/build/outputs/apk/release/app-release.apk
APK_PUBLISHED=app-release-v1.9.58.apk

apkdiff() {
  unzip -p "$1" classes.dex | sha256sum
  unzip -p "$1" resources.arsc | sha256sum
  unzip -l "$1" | sort
}

apkdiff "$APK_LOCAL"     > local.txt
apkdiff "$APK_PUBLISHED" > published.txt
diff local.txt published.txt && echo "Reproducible ✔"
```

To run the in-repo self-check locally on a Linux host:

```bash
bash scripts/verify-reproducible-apk.sh
```

## F-Droid submission

To complete N6.3, submit the prepared recipe at
[`fdroid/io.github.sysadmindoc.swiftfloris.yml`](../fdroid/io.github.sysadmindoc.swiftfloris.yml)
to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) via merge request.

The recipe is kept in the repo and updated alongside version bumps so it stays
in sync with the build. Current stanza:

```yaml
Builds:
  - versionName: "1.9.58"
    versionCode: 2107
    commit: v1.9.58
    submodules: true
    sudo:
      - apt-get update
      - apt-get install -y openjdk-17-jdk-headless
      - update-alternatives --auto java
    gradle:
      - yes
    output: app/build/outputs/apk/release/app-release-unsigned.apk
    binary: https://github.com/SysAdminDoc/SwiftFloris/releases/download/v%v/app-release.apk

ArchivePolicy: 6
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: "1.9.58"
CurrentVersionCode: 2107
```

The F-Droid build server will then attempt a deterministic rebuild and compare
SHA-256 — passing earns the verified ✔ badge alongside HeliBoard, Fossify,
FlickBoard, and Thumb-Key.
