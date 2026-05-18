# SwiftFloris v1.8.166 — Repo Hygiene Closure

Released: 2026-05-18

## Intent

Close `IMPROVEMENT_PLAN.md` Workstream 9 by making the repo-hygiene rules
durable instead of relying on handoff memory: deleted legacy docs are classified,
generated build/report output is guarded, commits stay scoped to one release
slice, and final handoffs carry exact verification commands.

## Changes

- Added `docs/REPO_HYGIENE.md` with the current legacy root-markdown decision,
  generated-output rule, one-slice commit rule, and handoff verification rule.
- Added `scripts/check-repo-hygiene.sh`, which fails if generated build/report
  directories are tracked or if local Markdown deletions remain unclassified.
- Wired the repo-hygiene script into `.github/workflows/android.yml` before
  Gradle work.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, and `README.md` for the v1.8.166 release
  state.

## Verification

- `git diff --check`
- `bash -n scripts/check-repo-hygiene.sh`
- `bash scripts/check-repo-hygiene.sh`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
