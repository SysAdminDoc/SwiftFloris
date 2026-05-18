# SwiftFloris v1.8.168 — Addon Scan Progress

Released: 2026-05-18

## Intent

Continue `IMPROVEMENT_PLAN.md` Workstream 10 by making active addon scans use
the same visible progress affordance as other file, import, and delete flows.

## Changes

- Added a shared progress card while Addons Settings rescans installed addon
  APKs and refreshes dictionary-pack metadata.
- Kept the existing disabled rescan action row, so users get both a stable
  control state and a visible page-level scan status.
- Updated the touched signing-pin preference observation from deprecated
  `observeAsState` to `collectAsState`.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, and `README.md` for the v1.8.168 release
  state.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
- `bash scripts/check-repo-hygiene.sh`
