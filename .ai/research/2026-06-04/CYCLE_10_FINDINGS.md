# Cycle 10 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `99a8431`
  (`docs: refresh cycle 9 research queue`), described as
  `v1.8.234-1-g99a8431`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not duplicate R6-1. v1.8.233 closed the selected synchronous
  `InputConnection` batch critical sections and `try/finally` batch pairing;
  this cycle is about fire-and-forget content-generation jobs that can resume
  after reset/finishInput.
- Did not duplicate R9-1. R9-1 scopes privacy inputs for async suggestion
  providers; this cycle scopes editor content publication and composing-region
  mutations to the active editor session.
- Did not re-open the unbalanced `finalizeComposingText` batch issue. Current
  `EditorInputConnectionBatchTest` coverage is about synchronous helper call
  order, not delayed coroutine lifecycle.
- Left the application preference-init coroutine failure audit for a later
  cycle; it is still live, but this cycle kept the scope to the IME editor
  session boundary.

## Local Evidence

- `AbstractEditorInstance.kt:69` owns a `MainScope()` used for editor content
  work.
- `AbstractEditorInstance.kt:141-153` launches content generation from
  `handleStartInputView(...)`, captures `ic`, then publishes
  `activeCursorCapsMode`, `activeContent`, shift-state reevaluation, and
  `ic.setComposingRegion(...)` when the coroutine resumes.
- `AbstractEditorInstance.kt:165-209` repeats the same launch/publish pattern
  for `handleSelectionUpdate(...)`.
- `AbstractEditorInstance.kt:212-227` routes `handleFinishInputView()` and
  `handleFinishInput()` through `reset()`, but reset only clears active info,
  caps, content, expected-content queue, and last-commit position. It does not
  cancel a pending content job or increment a generation token.
- `AbstractEditorInstance.kt:303-308` maps an invalid composing range to
  `finishComposingText()`, so a stale content job can still mutate the captured
  old editor connection even when no composing region remains.
- `EditorInputConnectionBatchTest.kt:27-189` pins synchronous batch helper call
  ordering and batch depth, but no test simulates delayed content generation
  resuming after reset, finishInput, or a field switch.
- `docs/AUDIT_2026-05-28.md:51-57` recorded this stale content-generation
  lifecycle issue separately from the batch-edit critical-section finding that
  v1.8.233 later closed.

## Roadmap Changes Fed

- R10-1: Cancel stale editor content-generation jobs on reset/finishInput.
  Implementation should track a pending job, generation token, or immutable
  editor-session request so only the current session can publish generated
  content or touch an `InputConnection`. Reset and finish-input paths should
  cancel/supersede pending jobs, and launched blocks should re-check the active
  generation plus current connection identity before publishing state or
  composing-region changes.

## Non-Adds

- No source fix was made in this cycle.
- No broad editor refactor proposed. The target is the launch/reset boundary
  around `handleStartInputView(...)`, `handleSelectionUpdate(...)`, and
  `reset()`.
- No new permission, storage, or network row added.
