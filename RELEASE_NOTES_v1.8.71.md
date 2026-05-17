# SwiftFloris v1.8.71

**Release date:** 2026-05-17
**Type:** Dependency maintenance / visual-test infrastructure

## What changed

- Roborazzi `1.55.0` → `1.60.0`
- Robolectric `4.14.1` → `4.16.1`

This is Bump-batch B from the 2026-05-17 prioritization matrix. It refreshes
the screenshot-regression and JVM Android test harness before the later
AGP 9.2.x / Compose BOM 2026.05.00 bump.

## Sources checked

- Roborazzi Maven Central metadata:
  `https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml`
- Roborazzi Gradle Plugin Portal metadata:
  `https://plugins.gradle.org/m2/io/github/takahirom/roborazzi/io.github.takahirom.roborazzi.gradle.plugin/maven-metadata.xml`
- Robolectric Maven Central metadata:
  `https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml`
- OSV querybatch API for the four updated Maven packages.

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

- Maven metadata confirmed Roborazzi core + Gradle plugin latest/release
  `1.60.0`; Robolectric latest/release `4.16.1`.
- OSV querybatch returned zero vulnerabilities for:
  - `io.github.takahirom.roborazzi:roborazzi:1.60.0`
  - `io.github.takahirom.roborazzi:roborazzi-compose:1.60.0`
  - `io.github.takahirom.roborazzi:roborazzi-junit-rule:1.60.0`
  - `org.robolectric:robolectric:4.16.1`
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
