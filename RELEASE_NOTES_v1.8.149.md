# SwiftFloris v1.8.149

Date: 2026-05-18

## Dictionary Entry Trust States

This release continues Workstream 5 by making manual user-dictionary add,
update, and delete operations communicate progress, terminal results, and
duplicate-action blocking.

### Changed

- Added `UserDictionaryEntryPolicy` for manual entry-operation gating, notice
  precedence, and save/delete result classification.
- Updated Settings -> Dictionary -> user dictionary entry editing to show save
  progress, delete progress, save success/failure, and delete success/failure
  cards.
- Moved manual user-dictionary insert, update, and delete DAO writes off the
  main thread.
- Refreshed affected suggestion overlays after successful manual mutations,
  including old and new locales when an edit changes the language tag.
- Blocked duplicate entry dialogs, list navigation, import/export actions, and
  back navigation while a manual entry mutation is running.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 14s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.149` / code `1949`.
- Roadmap, project context, improvement plan, and release index updated.
