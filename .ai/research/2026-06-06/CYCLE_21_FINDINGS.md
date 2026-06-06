# Cycle 21 Findings - 2026-06-06

## Cycle

`first-run-import-recovery-recheck-2026-06-06`

## Scope

Resumed from the roadmap Continuation State. This pass rechecked the first-run
dictionary import step, the Settings -> User dictionary import action, empty
states, and import preview/summary behavior to see whether R18-2 and R20-1 need
a smaller UX reliability row before broader migration assistant work.

## Files and sources reviewed

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupStepPolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/PersonalDictionaryImportPreviewDialog.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/PersonalDictionaryImportSummaryDialog.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/kotlin/dev/patrickgold/florisboard/app/setup/SetupStepPolicyTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryEntryPolicyTest.kt`
- Android `ActivityResultContracts.GetContent` reference:
  https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.GetContent

## Findings

- `SetupScreen.kt:267-273` sets `firstRunImportHintSeen` before navigation to
  the user-dictionary import action. That marks the setup import hint complete
  before a picker result, parse result, preview confirmation, or import summary
  exists.
- `UserDictionaryScreen.kt:523-529` treats `uri == null` as a silent return.
  The local comment correctly notes that this is usually picker cancellation,
  but the upstream setup step has already been suppressed by then.
- `UserDictionaryScreen.kt:545-553` consumes the import action once and launches
  the picker. If cancellation happens, there is no automatic retry state.
- `UserDictionaryScreen.kt:811-818` exposes Add as the empty dictionary state's
  primary action. Import remains available in the overflow menu, but a migration
  user arriving from setup sees no direct recovery CTA.
- The preview and summary dialogs are strong once an import is parsed; the weak
  branch is before parsing begins or after file-selection cancellation/failure.

## Roadmap effect

Added R21-1 to `ROADMAP.md`: keep first-run dictionary import recoverable after
picker cancel or import failure.

## Acceptance shape

- Setup should not mark the import hint complete merely because the user tapped
  "Choose export file".
- Cancellation should leave a visible retry or continue-without-import path.
- Empty dictionary states should expose Import when entry actions are enabled.
- Import failure should preserve a retry path and not duplicate dictionary rows.
- Focused tests should pin setup-step transitions and one-shot import action
  consumption.

## Duplicate avoidance

- R18-2 remains the broader in-app migration recovery assistant.
- R20-1 remains the migration document/source-of-truth cleanup.
- R21-1 is the smaller activation reliability issue visible in current setup
  and dictionary code.

