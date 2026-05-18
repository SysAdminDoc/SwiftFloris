# SwiftFloris v1.8.151

Date: 2026-05-18

## Dictionary Transfer Busy States

This release closes the broad Workstream 5 disabled/busy-state pass by covering
the remaining dictionary transfer gap. User dictionary import/export now has a
first-class transfer state, visible progress cards, and duplicate-action
blocking while long-running file work is in flight.

### Changed

- Added dictionary transfer operation and notice states to
  `UserDictionaryEntryPolicy`.
- Added visible import/export progress cards to the user dictionary screen.
- Disabled dictionary navigation, import/export menu actions, system-manager
  launch, and manual entry mutations while dictionary transfer work is running.
- Moved plain dictionary import and dictionary export work into coroutine-backed
  IO blocks so the busy state covers the actual file and database work.
- Added focused JVM coverage for dictionary transfer gating and notice
  precedence.
- Marked the Workstream 5 duplicate-action/busy-state task complete in the
  improvement plan.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 20s.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 41s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.151` / code `1951`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
