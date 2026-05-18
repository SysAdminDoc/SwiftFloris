# Release v1.8.108 — voice removeItemFromList refuses on existing selection

Date: 2026-05-17

Seventh-pass audit finding #14 from the voice subsystem agent. Closes a
silent data-loss path in the `REMOVE_ITEM_FROM_LIST` executor.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandExecutor.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandExecutor.kt#L170)
— `removeItemFromList` previously computed
`startOfOld = before.length - previous.length` and used
`content.textBeforeSelection.length` as the selection end. When the
user had a **non-empty selection** at the time the command fired —
e.g. they double-tapped a word, dragged the selection handles, or used
the system "select-all" shortcut — the `before.length` measurement was
taken at the *start* of their selection, but the subsequent
`editor.setSelection(startOfOld, content.textBeforeSelection.length)`
+ `commitText` collapsed the user's selection AND overwrote the
selected text plus the suffix above the cursor with the diff's new
text. Silent data loss.

The fix adds an early-return when `content.selectedText.isNotEmpty()`.
The streaming buffer is also NOT mutated in the refuse path (the early
return runs before `buffer.removeCommittedItem(item)`), so the user can
clear the selection and retry the command without the dictation buffer
diverging from the editor.

This matches the existing refuse-on-raw-input gate just above —
`isRawInputEditor` also returns ACTION_REJECTED rather than guessing.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandExecutor.kt`
- `gradle.properties` — versionCode 1908 / versionName 1.8.108

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction (requires voice IME + a list-dictation scenario):
- Dictate a list: "apples, bread, milk".
- Without saying anything else, manually select a word in the editor
  (e.g. double-tap "bread").
- Say "scratch apples from list".
  - **Pre-fix:** the selected word "bread" is collapsed and the
    "apples" entry plus its preceding text is overwritten with the
    diff result — multiple data losses in one command.
  - **Post-fix:** the command returns
    `VoiceCommandFailureReason.ACTION_REJECTED`; the editor selection
    and committed text are unchanged. The user can clear the
    selection and re-issue the command.
