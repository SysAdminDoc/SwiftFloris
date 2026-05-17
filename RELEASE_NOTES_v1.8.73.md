# SwiftFloris v1.8.73

**Release date:** 2026-05-17
**Type:** Repository hygiene / CI guardrail

## What changed

- Moved local root JVM crash and replay logs out of the repository root into
  `.ai/local-crash-logs/2026-05-16/`.
- Added `scripts/check-no-root-crash-logs.sh`, which fails when committed
  root-level `hs_err_pid*.log` or `replay_pid*.log` files are present.
- Wired that guard into `.github/workflows/android.yml` before Java / Gradle
  setup so forced-added logs fail quickly in CI.
- Added `.ai/local-crash-logs/README.md` to document that the log files are
  local diagnostics and should remain ignored.

This is the Tier-1 repository-hygiene batch from the 2026-05-17
prioritization matrix (#14 + #15). No app code, permissions, dependencies, or
runtime behavior changed.

## Files touched

- `.github/workflows/android.yml`
- `.ai/local-crash-logs/README.md`
- `scripts/check-no-root-crash-logs.sh`
- `gradle.properties`
- `README.md`
- `ROADMAP.md`
- `IMPROVEMENT_PLAN.md`
- `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Root crash/replay log scan after move returned no root files.
- `bash scripts/check-no-root-crash-logs.sh`
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
