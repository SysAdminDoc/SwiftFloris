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
  Note (2026-06-29 research): parser-side `parseWithDiagnostics()` shipped in `8986df14` for both `EspansoMatchParser` and `KlcLayoutParser` with `ImportDiagnostics` return type. Remaining: wire diagnostics into SnippetSettingsScreen and HardwareKeyboardLayoutImport UI surfaces, add malformed-fixture test files.

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

## Research-Driven Additions (2026-06-29)

### P1

### P2

- [ ] P2 — Bump compileSdk from 36 to 37
  Why: compileSdk 37 unlocks Android 17 `TextAttribute` for CJK IME accessibility, `show_passwords_physical` behavior verification, and the AboutLibraries 15.x upgrade path (`core-ktx:1.19.0` requires compileSdk 37).
  Evidence: AGP 9.2.0 release notes; Android 17 features page; `gradle.properties` `projectCompileSdk=36`.
  Touches: `gradle.properties` `projectCompileSdk`, verify `platforms/android-37` installed locally, run full test/lint/assemble.
  Acceptance: compileSdk 37 set; unit/lint/assemble pass; no new lint errors from API 37 exposure; public-doc version-pin checker updated if compileSdk is referenced in docs.
  Complexity: S

- [ ] P2 — Add scrollable/expanded suggestion strip mode
  Why: the current strip shows 3 fixed candidates; HeliBoard #2584, Reddit/Lemmy threads, and community feedback consistently request the ability to see more suggestions, either by scrolling or expanding the strip.
  Evidence: HeliBoard issue #2584; community posts on r/androidapps and Lemmy; FUTO Keyboard's multi-candidate row.
  Touches: `ime/smartbar/CandidatesRow.kt`, `ime/smartbar/Smartbar.kt`, Smartbar layout preferences, Roborazzi baselines.
  Acceptance: a preference-gated mode allows horizontal scrolling through more than 3 candidates; default behavior unchanged; TalkBack announces additional candidates; Roborazzi baseline covers the expanded state.
  Complexity: M

- [ ] P2 — Handle Android 17 IME visibility non-restoration after config changes
  Why: Android 17 no longer restores IME visibility after unhandled configuration changes (rotation). `FlorisImeService.onConfigurationChanged` may need to re-request the keyboard or explicitly handle the new default.
  Evidence: Android 17 behavior changes page ("The system no longer restores IME visibility after unhandled configuration changes"); `FlorisImeService.kt` `onConfigurationChanged`.
  Touches: `FlorisImeService.kt`, physical keyboard visibility tests.
  Acceptance: after rotation with API 37 target, the keyboard reappears correctly if it was previously shown; verified by policy test or manual check; no regression on API 36.
  Complexity: S

- [ ] P2 — Allowlist or migrate DictionaryManager.kt runBlocking
  Why: `DictionaryManager.kt:546` uses `runBlocking(Dispatchers.IO)` which is not in the CI allowlist at `scripts/runblocking-allowlist.txt`. The gate should either allowlist it with rationale or the code should migrate to a suspend path.
  Evidence: `DictionaryManager.kt:546`; `scripts/runblocking-allowlist.txt` (21 entries, none for DictionaryManager).
  Touches: `DictionaryManager.kt`, `scripts/runblocking-allowlist.txt`, or conversion to suspend function + caller updates.
  Acceptance: the `runBlocking` is either allowlisted with a documented rationale or replaced with a suspend call; the CI gate passes cleanly.
  Complexity: S

### P3

- [ ] P3 — Add Smartbar-only Roborazzi visual baseline
  Why: the Smartbar-only mode shipped in commit `a1e61050` with policy tests but no visual baseline. A committed Roborazzi snapshot prevents visual regressions in the compact hardware-keyboard surface.
  Evidence: `TextInputLayout.kt:68` smartbarOnly gate; `app/src/test/snapshots/` lacks a smartbar-only baseline.
  Touches: `app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/`, `app/src/test/snapshots/`, a `@RoboPreviewInclude` preview for the smartbar-only state.
  Acceptance: `:app:verifyRoborazziDebug` passes with a committed smartbar-only baseline; the baseline covers at least one theme.
  Complexity: S

