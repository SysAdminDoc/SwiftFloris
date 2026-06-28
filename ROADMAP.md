# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P2

- [ ] P2 — Bump Gradle wrapper to 9.6.1 with checksum verification
  Why: Gradle 9.6.1 is current and provides the latest build-tool fixes while the repo remains on 9.5.1.
  Evidence: `gradle/wrapper/gradle-wrapper.properties`; `https://services.gradle.org/versions/current`.
  Touches: `gradle/wrapper/gradle-wrapper.properties`, `docs/REPRODUCIBLE_BUILDS.md`, local verification docs.
  Acceptance: wrapper distribution URL and SHA-256 update together; wrapper validation/build/test/lint/assemble pass with JDK 21; reproducible-build documentation reflects the new pin.
  Complexity: S

- [ ] P2 — Surface skipped-record diagnostics for snippet and layout imports
  Why: competitor/community issue traffic shows import/migration/layout quality matters, and SwiftFloris dictionary import already reports skipped counts while Espanso snippets and hardware layout parsers can silently tolerate malformed lines.
  Evidence: `EspansoMatchParser.kt`; `KlcLayoutParser.kt`; `SwipeTraceImporter.kt`; `PersonalDictionaryImportSummaryDialog.kt`; HeliBoard issue traffic around imports/customization.
  Touches: `ime/snippet/`, `app/settings/typing/SnippetSettingsScreen.kt`, `ime/hardware/`, relevant parser tests and string resources.
  Acceptance: snippet/layout imports return parsed entries plus skipped/malformed diagnostics; UI shows a calm summary with a copy/exportable diagnostic detail; malformed fixtures prove partial imports remain safe and transparent.
  Complexity: M

- [ ] P2 — Replace Snygg URI resolver stub with typed failure handling
  Why: theme asset resolution should fail predictably; returning `NotImplementedError` from the default resolver is a user-facing reliability footgun if a theme path reaches it.
  Evidence: `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt:97`.
  Touches: `lib/snygg/`, theme import/rendering tests, theme editor error copy.
  Acceptance: no `NotImplementedError` is used for normal resolver failure; default resolver returns a typed unsupported-path result with tests; theme import/rendering surfaces actionable copy instead of an implementation-stub error.
  Complexity: S

- [ ] P2 — Expand Roborazzi baselines for new settings surfaces
  Why: recent user-facing screens landed without matching committed visual baselines, while existing baselines cover only selected pending settings and addon/theme surfaces.
  Evidence: `Routes.kt` includes CustomLayoutEditor, SnippetSettings, PrivacyAuditLog, Sync, Backup, Restore; `app/src/test/snapshots/` lacks those screen baselines.
  Touches: `app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/`, `app/src/test/snapshots/`, new `@RoboPreviewInclude` previews where appropriate.
  Acceptance: Roborazzi baselines cover custom layout editor, snippets, privacy audit, sync, backup, and restore in dark and high-contrast-relevant states; `:app:verifyRoborazziDebug` passes.
  Complexity: M

- [ ] P2 — Add live-doc canonical-source and release-state integrity check
  Why: contributor-facing docs still reference missing canonical files and stale release-state labels, which is separate from dependency-version drift and can mislead autonomous agents, contributors, and release reviewers.
  Evidence: `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and `.github/workflows` are absent; `CONTRIBUTING.md`, `docs/REPO_HYGIENE.md`, `docs/QA_CHECKLISTS.md`, `.github/PULL_REQUEST_TEMPLATE.md`, and `README.md` still reference those paths or stale v1.9.52 release-state text.
  Touches: `CONTRIBUTING.md`, `README.md`, `docs/REPO_HYGIENE.md`, `docs/QA_CHECKLISTS.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `scripts/`.
  Acceptance: a local checker scans live non-archive Markdown for missing local-file links and forbidden canonical-source references; docs route contributors to the actual canonical sources (`README.md`, `ROADMAP.md`, `RESEARCH.md`, fastlane changelogs, and `Roadmap_Blocked.md`); README current-release/highlight labels are verified against `gradle.properties` and fastlane changelog files; archive/historical docs are excluded intentionally.
  Complexity: S

### P2

- [ ] P2 - Add physical-keyboard Smartbar-only mode
  Why: desktop and USB/Bluetooth keyboard users still need suggestions, clipboard, undo/redo, language switching, and touch-keyboard toggle actions even when the touch keyboard itself is hidden.
  Evidence: FUTO Keyboard PR #2138 and issue #2137; `FlorisImeService.onEvaluateInputViewShown()` and `PhysicalKeyboardPolicy.inputViewVisibilityDecision()` currently suppress the whole input view when hardware keyboard is available.
  Touches: `FlorisImeService.kt`, `PhysicalKeyboardPolicy.kt`, Smartbar/action layout components, physical-keyboard settings, policy tests, IME window tests.
  Acceptance: with a hardware keyboard and show-on-screen-keyboard disabled, SwiftFloris can show a compact Smartbar/action surface without the touch keyboard; a visible toggle restores the full touch keyboard; pure policy tests cover preference on/off, no hardware keyboard, hardware keyboard, and desktop/tablet form-factor cases.
  Complexity: M

- [ ] P2 - Make custom layouts row-count-aware with stable popup anchoring
  Why: 4-row custom layouts are common for minority-language and number-row workflows, and competitor issue traffic shows fixed-height shrinkage plus shifted long-press popups break muscle memory.
  Evidence: HeliBoard issues #2542 and #2543; `CustomLayoutEditorPolicy.kt` supports adding rows, while `TextKeyboard.kt` lays runtime rows into the current keyboard height without an explicit row-count sizing contract.
  Touches: `app/settings/keyboard/CustomLayoutEditorPolicy.kt`, `ime/text/keyboard/TextKeyboard.kt`, `TextKeyboardLayout.kt`, popup mapping/layout tests, custom-layout editor UI tests.
  Acceptance: 3-row layouts preserve current sizing; 4-row custom layouts gain proportional height or an explicit per-layout height policy; popup mappings remain anchored to their intended base keys; tests cover 3-row, 4-row, number-row, and popup-origin behavior.
  Complexity: M

### P3

- [ ] P3 - Add offline sticker-pack import and share-to-sticker creation
  Why: SwiftFloris already supports SAF sticker folders, but users coming from proprietary keyboards expect quick sticker creation and portable local sticker packs without GIF/network dependencies.
  Evidence: HeliBoard issue #2587; `ime/media/sticker/` user-imported folder support; README sticker posture.
  Touches: `ime/media/sticker/`, media settings, SAF import/export helpers, MIME validation, backup/restore inclusion, sticker tests and docs.
  Acceptance: users can share a local image into SwiftFloris as a sticker and import/export a local sticker-pack archive with a small manifest; unsupported/oversized files fail with diagnostics; no network permission or remote catalog is introduced.
  Complexity: M
