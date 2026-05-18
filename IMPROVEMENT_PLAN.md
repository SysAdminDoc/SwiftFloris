# SwiftFloris Improvement Plan

Last updated: 2026-05-18

This plan tracks quality, UX, accessibility, performance, testing, and delivery improvements that sit beside the product roadmap. It is intentionally execution-focused: every item should end in code, tests, docs, or release-process changes.

## Current Baseline

- Branch: `master`; completed release slices are committed, tagged, and pushed.
- Main Kotlin files: 245.
- Test Kotlin files: 22.
- Latest verified commands:
  - `./gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`
  - `./gradlew.bat :app:installDebug`
  - adb launch smoke for `dev.patrickgold.florisboard.debug/dev.patrickgold.florisboard.SettingsLauncherAlias`
- Known worktree condition: unrelated deleted markdown files are present and must not be staged unless explicitly requested.
- Initial lint shape when this plan started: 324 warnings, 2 hints.
- Current lint shape after trust-state batches: 246 warnings, 1 hint. Largest remaining bucket is still `UnusedResources`; the remaining bucket is dominated by string resources and theme palette/spec files that need product-copy or theme-contract review before removal.
- Current compile-warning focus: touched backup/restore, extension import/export/view, extension archive file management, dictionary import/export/manual entry mutation, and language pack delete deprecated toast warnings are cleared. Remaining known warning themes are the Room nullable DAO type, Kotlin compiler flags, and deprecated synchronous toast calls in theme, devtools, keyboard, and clipboard surfaces.

## Current Improvement Assessment

- Keyboard correctness is the highest-risk product surface. Autocorrect, backspace rejection, punctuation commits, phantom spacing, glide delete, and hardware keyboard paths need explicit contracts plus JVM coverage.
- Backup, restore, extension, language pack, dictionary, and theme workflows are trust-sensitive. They need clear busy states, specific errors, recovery copy, duplicate-action blocking, and post-action confirmation.
- Settings are feature-rich and need stronger information architecture: clearer section summaries, consistent secondary text, better empty states, and predictable destructive confirmations.
- The keyboard surface needs a dedicated accessibility and polish pass for candidate row semantics, smartbar controls, touch targets, contrast, and state labels.
- Lint signal is improving, but the remaining `UnusedResources` bucket still hides real regressions. Further cleanup must stay conservative because translated strings, theme palette test fixtures, and build variants can look unused to lint.
- Compile warnings show older synchronous feedback APIs in several UI surfaces. Replacing them with coroutine-safe feedback removes UI-thread risk and improves consistency.
- CI and release verification should match the local path: lint, unit tests, assemble, optional adb install/launch, lint-baseline drift, and dependency review.
- Performance quality is currently under-instrumented. Keyboard cold start, first render, first suggestion latency, dictionary load, candidate recomposition, and backup/restore durations need repeatable measurements.

## Priority Model

- P0: protects core keyboard correctness, user trust, release safety, or crash prevention.
- P1: improves maintainability, accessibility, state clarity, or performance evidence.
- P2: polish, cleanup, and longer-term architecture work.

## Progress Log

