# SwiftFloris v1.8.58 — 2026-05-17

Phase D2 — Generic task-creation quick action (`QuickAction.InsertTask`),
the on-device replacement for SwiftKey's Microsoft-To-Do toolbar tile.

## Why ship this now

Phase D2 of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. SwiftKey's task
tile is hard-bound to Microsoft accounts; this slice ships an
on-device, cross-app replacement that doesn't require new
permissions and works with any installed task / note app that
registers a `SEND` filter (Tasks.org, OpenTasks, Google Tasks,
Joplin, Notion, Markor, etc.).

Small slice — single new sealed-class variant + polymorphic codec
registration + display-name / tooltip wiring. No new dependencies,
no Android permissions added.

## What changed

### `QuickAction.InsertTask` (new)

`data object InsertTask : QuickAction()` with `@SerialName("insert_task")`:

- `onPointerUp(context)`:
  1. Reads `editorInstance.activeInfo` and runs
     `SensitiveFieldGuard.isSensitive(inputType, imeOptions)`. If
     the field is a password / numeric-PIN / no-personalised-learning
     field, the action surfaces a Toast and refuses. (User
     consent via tap doesn't override the privacy moat —
     accidentally sending a password via the share sheet is exactly
     the failure mode the guard exists for.)
  2. Reads `editorInstance.activeContent.selectedText`. If empty,
     falls back to the last 140 chars of text-before-selection so
     the share sheet still has *something* meaningful (the user
     can edit it in the destination app).
  3. Builds an `Intent.ACTION_SEND` with `type = "text/plain"` and
     `EXTRA_TEXT = title`, wraps it in
     `Intent.createChooser(sendIntent, "Add to tasks")`, adds
     `FLAG_ACTIVITY_NEW_TASK` (IME service isn't an Activity), and
     calls `startActivity(chooser)`.
  4. On `ActivityNotFoundException` (no task / note app installed),
     surfaces a helpful Toast pointing at the install path.

The label is `"Add task"`; the tooltip is `"Send current selection
to a task / note app (Tasks.org, OpenTasks, Google Tasks, Joplin,
etc.)"`. Both are hard-coded English in this slice (consistent with
the existing `TranslateSelection` action); Crowdin string resources
follow in a localization sweep.

### `QuickActionArrangement.QuickActionJsonConfig`

The polymorphic serializers module now explicitly registers:

- `QuickAction.InsertKey` (already present)
- `QuickAction.InsertText` (already present)
- `QuickAction.TranslateSelection` (added — was relying on
  sealed-class auto-discovery)
- `QuickAction.InsertTask` (new)

Existing arrangements continue to round-trip; users who already
have a saved smartbar layout don't lose their configuration.

## Versioning

- `gradle.properties`: `projectVersionCode=1858`,
  `projectVersionName=1.8.58`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Hand-test path on a device:
1. Install at least one task app (Tasks.org via F-Droid is the
   recommended FOSS option).
2. Open Settings → Smartbar → Customize quick actions, drag the
   "Add task" tile into the active arrangement.
3. In any editor, type some text and select a portion.
4. Tap the "Add task" tile — the share sheet should appear with
   Tasks.org (and other share targets) as an option.
5. Pick Tasks.org — its "New task" form should open with the
   selected text pre-filled in the title field.
6. Repeat in a password field — should see "Sending tasks from
   sensitive fields is blocked" Toast.

## What's next

Phase D3 (typing-stats accuracy delta) is the next small slice —
compute "X% fewer corrections accepted this week vs. last" from
the existing `CorrectionOutcomePriors` store and surface in
`TypingStatsScreen`. Phase D1 (calendar quick-insert) is a larger
slice because it requires `READ_CALENDAR` permission and an
agenda picker UI; left for a focused follow-up.

After Phase D the remaining unblocked work in
`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` is Phase C1 (split-keyboard
renderer wire-up — large) and Phase C3 (High Contrast AAA + first
animated theme — pure asset work). Phase E sub-items are gated on
the L1 LiteRT-LM addon bring-up.
