# Release v1.8.120 — local voice catalog preview gate

Date: 2026-05-18

Seventh-pass follow-up roster item G1.

## What changed

The local Whisper/Vosk voice path is now explicitly preview-only until a real in-app recognizer runtime ships.

Previously the settings UI exposed a local model catalog with download/import actions, and `VoiceRecognitionEngineSelector` could route Auto, Embedded Whisper, or Vosk streaming to local engines once a matching model and microphone permission were present. That was misleading because the app does not bundle `AudioRecord`, Vosk JNI, or whisper.cpp runtime glue.

This release adds a `VoiceLocalRecognizerRuntime.AVAILABLE` gate, defaults it to false, and makes local routes report `LOCAL_RECOGNIZER_RUNTIME_UNAVAILABLE` unless a future runtime explicitly opts in. Auto continues to fall back to the enabled external voice keyboard. Settings -> Voice input now marks the local model catalog as preview-only, disables download/import while the runtime is absent, and leaves delete available for already-imported files.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceRecognitionEngineSelection.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/voice/VoiceInputScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceRecognitionEngineSelectorTest.kt`
- `gradle.properties` — versionCode 1920 / versionName 1.8.120

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceRecognitionEngineSelectorTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
