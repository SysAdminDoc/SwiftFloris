# SwiftFloris Improvement Plan

Last updated: 2026-05-05

This plan tracks quality, UX, accessibility, performance, testing, and delivery improvements that sit beside the product roadmap. It is intentionally execution-focused: every item should end in code, tests, docs, or release-process changes.

## Current Baseline

- Branch: `master`, currently ahead of `origin/master`.
- Main Kotlin files: 239.
- Test Kotlin files: 16.
- Latest verified commands:
  - `./gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`
  - `./gradlew.bat :app:installDebug`
  - adb launch smoke for `dev.patrickgold.florisboard.debug/dev.patrickgold.florisboard.SettingsLauncherAlias`
- Known worktree condition: unrelated deleted markdown files are present and must not be staged unless explicitly requested.
- Initial lint shape when this plan started: 324 warnings, 2 hints.
- Current lint shape after cleanup batches: 259 warnings, 0 hints. Largest remaining bucket is `UnusedResources`.
- Current compile-warning focus: touched backup/restore deprecated toast warnings are cleared. Remaining known warning themes are the Room nullable DAO type, Kotlin compiler flags, and deprecated synchronous toast calls in other user-facing import/export, dictionary, theme, language pack, devtools, keyboard, and clipboard surfaces.

## Current Improvement Assessment

- Keyboard correctness is the highest-risk product surface. Autocorrect, backspace rejection, punctuation commits, phantom spacing, glide delete, and hardware keyboard paths need explicit contracts plus JVM coverage.
- Backup, restore, extension, language pack, dictionary, and theme workflows are trust-sensitive. They need clear busy states, specific errors, recovery copy, duplicate-action blocking, and post-action confirmation.
- Settings are feature-rich and need stronger information architecture: clearer section summaries, consistent secondary text, better empty states, and predictable destructive confirmations.
- The keyboard surface needs a dedicated accessibility and polish pass for candidate row semantics, smartbar controls, touch targets, contrast, and state labels.
- Lint signal is improving, but `UnusedResources` still hides real regressions. Resource cleanup must be conservative because dynamic lookup and build variants are likely.
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

## Workstreams

### 1. Test Coverage Expansion

Status: In progress
Priority: P0

Goal: Raise confidence in the keyboard's highest-risk behavior by moving state rules into testable units and adding regression coverage.

Tasks:
- [x] Add JVM coverage for rejected autocorrect behavior.
- [ ] Add editor/input behavior tests for autocorrect accept, reject, undo, punctuation, and spacing flows.
- [ ] Add hardware keyboard tests for space, enter, delete, and punctuation behavior.
- [ ] Add phantom-space and autospace lifecycle tests.
- [ ] Add glide typing delete interaction tests.
- [ ] Add incognito suggestion behavior tests.
- [ ] Add backup and restore tests for success, cancellation, invalid archive, and partial failure cases.
- [ ] Add language pack import/update tests.
- [ ] Add subtype editor validation tests.
- [ ] Add theme editor validation tests.

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
- [ ] Review `UnusedResources`; delete only resources proven unused across build variants and dynamic lookup paths.
- [ ] Review dependency version warnings separately from source cleanup.

Acceptance criteria:
- Lint warning count decreases monotonically across cleanup batches.
- No broad suppressions are added for fixable warnings.
- Locale/resource fixes preserve formatting placeholders and product meaning.

### 3. Pure Core Extraction

Status: Planned
Priority: P0

Goal: Make keyboard behavior easier to test and safer to change by separating Android framework wiring from deterministic rules.

Tasks:
- [x] Extract autocorrect suppression state to a pure JVM-testable class.
- [ ] Extract candidate auto-commit eligibility and rejection policy from `NlpManager`.
- [ ] Extract punctuation-triggered commit rules from `KeyboardManager`.
- [ ] Extract phantom-space/autospace rules from `EditorInstance`.
- [ ] Extract backup/restore validation policy from Compose screens.
- [ ] Extract theme validation and rule parsing from UI surfaces.

Acceptance criteria:
- Extracted classes have no Android framework dependency unless unavoidable.
- Manager classes become thinner orchestration layers.
- Each extracted rule set has focused JVM tests.

### 4. Input Behavior Hardening

Status: In progress
Priority: P0

Goal: Make typing behavior predictable, especially around corrections, manual overrides, punctuation, hardware keyboards, and destructive edits.

Tasks:
- [x] Respect user backspace rejection after an automatic correction.
- [ ] Define a written contract for autocorrect lifecycle behavior.
- [ ] Make suggestion acceptance, rejection, and provider notification semantics explicit.
- [ ] Audit punctuation and non-letter commit behavior across software and hardware input.
- [ ] Audit delete behavior with glide setting `immediateBackspaceDeletesWord`.
- [ ] Add manual QA scripts or adb notes for preview-field typing checks.
- [ ] Add regression tests for every fixed input behavior.

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
- [ ] Audit backup flow for loading, success, warning, failure, and cancellation states.
- [ ] Audit restore flow for destructive confirmation, progress, partial failure, and recovery copy.
- [ ] Audit language pack import/update/remove states.
- [ ] Audit theme import/edit/delete states.
- [ ] Audit extension import/edit/delete states.
- [ ] Audit dictionary add/remove states.
- [ ] Add calm, specific microcopy for failure and recovery paths.
- [ ] Add disabled/busy states where repeated actions could corrupt state or confuse users.

Acceptance criteria:
- Every destructive or long-running workflow has explicit feedback.
- Error states include a recovery path.
- Busy states prevent duplicate execution.

### 6. Accessibility Pass

Status: Planned
Priority: P1

Goal: Treat accessibility as part of product quality, not post-release cleanup.

Tasks:
- [ ] Audit settings screen focus order.
- [ ] Audit candidate row and smartbar TalkBack labels.
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
- [ ] Keep generated reports/build output out of commits.
- [ ] Track improvement work in this file.
- [ ] Keep verification commands in final handoffs.

Acceptance criteria:
- No unrelated deleted docs are staged accidentally.
- Each commit has a clear purpose and matching verification.
- Project state can be resumed from this plan.

### 10. Product UX and Visual Polish

Status: Planned
Priority: P1

Goal: Make settings, onboarding, dialogs, and keyboard-adjacent UI feel more coherent, modern, calm, and easier to scan.

Tasks:
- [ ] Audit first-run/setup flow for orientation, progress, permission clarity, IME enablement, and final success state.
- [ ] Audit settings landing page hierarchy, section naming, secondary text, and action discoverability.
- [ ] Normalize screen-level spacing, section density, and button placement across settings screens.
- [ ] Normalize dialog, bottom-bar, empty-state, warning, error, and info-card copy.
- [ ] Review primary/secondary/destructive action treatment across backup, restore, extension, theme, language pack, and dictionary flows.
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
- [ ] Review Room nullable DAO warning and either fix the type or document why it is intentional.
- [ ] Review Kotlin compiler flags and remove stale flags only when language-version behavior is confirmed.
- [ ] Review dependency update warnings separately from product changes.
- [ ] Add a dependency-review note before any version bump.

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
