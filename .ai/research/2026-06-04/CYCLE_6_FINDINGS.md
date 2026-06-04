# SwiftFloris Cycle 6 Findings - 2026-06-04

## Scope

Cycle 6 ran from the clean detached worktree at pushed `master` commit
`49e9fd6`. `git pull --rebase origin master` reported the worktree was already
up to date. This pass did not edit feature code, tests, build files, or assets.

## Anti-Duplication Check

- The unbalanced `beginBatchEdit()` early-return issue is already fixed and
  recorded in `docs/AUDIT_2026-05-29.md`; this cycle does not re-add that bug.
- The broader Workstream 4 coroutine-policy work is historical. R6-1 is a
  narrow editor hot-path follow-up focused on open `InputConnection` batch
  windows.
- No new visual, clipboard, or addon trust row was added; R4-2, R5-1, and WS
  device-gated work already cover those surfaces.

## Local Evidence

- `AbstractEditorInstance.kt:311-325` opens an `InputConnection` batch in
  `setSelection`, then enters `runBlocking` to compute expected content, push
  `ExpectedContentQueue`, and call `setSelection` / `setComposingRegion`.
- `AbstractEditorInstance.kt:396-414` keeps a batch open in `commitTextInternal`
  while the non-raw path runs `runBlocking`, generates expected content, pushes
  the queue, commits text, and sets the composing region.
- `AbstractEditorInstance.kt:429-445` has the same pattern in
  `finalizeComposingText`.
- `ExpectedContentQueue` uses suspending `withLock` helpers for `push`,
  `peekNewestOrNull`, `popUntilOrNull`, and `clear` at
  `AbstractEditorInstance.kt:679-708`.
- `docs/AUDIT_2026-05-28.md:54-56` records the `setSelection` risk and suggests
  computing `newContent` outside the open batch.
- Current editor JVM tests cover policy/content helpers, but there is no focused
  fake `InputConnection` test asserting batch depth, call order, or no queue
  work while a batch is open.

## External Evidence

- Android `InputConnection` docs define `beginBatchEdit()` and `endBatchEdit()`
  as the editor-facing batch operation pair, and list `setSelection`,
  `setComposingRegion`, `setComposingText`, `commitText`, and
  `finishComposingText` as editor mutation APIs:
  https://developer.android.com/reference/android/view/inputmethod/InputConnection

## Roadmap Changes Fed

1. R6-1, P2: keep `InputConnection` batch edits free of `runBlocking` and
   queue-lock work.

## Non-Adds

- No item was added for the already-fixed unbalanced batch early return.
- No full coroutine rewrite was proposed; the row only asks to move expected
  content generation/queue locks outside open editor batch sections and to add
  focused tests.
- No source-code fix was attempted in this cycle; all changes are roadmap and
  research documentation only.
