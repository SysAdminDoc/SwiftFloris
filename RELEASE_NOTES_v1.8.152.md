# SwiftFloris v1.8.152

Date: 2026-05-18

## Settings Focus Order

This release begins the Accessibility Pass by giving the shared Settings
scaffold an explicit traversal order for TalkBack and keyboard navigation.
Settings screens now expose app bar controls first, scrollable content second,
persistent bottom actions third, and floating actions last.

### Changed

- Added `FlorisScreenFocusOrder` constants for the shared Settings scaffold.
- Marked the scaffold root as a traversal group while preserving its existing
  pane-title announcement behavior.
- Applied traversal indices to the app bar, content container, bottom bar, and
  floating action slot.
- Let `FlorisAppBar` accept a modifier so the shared screen scaffold can attach
  accessibility traversal semantics centrally.
- Suppressed the existing Android bar-color deprecation warnings inside the
  compatibility `SideEffect` block so this accessibility slice does not expand
  the warning surface.
- Added `FlorisScreenFocusOrderTest` to pin the traversal order.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 9s after the compatibility warning suppression.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 54s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.152` / code `1952`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
