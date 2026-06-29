# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P2

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

- [ ] P2 — Add scrollable/expanded suggestion strip mode
  Why: the current strip shows 3 fixed candidates; HeliBoard #2584, Reddit/Lemmy threads, and community feedback consistently request the ability to see more suggestions, either by scrolling or expanding the strip.
  Evidence: HeliBoard issue #2584; community posts on r/androidapps and Lemmy; FUTO Keyboard's multi-candidate row.
  Touches: `ime/smartbar/CandidatesRow.kt`, `ime/smartbar/Smartbar.kt`, Smartbar layout preferences, Roborazzi baselines.
  Acceptance: a preference-gated mode allows horizontal scrolling through more than 3 candidates; default behavior unchanged; TalkBack announces additional candidates; Roborazzi baseline covers the expanded state.
  Complexity: M

### P3

- [ ] P3 — Add Smartbar-only Roborazzi visual baseline
  Why: the Smartbar-only mode shipped in commit `a1e61050` with policy tests but no visual baseline. A committed Roborazzi snapshot prevents visual regressions in the compact hardware-keyboard surface.
  Evidence: `TextInputLayout.kt:68` smartbarOnly gate; `app/src/test/snapshots/` lacks a smartbar-only baseline.
  Touches: `app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/`, `app/src/test/snapshots/`, a `@RoboPreviewInclude` preview for the smartbar-only state.
  Acceptance: `:app:verifyRoborazziDebug` passes with a committed smartbar-only baseline; the baseline covers at least one theme.
  Complexity: S

## Research-Driven Additions

### P1

### P2

- [ ] P2 - Add context-scoped next-word rejection
  Why: users need to reject a displayed next-word prediction for a specific preceding context without removing the word globally, matching FUTO issue #2117 and avoiding dictionary blacklisting side effects.
  Evidence: FUTO Keyboard issue #2117; `LearnedWordForgetSuggestionCandidate`; `CorrectionOutcomePriors.kt`; `PersonalBigramStore.kt`; `PersonalTrigramStore.kt`; `CandidatesRow.kt`.
  Touches: candidate long-press/removal UI, correction/rejection priors, personal n-gram stores, strings, candidate-ranker tests.
  Acceptance: a user can reject "word B after word A"; the word remains available elsewhere, but the rejected context is demoted in next-word ranking; tests prove rejection is context-scoped and reversible/clearable through existing learning-data controls.
  Complexity: M
