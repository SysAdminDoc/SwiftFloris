# SwiftFloris v1.8.150

Date: 2026-05-18

## Trust-State Recovery Microcopy

This release closes the first broad Workstream 5 recovery-copy pass. The
failure cards added across the recent trust-state audit now tell users what did
not change and what the safe recovery path is, instead of showing only a raw
technical error.

### Changed

- Updated backup and restore failure cards to explain that no archive was saved
  or that restore stopped before all selected data could be imported, then point
  users toward storage/destination/archive retry paths.
- Updated extension import, installed-extension delete, theme-extension save,
  and archive-file import/rename/delete failure cards with specific unchanged
  state and retry guidance.
- Updated language-pack delete and manual dictionary entry save/delete failure
  cards with recovery copy that keeps the technical error detail visible.
- Marked the Workstream 5 failure/recovery microcopy task complete in the
  improvement plan.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 21s.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 3s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.150` / code `1950`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