- 2026-05-05: Created this plan and began the first implementation slice: small lint/testability cleanup with no product behavior change intended.
- 2026-05-05: First lint cleanup batch removed invalid manifest alias attributes, closed a Han suggestion cursor, resolved Compose naming warnings, and cleared efficiency hints for integer state and zero-filled arrays.
- 2026-05-05: Second lint cleanup batch migrated simple URI/preference calls to KTX APIs, fixed a translated crash-report format mismatch, normalized reported ellipses, and corrected high-confidence German, Portuguese, and Spanish translation typos. Remaining typo warnings are intentional Turkish repeated-word phrases.
- 2026-05-05: Third lint cleanup batch added translation-safe `many` plural fallbacks for affected Catalan, French, Italian, Portuguese, Brazilian Portuguese, and Spanish unit strings.
- 2026-05-05: Expanded this plan into a broader product-quality tracker covering keyboard correctness, UX polish, accessibility, trust states, localization, privacy, performance, CI, and release hygiene.
- 2026-05-05: Began build-warning hygiene by moving backup/restore success and failure feedback off deprecated synchronous toast calls.
- 2026-05-05: Continued build-warning hygiene by moving extension import/export/delete feedback off deprecated synchronous long toasts and giving extension export a concrete MIME type.
- 2026-05-05: Moved user dictionary import/export feedback off deprecated synchronous long toasts and gave dictionary export a concrete text MIME type.
- 2026-05-05: Moved language pack delete failure feedback off deprecated synchronous long toasts and switched the language pack import action to the AutoMirrored icon.
- 2026-05-05: Began the premium UX polish pass by tightening the shared settings shell, action-card affordances, setup step completion treatment, bottom-bar insets, backup/restore busy-state locking, extension empty-state actions, and language-pack manager empty/detail behavior.
- 2026-05-18: Extracted editor input behavior decisions into a pure policy and added JVM coverage for autocorrect accept/reject spacing, punctuation auto-spacing, phantom spacing, double-space period, and sentence-capitalization gates.
- 2026-05-18: Extracted hardware keyboard routing decisions into a pure policy and added JVM coverage for space, enter, delete pass-through, shift, mapped letters, mapped punctuation, and punctuation-triggered autocorrect flush behavior.
- 2026-05-18: Added JVM lifecycle coverage for auto-space and phantom-space state transitions, including editor-update grace, composing-region visibility, and candidate-for-revert cleanup.
- 2026-05-18: Extracted glide-backspace escalation into the editor input policy and added JVM coverage for immediate word-delete, disabled preference, inactive phantom-space, and explicit word-delete paths.
- 2026-05-18: Extracted incognito suggestion privacy decisions into a pure policy and added JVM coverage for app-declared privacy override, dynamic toggle availability, learning gates, and touch-decoder evidence suppression.
- 2026-05-18: Extracted backup/restore validation and operation-state decisions into a pure policy, rejecting archives with no restorable content and adding JVM coverage for backup success/cancellation/failure, restore invalid archives, enablement, and partial failures.
- 2026-05-18: Extracted extension import readiness decisions into a pure policy and added JVM coverage for language pack import/update, bundled-core rejection, corrupted metadata, wrong extension type, and import button enablement.
- 2026-05-18: Extracted subtype editor draft validation into a pure policy and added JVM coverage for default add-state missing fields, complete draft building, placeholder rejection, and edit-state preservation.
- 2026-05-18: Extracted theme component metadata validation into a pure policy and added JVM coverage for valid apply normalization, invalid fields, duplicate IDs, and blank stylesheet fallback.
- 2026-05-18: Completed the first conservative `UnusedResources` review by deleting only obsolete launcher/branding resources and legacy color tokens with no code, manifest, asset, test, or dynamic lookup references. Lint dropped from 289 warnings / 1 hint to 245 warnings / 1 hint; remaining `UnusedResources` entries are string, theme-palette, or spec-dimension buckets requiring separate semantic review.
- 2026-05-18: Reviewed dependency-version lint warnings as a dedicated dependency slice and bumped Gradle 9.4.1 -> 9.5.1, Navigation Compose 2.9.7 -> 2.9.8, and JUnit Vintage 5.13.1 -> 6.0.3 after checking official Gradle, AndroidX, and JUnit release metadata. Lint dropped to 241 warnings / 1 hint.
- 2026-05-18: Extracted candidate auto-commit ordering, quick-prediction spacebar selection, and rejected-correction gating into `CandidateAutoCommitPolicy`, leaving `NlpManager` to gather Android-bound state and adding focused JVM coverage for disabled states, shortcut/phrase/active/immediate priority, language-confidence gating, rejection suppression, and plain-space prediction suppression.
- 2026-05-18: Extracted software punctuation/non-letter autocorrect flush decisions into `KeyboardAutoCommitFlushPolicy`, leaving `KeyboardManager` to execute the chosen flush and adding JVM coverage for media mode, alphabetic keys, punctuation keys, numeric keys, numeric/phone layouts, non-text keys, and empty text.
- 2026-05-18: Extracted theme rule editing validation and key-code parsing into `ThemeRuleEditPolicy`, leaving `EditRuleDialog` to render dialog state and adding JVM coverage for empty add-rule selection, selector toggling, invalid/duplicate/unchanged code decisions, and add/replace code actions.
- 2026-05-18: Codified the autocorrect lifecycle in `docs/AUTOCORRECT_LIFECYCLE.md`, moved accepted-candidate provider notifications behind successful editor commits with `CandidateCommitSideEffectPolicy`, and mapped regression/manual QA coverage for spacebar, punctuation, hardware, glide delete, and backspace rejection behavior.
- 2026-05-18: Audited the backup flow trust states by adding explicit progress, cancellation, share-sheet handoff, failure, and clipboard privacy warning cards, plus `BackupFlowNotice` policy coverage for notice ordering.
- 2026-05-18: Audited the restore flow trust states by adding erase-mode confirmation, progress/cancellation/failure/partial-failure cards, recovery-copy guidance, duplicate-action blocking, and `RestoreFlowNotice` policy coverage.
- 2026-05-18: Audited language pack import/update/remove states by adding import preparation/importing/cancel/failure cards, install/update/skipped-file counts, delete progress/success/failure cards, duplicate-action blocking, and focused policy coverage.
- 2026-05-18: Audited theme import/edit/delete states by adding theme-extension save progress/failure cards, confirmed component removal with draft-state feedback, installed extension delete progress/failure cards, duplicate-action blocking, and focused policy coverage.
- 2026-05-18: Audited extension import/edit/delete states by adding extension archive file progress/success/failure cards, async file import/rename/delete work, duplicate-action blocking, and focused policy coverage.
- 2026-05-18: Audited dictionary add/update/remove states by adding manual entry progress/success/failure cards, async DAO writes, duplicate-action blocking, and focused policy coverage.
- 2026-05-18: Added calm recovery-path copy to backup, restore, extension import/edit/delete, archive-file, language-pack delete, and manual dictionary entry failure cards so visible errors explain what stayed unchanged and what to retry.
- 2026-05-18: Closed the Workstream 5 duplicate-action/busy-state pass by adding dictionary import/export transfer state, visible transfer progress cards, off-main-thread transfer work, disabled navigation/menu/entry actions during transfers, and focused policy coverage.
- 2026-05-18: Began the Accessibility Pass by giving the shared settings scaffold an explicit traversal order: app bar, content, bottom actions, then floating action, with a regression test pinning the order.
- 2026-05-18: Audited candidate row and smartbar TalkBack labels by extracting `SmartbarAccessibilityLabels`, announcing candidate type/position/text, preserving the remove-candidate custom action label, and pinning quick-action label fallback behavior in JVM tests.

