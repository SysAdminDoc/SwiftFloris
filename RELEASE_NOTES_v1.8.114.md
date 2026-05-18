# Release v1.8.114 — external voice IME microphone gate

Date: 2026-05-18

Seventh-pass follow-up roster item G8.

## What changed

External voice-input handoff readiness now requires microphone permission for every enabled external voice IME package, not only FUTO Voice Input.

The previous `isVoiceInputReadyForHandoff()` implementation correctly required `RECORD_AUDIO` for FUTO, but treated any other enabled voice IME as ready without checking that package's microphone grant. This release adds `ExternalVoiceInputHandoffPolicy` and routes all enabled external voice IME packages through the same `PackageManager.checkPermission(android.Manifest.permission.RECORD_AUDIO, packageName)` path before SwiftFloris reports the external handoff as ready.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/ExternalVoiceInputHandoffPolicyTest.kt`
- `gradle.properties` — versionCode 1914 / versionName 1.8.114

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.ExternalVoiceInputHandoffPolicyTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.