## Research-Driven Additions

### P1

- [ ] P1 - Harden inline suggestion inflation against invalid host sizes
  Why: upstream FlorisBoard issue #3294 shows `InlineSuggestion.inflate` can crash on invalid size constraints, and SwiftFloris currently requests an unconstrained max size and forwards inline suggestions through the same platform path.
  Evidence: FlorisBoard issue #3294; `FlorisImeService.kt` `InlineSuggestionUiSmallestSize`/`InlineSuggestionUiBiggestSize`; `NlpInlineAutofill.showInlineSuggestions`; `InlineSuggestionsUi.kt`.
  Touches: `FlorisImeService.kt`, inline-autofill handling, `InlineSuggestionsUi.kt`, tests for malformed/oversized inline presentations.
  Acceptance: invalid inline suggestions are dropped with a warning/log signal rather than crashing the IME; tests cover invalid bounds and normal inline suggestions still render.
  Complexity: M

### P2

- [ ] P2 - Add glide endpoint-plausibility regression fixtures
  Why: FUTO issue #2120 shows real-world glide failures where language probability can surface words far outside the gesture endpoints; SwiftFloris has a scorecard and replay harness that can catch this without changing model architecture first.
  Evidence: FUTO Keyboard issue #2120; `scripts/glide-benchmark.py`; `scripts/typing-quality-scorecard.py`; `app/src/test/resources/swiftkey/replay/glide_context_cases.jsonl`; `SwiftKeyCandidateRankerTest.kt`.
  Touches: glide replay fixtures, `SwiftKeyCandidateRanker.kt`, `SwiftKeyCandidateRankerTest.kt`, `typing-quality-scorecard.py` thresholds if a new metric is added.
  Acceptance: fixtures cover endpoint-mismatch examples such as `mkv` not promoting `move` and `the` not losing to a far-endpoint candidate; the scorecard exposes endpoint-plausibility pass/fail without reducing existing top-4 rescue coverage.
  Complexity: M

- [ ] P2 - Add context-scoped next-word rejection
  Why: users need to reject a displayed next-word prediction for a specific preceding context without removing the word globally, matching FUTO issue #2117 and avoiding dictionary blacklisting side effects.
  Evidence: FUTO Keyboard issue #2117; `LearnedWordForgetSuggestionCandidate`; `CorrectionOutcomePriors.kt`; `PersonalBigramStore.kt`; `PersonalTrigramStore.kt`; `CandidatesRow.kt`.
  Touches: candidate long-press/removal UI, correction/rejection priors, personal n-gram stores, strings, candidate-ranker tests.
  Acceptance: a user can reject "word B after word A"; the word remains available elsewhere, but the rejected context is demoted in next-word ranking; tests prove rejection is context-scoped and reversible/clearable through existing learning-data controls.
  Complexity: M

- [ ] P2 - Add hostile-editor compatibility replay matrix
  Why: current competitor issue traffic clusters around host-editor failures (Teams no keyboard, Flutter undo/redo, Ren'Py enter/delete, Typst/OnlyOffice random replacement, cursor jumps), and SwiftFloris already has editor policy tests that can be extended before device-specific bugs escape.
  Evidence: FlorisBoard issues #3292, #3262, #3242, #3241; FUTO issues #2139, #2106; `HostileEditorCandidateReplayTest.kt`; `EditorInfoSensitiveFieldReplayTest.kt`.
  Touches: `app/src/test/kotlin/dev/patrickgold/florisboard/ime/editor/`, editor action/selection helpers, inline suggestion and hardware shortcut policies where needed.
  Acceptance: replay/policy tests model at least four hostile editor classes (rich editor, game engine, Flutter, desktop/physical keyboard) and prove commit, enter, delete, undo/redo, and selection behavior stay stable.
  Complexity: M