## Workstreams

### 1. Test Coverage Expansion

Status: Completed
Priority: P0

Goal: Raise confidence in the keyboard's highest-risk behavior by moving state rules into testable units and adding regression coverage.

Tasks:
- [x] Add JVM coverage for rejected autocorrect behavior.
- [x] Add editor/input behavior tests for autocorrect accept, reject, undo, punctuation, and spacing flows.
- [x] Add hardware keyboard tests for space, enter, delete, and punctuation behavior.
- [x] Add phantom-space and autospace lifecycle tests.
- [x] Add glide typing delete interaction tests.
- [x] Add incognito suggestion behavior tests.
- [x] Add backup and restore tests for success, cancellation, invalid archive, and partial failure cases.
- [x] Add language pack import/update tests.
- [x] Add subtype editor validation tests.
- [x] Add theme editor validation tests.

Acceptance criteria:
- Core keyboard correction behavior can be verified with JVM tests without requiring adb.
- New bug fixes include regression tests.
- High-risk state machines have direct tests rather than only UI or integration coverage.

### 2. Lint Debt Reduction

Status: In progress
Priority: P0

Goal: Reduce lint noise so new regressions stand out and release gates are easier to trust.

Tasks:
- [x] Fix small source-level warnings first: `Recycle`, `UnnecessaryArrayInit`, `AutoboxingStateCreation`, `ComposableNaming`, `InvalidManifestAttribute`.
- [x] Fix KTX warnings where the dependency surface already supports the suggested APIs.
- [x] Fix `StringFormatCount` in translated resources without changing source-string intent.
- [x] Normalize `TypographyEllipsis` in string resources.
- [x] Audit `Typos` warnings and correct only cases with high confidence.
- [x] Address `MissingQuantity` by adding required plural quantities, preferably via translation-safe fallback copying.
- [x] Review `UnusedResources`; delete only resources proven unused across build variants and dynamic lookup paths.
- [x] Review dependency version warnings separately from source cleanup.

Acceptance criteria:
- Lint warning count decreases monotonically across cleanup batches.
- No broad suppressions are added for fixable warnings.
- Locale/resource fixes preserve formatting placeholders and product meaning.

