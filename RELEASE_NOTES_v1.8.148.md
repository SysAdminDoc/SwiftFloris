# SwiftFloris v1.8.148

Date: 2026-05-18

## Extension Archive File Trust States

This release continues Workstream 5 by making generic extension archive file
management communicate progress, terminal results, and duplicate-action blocking
for import, rename, and delete operations.

### Changed

- Added `ExtensionEditorFilesPolicy` for file-action gating, notice precedence,
  and import/rename/delete result classification.
- Updated extension archive file management to show file-action progress,
  import success/failure, rename success/failure, and delete success/failure
  cards instead of transient toast-only feedback.
- Moved selected-file copying, archive-file rename, and archive-file delete work
  off the main thread.
- Disabled duplicate close/add/select/file-property actions while file work is
  running so users cannot start overlapping archive mutations.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 1m 50s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.148` / code `1948`.
- Roadmap, project context, improvement plan, and release index updated.
