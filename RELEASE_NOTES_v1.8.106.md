# Release v1.8.106 — voice handoff sensitive-field guard

Date: 2026-05-17

Seventh-pass audit finding #7 from the voice subsystem agent.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt#L247)
— `switchToVoiceInputMethod` previously routed the user's voice key
tap to an external voice IME (FUTO Voice Input, or any other enabled
voice IME) regardless of the focused field's sensitivity. Net effect:
a user typing in a password / numeric-PIN / web-password field who
tapped the voice key would have their spoken credential streamed
through an external recogniser process whose privacy boundary the
SwiftFloris no-`INTERNET` contract does **not** cover — voice IMEs
typically request full network access for cloud recognition.

The fix adds an early-return at the top of `switchToVoiceInputMethod`:
when `keyVariation == KeyVariation.PASSWORD` OR
`isIncognitoMode` (per v1.8.104 / v1.8.105, the unified privacy gate),
the function shows a toast and returns false. The host app's
sensitive-field declaration is the load-bearing signal here; the
IME-side gate honours it.

Mirrors the existing dictionary-learn, clipboard cut/copy (v1.8.86 +
v1.8.105), and smart-compose (`SensitiveFieldGuard`) gates.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `app/src/main/res/values/strings.xml` (new toast string
  `voice_input__suppressed_on_sensitive_field`)
- `gradle.properties` — versionCode 1906 / versionName 1.8.106

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction:
- Install a voice IME (FUTO Voice Input recommended) and enable it
  in system Settings.
- Open SwiftFloris in a password field (or numeric-PIN, or web-password,
  or any field whose host sets `IME_FLAG_NO_PERSONALIZED_LEARNING`).
- Tap the SwiftFloris voice key.
  - **Pre-fix:** the system swaps to the external voice IME and
    begins recording / streaming.
  - **Post-fix:** the swap is refused; a toast reading "Voice input
    is disabled for this field for your privacy." appears; focus
    stays on SwiftFloris.
- Repeat in a normal text field and verify voice handoff still works.