### 3. Pure Core Extraction

Status: Completed
Priority: P0

Goal: Make keyboard behavior easier to test and safer to change by separating Android framework wiring from deterministic rules.

Tasks:
- [x] Extract autocorrect suppression state to a pure JVM-testable class.
- [x] Extract candidate auto-commit eligibility and rejection policy from `NlpManager`.
- [x] Extract punctuation-triggered commit rules from `KeyboardManager`.
- [x] Extract phantom-space/autospace rules from `EditorInstance`.
- [x] Extract backup/restore validation policy from Compose screens.
- [x] Extract theme validation and rule parsing from UI surfaces.

Acceptance criteria:
- Extracted classes have no Android framework dependency unless unavoidable.
- Manager classes become thinner orchestration layers.
- Each extracted rule set has focused JVM tests.

### 4. Input Behavior Hardening

Status: Completed
Priority: P0

Goal: Make typing behavior predictable, especially around corrections, manual overrides, punctuation, hardware keyboards, and destructive edits.

Tasks:
- [x] Respect user backspace rejection after an automatic correction.
- [x] Define a written contract for autocorrect lifecycle behavior.
- [x] Make suggestion acceptance, rejection, and provider notification semantics explicit.
- [x] Audit punctuation and non-letter commit behavior across software and hardware input.
- [x] Audit delete behavior with glide setting `immediateBackspaceDeletesWord`.
- [x] Add manual QA scripts or adb notes for preview-field typing checks.
- [x] Add regression tests for every fixed input behavior.

Acceptance criteria:
- User correction rejection is stable across software and hardware entry points.
- Space/punctuation behavior is documented and covered.
- Manual overrides are never silently overwritten in the same word.

### 5. User-Facing Trust States

Status: In progress
Priority: P1

Goal: Make high-risk workflows communicate clearly, recover gracefully, and avoid silent failure.

Tasks:
- [x] Start replacing deprecated synchronous toast feedback in backup/restore flows with coroutine-safe feedback.
- [x] Replace deprecated synchronous toast feedback in extension import/export/view flows.
- [x] Replace deprecated synchronous toast feedback in dictionary import/export flows.
- [x] Replace deprecated synchronous toast feedback in language pack delete flow.
- [x] Audit backup flow for loading, success, warning, failure, and cancellation states.
- [x] Audit restore flow for destructive confirmation, progress, partial failure, and recovery copy.
- [x] Audit language pack import/update/remove states.
- [x] Audit theme import/edit/delete states.
- [x] Audit extension import/edit/delete states.
- [x] Audit dictionary add/remove states.
- [x] Add calm, specific microcopy for failure and recovery paths.
- [x] Add disabled/busy states where repeated actions could corrupt state or confuse users.

Acceptance criteria:
- Every destructive or long-running workflow has explicit feedback.
- Error states include a recovery path.
- Busy states prevent duplicate execution.

### 6. Accessibility Pass

Status: Planned
Priority: P1

Goal: Treat accessibility as part of product quality, not post-release cleanup.

Tasks:
- [x] Audit settings screen focus order.
- [x] Audit candidate row and smartbar TalkBack labels.
- [ ] Audit keyboard key semantics and touch target size.
- [ ] Verify dynamic font scaling on settings and dialogs.
- [ ] Verify theme contrast for keyboard, candidate row, dialogs, and warnings.
- [ ] Ensure state indicators do not rely on color alone.
- [ ] Add accessibility notes to manual QA checklist.

Acceptance criteria:
- Main settings workflows can be navigated predictably with accessibility services.
- High-frequency keyboard controls have meaningful descriptions.
- Theme contrast issues are documented or fixed.

### 7. Performance Instrumentation

Status: Planned
Priority: P1

Goal: Add lightweight evidence around typing responsiveness and startup quality.

Tasks:
- [ ] Measure keyboard cold start and first render.
- [ ] Measure first suggestion latency.
- [ ] Measure dictionary load and preload time.
- [ ] Measure candidate row recomposition hotspots.
- [ ] Measure theme switching cost.
- [ ] Measure backup/restore duration on representative archives.
- [ ] Add repeatable profiling notes using adb, simpleperf, Perfetto, or Compose tracing where appropriate.

