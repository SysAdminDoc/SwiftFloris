# SwiftFloris v1.8.153

Date: 2026-05-18

## Candidate And Smartbar TalkBack Labels

This release continues the Accessibility Pass by making high-frequency
keyboard controls speak with more context. Prediction-strip candidates now
announce their suggestion type, position, and text, while quick actions use a
single fallback policy that prefers the visible display name, then the tooltip,
then a generic smartbar action label.

### Changed

- Added `SmartbarAccessibilityLabels` as the shared policy for candidate and
  smartbar quick-action labels.
- Candidate row accessibility labels now include list position, total count,
  clipboard status, autocorrect status, and candidate text.
- Preserved the remove-candidate custom accessibility action through the shared
  label constant.
- Routed quick-action content descriptions through the shared display-name /
  tooltip fallback policy.
- Added `SmartbarAccessibilityLabelsTest` to pin candidate label formatting,
  invalid-position clamping, and quick-action fallback behavior.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 28s.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 38s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.153` / code `1953`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
