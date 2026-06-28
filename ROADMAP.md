# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P2

- [ ] P2 — Surface skipped-record diagnostics for snippet and layout imports
  Why: competitor/community issue traffic shows import/migration/layout quality matters, and SwiftFloris dictionary import already reports skipped counts while Espanso snippets and hardware layout parsers can silently tolerate malformed lines.
  Evidence: `EspansoMatchParser.kt`; `KlcLayoutParser.kt`; `SwipeTraceImporter.kt`; `PersonalDictionaryImportSummaryDialog.kt`; HeliBoard issue traffic around imports/customization.
  Touches: `ime/snippet/`, `app/settings/typing/SnippetSettingsScreen.kt`, `ime/hardware/`, relevant parser tests and string resources.
  Acceptance: snippet/layout imports return parsed entries plus skipped/malformed diagnostics; UI shows a calm summary with a copy/exportable diagnostic detail; malformed fixtures prove partial imports remain safe and transparent.
  Complexity: M

- [ ] P2 — Expand Roborazzi baselines for new settings surfaces
  Why: recent user-facing screens landed without matching committed visual baselines, while existing baselines cover only selected pending settings and addon/theme surfaces.
  Evidence: `Routes.kt` includes CustomLayoutEditor, SnippetSettings, PrivacyAuditLog, Sync, Backup, Restore; `app/src/test/snapshots/` lacks those screen baselines.
  Touches: `app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/`, `app/src/test/snapshots/`, new `@RoboPreviewInclude` previews where appropriate.
  Acceptance: Roborazzi baselines cover custom layout editor, snippets, privacy audit, sync, backup, and restore in dark and high-contrast-relevant states; `:app:verifyRoborazziDebug` passes.
  Complexity: M

### P2

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