Acceptance criteria:
- Performance claims are backed by repeatable commands.
- Slow paths have before/after numbers when changed.
- Instrumentation does not add user-facing overhead.

### 8. CI Quality Gates

Status: Planned
Priority: P1

Goal: Keep quality checks repeatable and visible before release.

Tasks:
- [ ] Review existing GitHub Actions Android workflow.
- [ ] Ensure unit tests run on pull requests.
- [ ] Ensure `assembleDebug` or equivalent build runs on pull requests.
- [ ] Ensure lint runs with baseline drift detection.
- [ ] Add dependency/version review as a scheduled or manual check.
- [ ] Evaluate emulator smoke feasibility for key settings launch flow.
- [ ] Document local verification commands used before commit/push.

Acceptance criteria:
- CI matches the local verification path closely enough to catch common regressions.
- Lint baseline changes are intentional and visible.
- Release-critical checks are not only manual.

### 9. Repo Hygiene

Status: In progress
Priority: P0

Goal: Keep commits and release state understandable.

Tasks:
- [ ] Resolve whether the currently deleted markdown files are intentional before the next push.
- [ ] Keep future commits scoped to one logical improvement.
- [x] Add root `ARCHITECTURE.md` and `CONTRIBUTING.md` entry points for
  package/runtime orientation and contributor verification expectations.
- [x] Move root multilingual / voice guides into `docs/` and update internal
  links.
- [x] Keep JVM crash/replay logs out of the repo root and committed history
  (v1.8.73 moved local logs to `.ai/local-crash-logs/2026-05-16/` and added
  `scripts/check-no-root-crash-logs.sh` to CI).
- [ ] Keep generated reports/build output out of commits.
- [ ] Track improvement work in this file.
- [ ] Keep verification commands in final handoffs.

Acceptance criteria:
- No unrelated deleted docs are staged accidentally.
- Each commit has a clear purpose and matching verification.
- Project state can be resumed from this plan.

### 10. Product UX and Visual Polish

Status: In progress
Priority: P1

Goal: Make settings, onboarding, dialogs, and keyboard-adjacent UI feel more coherent, modern, calm, and easier to scan.

Tasks:
- [x] Audit first-run/setup flow for orientation, progress, permission clarity, IME enablement, and final success state.
- [x] Audit settings landing page hierarchy, section naming, secondary text, and action discoverability.
- [x] Normalize screen-level spacing, section density, and button placement across settings screens.
- [x] Normalize bottom-bar, empty-state, warning, error, and info-card treatment in the first shared pass.
- [x] Review primary/secondary/destructive action treatment across backup, restore, extension, language pack, and dictionary flows.
- [ ] Continue dialog copy and destructive-confirmation review across theme and extension editing.
- [ ] Add consistent loading/skeleton or progress affordances where files, extensions, or language packs are being scanned.
- [ ] Improve empty states for dictionary, extension lists, language packs, clipboard, and theme lists.
- [ ] Review keyboard preview field placement and state feedback in settings screens.
- [ ] Create a visual QA checklist for phone portrait, phone landscape, compact mode, floating mode, dark theme, and high font scale.

Acceptance criteria:
- Main settings flows use consistent hierarchy, labels, and action placement.
- Empty and error states explain what happened and what the user can do next.
- Visual QA notes identify remaining large design changes before they are attempted.

### 11. Keyboard Surface Polish

Status: Planned
Priority: P0

Goal: Make the typing surface feel predictable, accessible, and intentional.

Tasks:
- [ ] Audit candidate row visual hierarchy, selection, pressed, disabled, and correction states.
- [ ] Audit smartbar action ordering, icon semantics, overflow behavior, and long-label resilience.
- [ ] Audit software-key pressed/held/disabled/gesture states.
- [ ] Audit one-handed, floating, split, compact, landscape, and tablet layouts.
- [ ] Audit autocorrect toggle behavior; replace placeholder feedback with a completed or hidden affordance.
- [ ] Verify manual correction override behavior in real input fields after the autocorrect fix.
- [ ] Add QA scripts for candidate accept/reject, punctuation commit, enter, delete, and hardware keyboard input.

Acceptance criteria:
- Keyboard actions provide clear feedback without surprising text mutation.
- Layout variants maintain touch targets, readability, and stable alignment.
- Placeholder or unfinished controls are removed or completed.

### 12. Localization and Content Quality

