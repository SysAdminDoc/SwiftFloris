# SwiftFloris v1.8.154

Date: 2026-05-18

## Keyboard Key Accessibility Semantics

This release continues the Accessibility Pass by aligning keyboard-key
semantics with the real key hitboxes. TalkBack focus now follows each key's
`touchBounds` instead of the smaller visual key surface, and accessibility
activation dispatches the same key event path used by normal touch input.

### Changed

- Added a dedicated semantics overlay for text keys using the key's real touch
  bounds while leaving the visual Snygg key surface unchanged.
- Added an accessibility click action that dispatches the key through the normal
  `InputEventDispatcher.sendDownUp(...)` path.
- Cleared duplicate child semantics from the visual key surface so TalkBack
  announces one target per key.
- Expanded localized special-key labels for clipboard, voice, keyboard mode,
  input-method, layout, and smartbar-control keys.
- Extracted a testable key-description overload so resource-backed labels can
  be verified without Android context setup.
- Added `KeyboardKeyAccessibilityTest` to pin printable-key hints, special-key
  labels, and touch-bounds semantics fallback behavior.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 22s.
- `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.KeyboardKeyAccessibilityTest`
  passed in 17s after moving the label contract behind testable resource
  lookup functions.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 3m 28s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.154` / code `1954`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
