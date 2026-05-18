# Release v1.8.113 — voice setup intent hardening

Date: 2026-05-18

Seventh-pass follow-up roster item G7.

## What changed

`VoiceInputSetupActivity` now has an explicit setup-intent contract instead of accepting arbitrary extras and silently falling back to `NO_ENABLED_PROVIDER`.

The manifest already declares the activity as `android:exported="false"`; this release pins that with a Robolectric manifest test and adds `VoiceInputSetupIntentContract` so the only accepted input is a single `reason` extra whose value matches a known `VoiceInputSetupReason`. Missing reasons, unknown values, and unexpected extra keys now finish the transparent setup activity without rendering a misleading setup dialog.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputSetupActivity.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputSetupActivityManifestTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputSetupIntentContractTest.kt`
- `gradle.properties` — versionCode 1913 / versionName 1.8.113

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceInputSetupIntentContractTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceInputSetupActivityManifestTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.
