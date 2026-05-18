# SwiftFloris v1.8.167 — Destructive Confirmation Polish

Released: 2026-05-18

## Intent

Continue `IMPROVEMENT_PLAN.md` Workstream 10 by making theme and extension
editing destructive actions explicit before they mutate a draft.

## Changes

- Added a dedicated confirmation before deleting a file from an extension draft
  archive.
- Added confirmation before deleting a theme editor stylesheet rule.
- Added confirmation before deleting a theme editor rule property.
- Updated the confirmation copy to clarify that installed extensions and themes
  stay unchanged until the edited draft is saved.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, and `README.md` for the v1.8.167 release
  state.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
