# Release v1.8.104 — IME_FLAG_NO_PERSONALIZED_LEARNING always honoured

Date: 2026-05-17

Seventh-pass audit finding from my own pass on `FlorisImeService` /
`EditorInstance` plumbing.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt#L138-L152)
— `handleStartInputView` previously read the user's
`prefs.suggestion.incognitoMode` setting and only honoured the host
app's `IME_FLAG_NO_PERSONALIZED_LEARNING` declaration when that
preference was `DYNAMIC_ON_OFF` (the default). When the user had set
the preference to `FORCE_OFF`, the flag was silently ignored.

The privacy contract this gets wrong: apps that set
`IME_FLAG_NO_PERSONALIZED_LEARNING` (Signal, ProtonMail, banking
apps, end-to-end encrypted chat surfaces, password vaults) are
asserting "this is sensitive content; do not learn from it." That's
**not a user preference question** — it's a host-app declaration the
IME is obligated to honour. A user who chose `FORCE_OFF` was making a
statement about their *own* manual incognito-toggle UX, not about
overriding cross-app privacy declarations.

The fix splits the gate:

- **App-declared `flagNoPersonalizedLearning`** always forces
  `isIncognitoMode = true` for the current field, regardless of the
  user's IncognitoMode preference.
- The **user's IncognitoMode preference** continues to control
  user-requested incognito (the smartbar toggle, the FORCE_ON
  power-user setting).

Net effect: every gate that reads `activeState.isIncognitoMode` —
`learnIfAllowed` in `KeyboardManager` (which suppresses dictionary
writes), the bigram / trigram store updates, the touch-decoder
sample writes — now correctly suppresses on app-declared sensitive
fields even for users who turned off the manual incognito UX.

## Why this is a separate release

Per [AGENTS.md §6](AGENTS.md), one logical improvement per release.
v1.8.85 was the documented exception. This is the seventh-pass audit
opening shot.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `gradle.properties` — versionCode 1904 / versionName 1.8.104

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction:
- Set Settings → Typing → Suggestion → Incognito mode → "Always off".
- Open Signal (or any app whose editor sets
  `IME_FLAG_NO_PERSONALIZED_LEARNING`) and type a few unique words.
- Switch to a non-sensitive editor (a normal text field) and type
  one of the words you just typed in Signal.
  - **Pre-fix:** the word appears in the suggestion strip as a
    learned personal-dictionary entry — Signal's privacy contract
    is broken.
  - **Post-fix:** the word does not appear unless it's already in
    the base SCOWL dictionary.
- Verify the manual incognito toggle still works on the smartbar
  (independent surface).
