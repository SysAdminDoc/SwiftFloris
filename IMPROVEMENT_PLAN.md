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
- Current lint shape after cleanup batches: 293 warnings, 0 hints. Largest remaining buckets are `UnusedResources` and `MissingQuantity`.

## Priority Model

- P0: protects core keyboard correctness, user trust, release safety, or crash prevention.
- P1: improves maintainability, accessibility, state clarity, or performance evidence.
- P2: polish, cleanup, and longer-term architecture work.

## Progress Log

- 2026-05-05: Created this plan and began the first implementation slice: small lint/testability cleanup with no product behavior change intended.
- 2026-05-05: First lint cleanup batch removed invalid manifest alias attributes, closed a Han suggestion cursor, resolved Compose naming warnings, and cleared efficiency hints for integer state and zero-filled arrays.
- 2026-05-05: Second lint cleanup batch migrated simple URI/preference calls to KTX APIs, fixed a translated crash-report format mismatch, normalized reported ellipses, and corrected high-confidence German, Portuguese, and Spanish translation typos. Remaining typo warnings are intentional Turkish repeated-word phrases.

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
- [ ] Address `MissingQuantity` by adding required plural quantities, preferably via translation-safe fallback copying.
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

Status: Planned
Priority: P1

Goal: Make high-risk workflows communicate clearly, recover gracefully, and avoid silent failure.

Tasks:
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

## Suggested Build Order

1. Finish first lint cleanup batch.
2. Add input behavior contract and extend autocorrect tests.
3. Extract punctuation/phantom-space rules into pure testable classes.
4. Audit and harden backup/restore trust states.
5. Add accessibility checklist and fix obvious semantic/touch-target gaps.
6. Add performance notes and first-suggestion/cold-start measurement workflow.
7. Tighten CI gates after local checks are stable.
8. Continue resource cleanup once lint signal is cleaner.
