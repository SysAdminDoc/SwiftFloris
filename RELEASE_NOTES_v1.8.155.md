# SwiftFloris v1.8.155

Date: 2026-05-18

## Dynamic Font Scaling For Settings And Dialogs

This release continues the Accessibility Pass by giving compact settings and
theme-dialog surfaces more room at high font scale. Metadata rows, links,
component headings, and key-preview boxes keep their compact layout at normal
font scale, then expand wrapping room or minimum preview size when the user has
large text enabled.

### Changed

- Added `DynamicFontScale` as a shared Compose-side policy for high-font-scale
  line-count and minimum-size expansion.
- Allowed shared hyperlink text to expand from one to two lines at high font
  scale instead of always truncating after one line.
- Allowed extension metadata labels, metadata values, and component headings to
  gain extra wrapping room at high font scale while preserving compact defaults.
- Expanded the theme-rule key-data preview from 36 dp to 48 dp at high font
  scale and allowed text labels inside the preview to wrap when expanded.
- Added `DynamicFontScaleTest` to pin the expansion threshold and defensive
  clamping behavior.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 15s after final import-order cleanup.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 33s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.155` / code `1955`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
