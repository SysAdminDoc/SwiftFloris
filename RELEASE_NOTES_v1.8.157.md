# SwiftFloris v1.8.157

Date: 2026-05-18

## Non-Color State Indicators

This release continues the Accessibility Pass by making app state feedback rely
on icon shape and explicit copy instead of color treatment alone.

### Changed

- Added shared `FlorisSuccessCard`, `FlorisProgressCard`, and
  `FlorisNeutralCard` wrappers with distinct icons and tones.
- Updated extension import/edit/delete, language-pack delete, dictionary
  import/export/entry, backup/restore, home readiness, and voice readiness
  notices to use the new state-specific cards.
- Added ready/skipped icons to extension import file rows so per-file status is
  visible even when color is unavailable.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin` passed in 16s; the run reported only
  the repo's existing Room/Kotlin/deprecated-toast warnings.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 24s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.
- `.\gradlew.bat :app:verifyRoborazziDebug` passed in 59s.

### Definition of Done

- Version bumped to `1.8.157` / code `1957`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
