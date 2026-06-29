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

- [ ] P1 — Bump Kotlin 2.4.0 → 2.4.20 to fix CVE-2026-53914
  Why: CVE-2026-53914 (MEDIUM, CVSS 6.7) is an unsafe-deserialization vulnerability in Kotlin compiler build cache metadata. Fixed in 2.4.20 with no API changes.
  Evidence: CVE-2026-53914 on OpenCVE/cvefeed.io; JetBrains Kotlin security support policy; `gradle/libs.versions.toml` `kotlin = "2.4.0"`.
  Touches: `gradle/libs.versions.toml` `kotlin` version, `ksp` version (verify KSP 2.3.9 compatibility with Kotlin 2.4.20), Compose compiler plugin version (derived from Kotlin).
  Acceptance: `kotlin = "2.4.20"` in version catalog; KSP version updated if needed; full unit/lint/assemble passes; public-doc version-pin checker passes; no regressions.
  Complexity: S

- [ ] P1 — Correct stale SLSA/SBOM claim in README v1.9.44 release note
  Why: README line 342 says v1.9.44 shipped SLSA/SBOM generation, but those workflows were deleted in commit `73dc7d15`. Readers parsing release notes for trust evidence will find a false claim.
  Evidence: `README.md:342`; absence of `.github/workflows/`; `docs/SECURITY.md:101` correctly disclaimed remote attestation.
  Touches: `README.md` v1.9.44 release bullet.
  Acceptance: the release note appends a correction (e.g. "later replaced by local-only release evidence") and does not claim active SLSA/SBOM capability.
  Complexity: S

- [ ] P1 — Update THREAT_MODEL.md to remove fixed allowMainThreadQueries() gap
  Why: THREAT_MODEL.md line 232 lists `allowMainThreadQueries()` as a known gap, but commit `765295b9` moved Room access to IO. Stale gap claims weaken the threat model's reliability.
  Evidence: `docs/THREAT_MODEL.md:232`; commit `765295b9`; test guard at `PersonalDictionaryEncryptionTest.kt:49`.
  Touches: `docs/THREAT_MODEL.md` §4 known-gaps table.
  Acceptance: the gap row is removed or replaced with a "fixed in v1.9.53+" note; the verification checklist at §5 reflects the current state.
  Complexity: S

- [ ] P1 — Unblock compileSdk 37: correct false AGP 9.3.0 prerequisite
  Why: `Roadmap_Blocked.md` says "AGP 9.3.0 is not yet available" as the blocker for compileSdk 37. AGP 9.2.0+ supports API 37 per the official compatibility table and release notes. This false blocker holds back Android 17 TextAttribute APIs, physical keyboard password behavior, and AboutLibraries 15.x.
  Evidence: AGP 9.2.0 release notes ("maximum API level that Android Gradle plugin 9.2 supports is API level 37.0"); `Roadmap_Blocked.md` lines 205-210, 65-69, 71-79.
  Touches: `Roadmap_Blocked.md` entries for AGP 9.3.0 bump, AboutLibraries 15.x, and CJKV TextAttribute APIs — all should have their blockers corrected or items moved back to `ROADMAP.md`.
  Acceptance: false blocker removed; unblocked items moved to active roadmap or executed directly.
  Complexity: S

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

- [ ] P1 - Fix live-doc integrity self-failure on the workflow-reference ban
  Why: the current local release/documentation gate fails before implementation work because `ROADMAP.md` contains the forbidden `.github/workflows/` literal while `scripts/check-live-doc-integrity.py` bans deleted workflow references.
  Evidence: `python scripts/check-live-doc-integrity.py` reports `ROADMAP.md:60`; `scripts/check-live-doc-integrity.py`; existing P1 stale SLSA/SBOM roadmap item.
  Touches: `ROADMAP.md` evidence wording for the stale SLSA/SBOM item and, only if needed, `scripts/check-live-doc-integrity.py` to keep the workflow ban precise.
  Acceptance: `python scripts/check-live-doc-integrity.py` passes without weakening the deleted-workflow guard; the stale SLSA/SBOM item still points to local-only release evidence.
  Complexity: S

- [ ] P1 - Make public markdown link integrity use tracked-file truth
  Why: `CONTRIBUTING.md` links to four docs that exist only as ignored local markdown, so a clean public clone has broken contributor onboarding while the current filesystem-based check can miss it.
  Evidence: `CONTRIBUTING.md`; `docs/LOCAL_VERIFICATION.md`, `docs/REPO_HYGIENE.md`, `docs/QA_CHECKLISTS.md`, and `docs/AUTOCORRECT_LIFECYCLE.md` are `tracked=False exists=True`; `scripts/check-live-doc-integrity.py`.
  Touches: `scripts/check-live-doc-integrity.py`, `CONTRIBUTING.md`, and either tracked replacements in already-allowed docs or removal/rewording of links to ignored docs.
  Acceptance: the integrity checker fails when README/CONTRIBUTING/tracked docs link to untracked local markdown; all current public markdown links resolve in a clean checkout.
  Complexity: S

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
