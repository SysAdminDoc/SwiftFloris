# Release v1.8.110 — voice Listening state observable during handoff

Date: 2026-05-17

Seventh-pass audit finding #11 from the voice subsystem agent.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt#L88) —
`startListening` previously did this:

```kotlin
_isListening.value = true
_transcriptionState.value = TranscriptionState.Listening
val switched = FlorisImeService.switchToVoiceInputMethod(...)
if (switched) {
    _isListening.value = false                              // ←
    _transcriptionState.value = TranscriptionState.Ready    // ← same frame
    return true
}
```

Both `_isListening = true` and `_transcriptionState = Listening` were
assigned and immediately overwritten in the same synchronous frame.
The Listening event was sub-millisecond — no `collectAsState()` /
flow consumer ever observed it. Mic-meter UIs read `_isListening` as
permanently false; "Connecting to voice IME…" spinners read
`_transcriptionState` as Ready → Ready with no intermediate Listening
frame.

The fix keeps the state in Listening when the IME swap succeeds and
relies on the reset path on the way back (next paragraph).

### IME re-entry resets to Ready

[`app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt#L489) —
`onStartInput` now calls `voiceInputManager.refreshAvailability()`.
When SwiftFloris is re-bound as the active IME (the user returned
from FUTO / system picker / cancel), this resets the state to Ready
so the next interaction starts cleanly. `refreshAvailability()` is
cheap and idempotent — it already short-circuits when the recogniser
availability hasn't changed.

Net effect: any UI consumer observing `transcriptionState` or
`isListening` now sees a proper Ready → Listening (held for the
duration of the external-IME session) → Ready transition, instead of
the previous Ready → Ready null-transition.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `gradle.properties` — versionCode 1910 / versionName 1.8.110

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction (requires voice IME installed + enabled):
- Wire any debug UI that does
  `transcriptionState.collectAsState()` and renders the value.
  (If no such UI exists, add an adb-logcat hook via `flogInfo` in
  `setTranscriptionState` for the test.)
- Tap the voice key in SwiftFloris.
  - **Pre-fix:** observer logs `Ready → Ready`. No Listening
    transition.
  - **Post-fix:** observer logs `Ready → Listening` immediately. The
    Listening value remains held while FUTO's UI is active.
- Cancel / submit / return from FUTO to SwiftFloris.
  - Observer logs `Listening → Ready` on the `onStartInput` rebind.
