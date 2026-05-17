# SwiftFloris v1.8.74

**Release date:** 2026-05-17
**Type:** Dependency maintenance / build toolchain

## What changed

- Android Gradle Plugin `9.0.0` → `9.2.1`
- Compose BOM `2026.03.01` → `2026.05.00`

This is Bump-batch C from the 2026-05-17 prioritization matrix. It follows the
Roborazzi / Robolectric refresh that shipped in v1.8.71 and keeps the build
toolchain on the stable AGP 9.2 patch line while avoiding the newer
`9.3.0-alpha*` preview series.

## Sources checked

- Android Gradle Plugin 9.2 release notes:
  `https://developer.android.com/build/releases/agp-9-2-0-release-notes`
- Android Studio release updates for Panda 4 Patch 1 / AGP 9.2.1:
  `https://androidstudio.googleblog.com/2026/05/`
- Google Maven AGP metadata:
  `https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml`
- Google Maven Compose BOM metadata:
  `https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml`
- Compose release page:
  `https://developer.android.com/jetpack/androidx/releases/compose`
- OSV querybatch API for `com.android.tools.build:gradle:9.2.1` and
  `androidx.compose:compose-bom:2026.05.00`.

## R8 rule audit

AGP 9.2 changes `-keepattributes` wildcard handling for runtime-invisible
annotations. SwiftFloris's app release rules already keep only
`RuntimeVisibleAnnotations,AnnotationDefault`, so no rule change is required.
The older `SourceFile,LineNumberTable` examples in library ProGuard files are
commented out and do not participate in release builds.

## Files touched

- `gradle/libs.versions.toml`
- `gradle.properties`
- `README.md`
- `docs/DEPENDENCY_TRIAGE.md`
- `ROADMAP.md`
- `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Google Maven metadata: AGP stable tail includes `9.2.1`; `9.3.0-alpha05`
  is preview and intentionally skipped.
- Google Maven metadata: Compose BOM latest/release is `2026.05.00`.
- Android Studio May 2026 stable patch notes list AGP `9.2.1`.
- OSV querybatch returned zero vulnerabilities for:
  - `com.android.tools.build:gradle:9.2.1`
  - `androidx.compose:compose-bom:2026.05.00`
- R8 keepattributes audit found no active wildcard rule that keeps
  `RuntimeInvisibleAnnotations`.
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