Status: In progress
Priority: P1

Goal: Keep user-facing language clear while avoiding risky translation churn.

Tasks:
- [x] Fix high-confidence format, ellipsis, typo, and plural lint issues.
- [ ] Review remaining Turkish repeated-word lint warnings with a native-language-safe approach before changing them.
- [ ] Check top-level English source strings for vague, abrupt, or overly technical labels.
- [ ] Standardize backup/restore/import/export failure copy around cause and recovery.
- [ ] Standardize destructive confirmation copy.
- [ ] Document translation-safe rules for future resource cleanup.

Acceptance criteria:
- Text changes preserve placeholders and meaning.
- Risky translation changes are documented or deferred.
- Core recovery messages are specific enough to guide action.

### 13. Privacy, Safety, and Data Integrity

Status: Planned
Priority: P0

Goal: Make sensitive keyboard data, backups, dictionaries, clipboard data, and extensions safer to manage.

Tasks:
- [ ] Audit backup contents selection and labels for privacy clarity.
- [ ] Audit restore overwrite/merge behavior and confirm destructive impact before execution.
- [ ] Audit clipboard backup/restore media handling for missing-file and path-safety cases.
- [ ] Audit dictionary import/export for malformed files and duplicate handling.
- [ ] Audit extension import for path traversal, duplicate IDs, invalid manifests, and recovery.
- [ ] Audit incognito mode persistence and suggestion suppression semantics.
- [ ] Add tests around backup/restore path safety and import validation.

Acceptance criteria:
- Destructive or privacy-sensitive operations are explicit and reversible where feasible.
- Invalid archives/files fail with specific messages and no partial corruption.
- Import/export code has focused tests for path and validation failures.

### 14. Build Warning and Dependency Hygiene

Status: In progress
Priority: P1

Goal: Reduce compile and dependency noise without destabilizing the app.

Tasks:
- [ ] Replace deprecated synchronous toast calls in user-facing Compose screens where coroutine scope is available.
- [x] Replace deprecated synchronous toast calls in backup/restore screens.
- [x] Replace deprecated synchronous toast calls in extension import/export/view screens.
- [x] Replace wildcard extension export document MIME type with the registered extension archive MIME type.
- [x] Replace deprecated synchronous toast calls in dictionary import/export screens.
- [x] Replace wildcard dictionary export document MIME type with a text MIME type.
- [x] Replace deprecated synchronous toast calls in language pack manager delete flow.
- [x] Replace deprecated language pack manager import icon usage with the AutoMirrored icon.
- [ ] Review Room nullable DAO warning and either fix the type or document why it is intentional.
- [ ] Review Kotlin compiler flags and remove stale flags only when language-version behavior is confirmed.
- [x] Review dependency update warnings separately from product changes.
- [x] Add a dependency-review note before any version bump.

Acceptance criteria:
- Compile output gets quieter without broad suppressions.
- Dependency changes are isolated and easy to revert.
- User-facing behavior remains unchanged unless explicitly intended.

### 15. Manual QA and Release Evidence

Status: Planned
Priority: P1

Goal: Make repeated quality passes verifiable by future runs.

Tasks:
- [ ] Add a manual QA checklist for setup, settings navigation, typing, backup/restore, extensions, themes, language packs, dictionary, and clipboard.
- [ ] Add adb commands for settings launch, crash-buffer capture, and keyboard smoke where possible.
- [ ] Add expected lint/test/build commands and success criteria.
- [ ] Track known device coverage and gaps.
- [ ] Add before/after measurement slots for performance and interaction polish work.

Acceptance criteria:
- A future agent can repeat the same smoke checks without rediscovering commands.
- Release readiness is based on concrete evidence rather than visual inspection alone.

## Suggested Build Order

1. Continue warning cleanup in user-facing feedback flows, starting with backup/restore synchronous toast removal.
2. Add input behavior contract and extend autocorrect tests.
3. Extract punctuation/phantom-space rules into pure testable classes.
4. Audit and harden backup/restore trust states.
5. Add product UX and accessibility checklist with concrete screen coverage.
6. Fix obvious semantic/touch-target gaps found by that checklist.
7. Add performance notes and first-suggestion/cold-start measurement workflow.
8. Tighten CI gates after local checks are stable.
9. Continue conservative resource cleanup once lint signal is cleaner.
