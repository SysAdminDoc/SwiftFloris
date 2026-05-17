# SwiftFloris v1.8.49 — 2026-05-17

N15.3 — Smart Edit voice REMOVE_ITEM_FROM_LIST.

## Why ship this now

The §6 N15.3 roadmap item closes the Gboard 2026 Smart-Edit parity
gap for voice-dictated list editing — saying "no longer want apples"
mid-stream should excise `apples` from the dictated list without the
user having to backspace, retype, or stop the session. The existing
v1.7.9 `StreamingVoiceTranscriptBuffer` already routes per-chunk
matches through `VoiceCommandExecutor`; this slice adds the
parameterised command type and the buffer-side excision walker the
roadmap calls out.

## What changed

### Parser

- New `VoiceCommandAction.REMOVE_ITEM_FROM_LIST` enum value.
- New `argument: String?` field on `VoiceCommandMatch` so
  parameterised commands carry their extracted target. Null for
  every fixed-phrase command (existing call sites unchanged).
- New `VoiceCommandParser.parameterisedMatch(...)` runs before the
  fixed-phrase ranker and recognises seven unambiguous patterns:
  - `no longer want <item>`
  - `no longer need <item>`
  - `remove <item> from the list`
  - `remove <item> from list`
  - `delete <item> from the list`
  - `delete <item> from list`
  - `scratch <item>`
- Confidence is fixed at 1.0 for an exact pattern match — the anchor
  tokens disambiguate, so partial matches simply don't fire and the
  user retries.
- `extractRaw` preserves the original argument casing so UX feedback
  ("Removed 'Apples'") reads naturally.
- Conservative stopword guard (`the`/`a`/`an`/`this`/`that`/`it`/
  `them`/`those`/`these`) prevents `remove the from the list` or
  `scratch the` from excising the whole buffer.

### Buffer

- New `StreamingVoiceTranscriptBuffer.committedSegmentsSnapshot()`
  exposes the dictated-list state to the executor.
- New `removeCommittedItem(item)` walks `committedSegments`, excises
  every case-insensitive whole-phrase occurrence of `item`,
  collapses dangling `and` / `or` / `plus` / `with` / `&` connectors,
  and returns a `RemoveCommittedItemResult { removedCount,
  previousCommittedText, newCommittedText, didChange }` so the
  executor can apply the diff to the editor.
- Multi-word items are supported (`almond butter` → matched as one
  phrase).
- Buffer is left untouched on no-match, blank-input, or
  whitespace-only input — defensive against a malformed parser
  argument silently nuking the entire buffer.

### Executor

- `VoiceCommandActions.removeItemFromList(item)` is a new interface
  method with a default impl returning `ACTION_REJECTED` so existing
  implementations of the interface (test doubles, external adapters)
  compile unchanged.
- `EditorVoiceCommandActions` gains an optional `transcriptBuffer`
  reference; when set, `removeItemFromList(item)`:
  1. Asks the buffer for the diff.
  2. If the editor's text-before-cursor still ends with the
     buffer's previous committed text, selects exactly that suffix
     via `editor.setSelection` and replaces it via
     `editor.commitText(diff.newCommittedText)`.
  3. If the user typed something between dictation chunks, returns
     the new `EDITOR_OUT_OF_SYNC` failure instead of risking editor
     corruption.
- Two new `VoiceCommandFailureReason` values:
  - `ITEM_NOT_FOUND` — the parameterised command matched but the
    item was not present in the dictated buffer.
  - `EDITOR_OUT_OF_SYNC` — buffer was mutated but the editor diff
    couldn't be safely applied; the IME can re-sync.
- `VoiceCommandExecutor` short-circuits to `ACTION_REJECTED` when
  the match's argument is null / blank, so the action sink never
  sees an empty-string item.

### Settings UI

- `REMOVE_ITEM_FROM_LIST` is filtered out of the custom-command
  picker in Settings → Voice Input — it's argument-only and not
  assignable as a fixed-phrase custom command.
- New string resource
  `settings__voice_input__voice_command_remove_item_from_list`
  covers the action's display label for the case where a custom
  command somehow already references it (forward-compat).

## New tests

- `VoiceCommandParserTest`: 8 new cases covering pattern detection,
  casing preservation, every remove/delete variant, `scratch`,
  `remove that` → DELETE_THAT precedence, stopword rejection,
  no-argument rejection, and the ambient-utterance false-positive
  guard (`delete the old message after lunch` stays unclassified).
- `StreamingVoiceTranscriptBufferTest`: 8 new cases covering single
  match, dangling-`and` cleanup, case-insensitive match with
  casing-preserved remainder, no-op when absent, blank-input
  refusal, multi-word item, cross-segment walk, and
  trailing-punctuation argument tolerance.
- `VoiceCommandExecutorTest`: 3 new cases plus an updated all-actions
  loop — null-argument short-circuit, whitespace-argument
  short-circuit, trimmed-argument forwarding.

## Versioning

- `gradle.properties`: `projectVersionCode=1849`,
  `projectVersionName=1.8.49`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK on
the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

The Next-2.5 Rambler-style streaming-voice cleanup pass remains
gated on the L1 Gemma 3 LLM bring-up. Continuing through the §6 NOW
queue: N17.1 (emoji crash triage), N14.3 (Compose BOM refresh
audit), N14.4 (Gradle wrapper bump audit).
