# Cycle 8 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `1d5bf2e`
  (`docs: refresh cycle 7 research queue`).
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not add another dictionary transfer-gating row: `CHANGELOG.md:2998-3011`
  records the shipped transfer state, progress cards, duplicate-action
  blocking, and `UserDictionaryEntryPolicy` coverage.
- Did not re-open the import-summary rollback DAO-null row:
  `UserDictionaryScreen.kt:924` and `UserDictionaryScreen.kt:971` now fail
  loudly with the localized store-unavailable message when the DAO is absent.
- Did not duplicate R7-1 or any `FLAG_SECURE` field-start coverage.
- Did not add the low-priority release workflow versionCode/changelog parity
  audit note because active release hardening rows already cover larger
  maintainer-gated release evidence, SBOM/provenance, signed tags, and
  fastlane changelog guidance.

## Local Evidence

- `UserDictionaryScreen.kt:171-179` derives
  `isEntryOperationInProgress`, `isDictionaryTransferInProgress`, and
  `canLeaveDictionaryScreen` from `UserDictionaryEntryPolicy`.
- `UserDictionaryScreen.kt:626-650` disables the visible navigation button
  while `canLeaveDictionaryScreen` is false.
- `UserDictionaryScreen.kt:722-727` enables `BackHandler` for selected-locale
  or active-operation state, but the handler body only closes the selected
  locale when `canLeaveDictionaryScreen` is true. During save/delete/import/
  export, the enabled handler consumes back and does nothing.
- `UserDictionaryScreen.kt:734-758` already renders import/export/save/delete
  progress cards while work is active.
- `strings.xml:942-945` and `strings.xml:997-1005` already provide localized
  in-progress wording for dictionary import/export and entry save/delete.
- `UserDictionaryEntryPolicy.kt:45-65` keeps leave, mutate, and transfer-start
  gates false while entry operations or dictionary transfers are active.
- `UserDictionaryEntryPolicyTest.kt:23-51` pins the policy that entry
  mutations and dictionary transfers block leaving and duplicate actions while
  busy.
- `docs/AUDIT_2026-05-28.md:160-162` records the swallowed-back no-feedback
  gap against the same user-dictionary screen.

## External Evidence

- AndroidX `BackHandler` API reference:
  `https://developer.android.com/reference/kotlin/androidx/activity/compose/BackHandler.composable`.
  The reference treats `enabled` as the control for whether a handler is
  active, so a no-op enabled handler is still a consumed system-back path.

## Roadmap Changes Fed

- R8-1: Give blocked user-dictionary back gestures explicit feedback.
  Implementation should keep the current leave-blocking behavior during active
  dictionary mutation/transfer work, but back should surface a toast, snackbar,
  live announcement, or equivalent feedback using existing in-progress wording
  or a dedicated string.

## Non-Adds

- No source fix was made in this cycle.
- No broad navigation rewrite proposed; the target is the existing
  user-dictionary screen and policy.
- No new privacy/security row added. This is UI feedback for an already-blocked
  operation path, not a data-retention or permission change.
