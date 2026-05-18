# SwiftFloris v1.8.165 — CI Quality Gates

Released: 2026-05-18

## Intent

Close `IMPROVEMENT_PLAN.md` Workstream 8 by making the local verification path
visible in CI and docs: unit tests and debug builds were already PR-gated, while
lint baseline drift, dependency version review, emulator smoke, and local command
documentation needed explicit wiring.

## Changes

- Fixed Android lint DSL wiring in `app/build.gradle.kts`: `app/lint.xml` is now
  a lint config file instead of being treated as a stale baseline file.
- Added `scripts/run-lint-debug-with-baseline-check.sh`, which runs
  `:app:lintDebug`, captures `app/build/reports/lintDebug-console.log`, and
  fails if Android Lint reports stale baseline entries.
- Updated `.github/workflows/android.yml` to run the lint drift wrapper and
  upload the captured lint console log.
- Added `.github/dependabot.yml` for weekly Gradle and GitHub Actions version
  review PRs.
- Added `.github/workflows/emulator-smoke.yml`, a manual/emergent PR workflow
  that builds the debug APK, launches the Settings app on an API 35 emulator,
  checks that the process is alive, and uploads logcat.
- Added `docs/LOCAL_VERIFICATION.md` and linked it from contributor/agent docs.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, `docs/SECURITY.md`, and `README.md` for the
  v1.8.165 release state.

## Verification

- `git diff --check`
- `bash scripts/run-lint-debug-with-baseline-check.sh`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
