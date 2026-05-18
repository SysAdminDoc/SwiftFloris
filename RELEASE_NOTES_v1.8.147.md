# SwiftFloris v1.8.147

Date: 2026-05-18

## Theme Extension Trust States

This release continues Workstream 5 by making theme extension edit and delete
workflows communicate progress, failures, and destructive draft changes.

### Changed

- Added `ThemeExtensionTrustStatePolicy` for theme-editor save gating, editor
  notice precedence, installed extension delete gating, and delete notice
  precedence.
- Updated theme extension editing to show save progress and save-failure cards,
  block duplicate save/cancel/component actions while saving, and persist the
  archive off the main thread.
- Added confirmation before removing a theme component from an extension draft,
  plus a visible "theme removed from draft" card that explains saving applies
  the removal.
- Updated installed extension details to show delete progress and failure cards,
  block duplicate delete/export actions while deletion is running, and perform
  deletion off the main thread.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 12s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.147` / code `1947`.
- Roadmap, project context, improvement plan, and release index updated.
