# SwiftFloris v1.8.158

Date: 2026-05-18

## Accessibility Manual QA Notes

This release closes the Accessibility Pass documentation item by adding the
manual checks future contributors should run when a change touches settings,
keyboard layout, or IME state feedback.

### Changed

- Added accessibility-specific manual QA notes to `CONTRIBUTING.md`.
- Expanded `docs/ACCESSIBILITY.md` with a concrete manual QA checklist covering
  settings traversal, keyboard labels, candidate-row announcements, high font
  scale, non-color state indicators, and theme/layout cross-checks.

### Tests

- Documentation-only change. `git diff --check` passed.
- `.\gradlew.bat :app:assembleDebug` passed in 17s.

### Definition of Done

- Version bumped to `1.8.158` / code `1958`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.
