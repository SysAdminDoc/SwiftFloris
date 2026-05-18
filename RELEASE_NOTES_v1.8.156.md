# SwiftFloris v1.8.156

Date: 2026-05-18

## Theme Contrast Verification

This release continues the Accessibility Pass by turning theme contrast into a
broader regression contract. Bundled IME stylesheets now have selector-level
coverage for keyboard keys, candidate rows, and clipboard clear-all dialogs,
and settings warning/error/dialog colors are checked across every predefined
accent scheme.

### Changed

- Expanded `ThemeContrastTest` to check keyboard, candidate-row, and
  clipboard-dialog foreground/background pairs across every bundled stylesheet.
- Added settings warning-card, error-card, and dialog contrast checks for light,
  dark, and AMOLED schemes across every predefined accent color.
- Fixed low-contrast enter-key foregrounds in Floris Night, Floris Pure Night,
  Dracula, Nord, and Tokyo Night theme variants.
- Raised shared card secondary-copy opacity so warning/error card body text
  remains at WCAG AA contrast.

### Tests

- `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.theme.ThemeContrastTest`
  passed in 11s after fixing discovered contrast gaps.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 30s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.156` / code `1956`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
