# SwiftFloris v1.8.145

Date: 2026-05-18

## Restore Flow Trust States

This release continues Workstream 5 by making Settings -> Advanced -> Restore
data communicate destructive and long-running states explicitly.

### Changed

- Added `RestoreFlowNotice` and `RestoreOperationSummary` to
  `BackupRestorePolicy` so restore loading, progress, erase-mode recovery-copy
  guidance, cancellation, failure, partial failure, and success have
  deterministic precedence.
- Added an erase-restore confirmation dialog and visible recovery-copy guidance
  before local data can be cleared.
- Changed restore execution to summarize selected sections individually, keep
  restoring other sections after a recoverable section failure, and report
  partial failures on-screen instead of relying only on toasts.
- Carried the first per-section restore exception into the summary so full
  failures can show a specific error instead of a generic fallback.
- Avoided erasing local keyboard, theme, preferences, or clipboard data for a
  selected section when the backup archive does not contain that section.
- Disabled restore and cancel actions while restore work is running to prevent
  duplicate execution or leaving the screen mid-restore.

### Tests

- `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.advanced.BackupRestorePolicyTest`
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  - Passed. Lint reports 243 warnings, 1 hint, plus the existing stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.145` / code `1945`.
- Roadmap, project context, improvement plan, and release index updated.
