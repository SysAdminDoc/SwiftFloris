# SwiftFloris v1.8.69 — 2026-05-17

Bump-batch A — low-risk dependency refresh.

## Why ship this now

The 2026-05-17 dependency review split upgrades into small risk-isolated
batches. This release ships the low-risk patch/minor batch while leaving
Roborazzi/Robolectric and AGP/Compose for their own visual-regression and R8
audit slices.

## What changed

Updated `gradle/libs.versions.toml`:

| Dependency | Before | After | Source checked |
|---|---:|---:|---|
| `kotlinx-coroutines` | `1.10.2` | `1.11.0` | Maven Central metadata for `org.jetbrains.kotlinx:kotlinx-coroutines-android` |
| `ksp` | `2.3.5` | `2.3.8` | Maven Central plugin-marker metadata for `com.google.devtools.ksp` |
| `zxing-core` | `3.5.3` | `3.5.4` | Maven Central metadata for `com.google.zxing:core` |
| `mikepenz-aboutlibraries` | `14.0.1` | `14.2.0` | Maven Central / Gradle Plugin Portal metadata; `15.0.0-b01` is beta and was intentionally skipped |

No app code, permissions, network surface, or runtime feature behavior changed.

## Versioning

- `gradle.properties`: `projectVersionCode=1869`,
  `projectVersionName=1.8.69`.

## Verification

Local checks performed on this Windows VM:

```powershell
git diff --check
rg -n "kotlinx-coroutines = \"1.11.0\"|ksp = \"2.3.8\"|mikepenz-aboutlibraries = \"14.2.0\"|zxing-core = \"3.5.4\"" gradle/libs.versions.toml
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

Gradle verification is still blocked on this VM because Java is not configured:
`JAVA_HOME is not set and no 'java' command could be found in your PATH`.
Run before merge on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
