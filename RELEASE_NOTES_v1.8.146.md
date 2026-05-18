# SwiftFloris v1.8.146

Date: 2026-05-18

## Language Pack Trust States

This release continues Workstream 5 by making language pack import, update, and
removal workflows communicate progress, outcomes, and recovery paths.

### Changed

- Added import-flow policy helpers for file selection, import busy-state
  gating, import notice precedence, and install/update/skipped-file summaries.
- Updated extension import to show file-reading, importing, cancellation,
  failure, and success cards, with duplicate select/import actions disabled
  while work is running.
- Replaced the generic import review copy with explicit new-install, update,
  and skipped-file counts so language pack updates are visible before commit.
- Added `LanguagePackManagerPolicy` and wired language pack management to show
  delete progress, success, and failure cards.
- Disabled duplicate language pack delete/import actions while deletion is in
  progress, and kept delete failures visible on the manager screen.

### Tests

- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 1m 51s; lint reported 245 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.146` / code `1946`.
- Roadmap, project context, improvement plan, and release index updated.
