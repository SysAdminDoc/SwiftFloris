# Release v1.8.87 — FLAG_SECURE on the encrypted-dictionary passphrase dialog

Date: 2026-05-17

Follow-up #2 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt#L742-L780)
— the `DictionaryPassphraseDialog` is the input surface for both
encrypted-export and encrypted-import of the personal dictionary (the
ROADMAP §6 N7.4 encrypted-blob round-trip that shipped in v1.8.54 /
v1.8.65). Two defects:

1. **No `FLAG_SECURE` on the host window.** The host
   `FlorisAppActivity` does not set `FLAG_SECURE` for its window globally
   (and shouldn't — the rest of Settings is screen-recordable for support /
   bug-report screenshots). While the passphrase dialog was up, screen
   recordings, external-display mirroring, and the system screenshot
   gesture could all capture the typed passphrase. The
   `PasswordVisualTransformation` only masks the rendered glyph; the
   passphrase characters are still in the surface layer.
2. **Passphrase stored via `rememberSaveable`.** `rememberSaveable` round-
   trips state through Android's `savedInstanceState` bundle, which is
   recoverable via `am dumpstate`, crash reports, and the platform's
   restore-after-process-death path. Passphrase state must not be
   serialised.

This release:

- Adds a `DisposableEffect` keyed on the host view that sets
  `WindowManager.LayoutParams.FLAG_SECURE` on entry and clears it on
  dispose. The flag is set only while the passphrase dialog is composed,
  so the rest of Settings remains screen-recordable. Both the export
  (with confirmation) and import (without confirmation) flows use the
  same dialog, so both are covered.
- Switches `passphrase` and `passphraseConfirmation` from
  `rememberSaveable` to plain `remember`. The dialog already re-prompts
  on every show (because it's only composed when the visibility flag is
  true), so losing in-flight passphrase state across configuration changes
  is the correct behaviour, not a regression.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt`
- `gradle.properties` — versionCode 1887 / versionName 1.8.87

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Open Settings → User Dictionary → encrypted export. Verify the
  passphrase dialog appears. Trigger a system screen recording (or
  external-display mirror). Verify the recording shows a black surface
  for the dialog area (FLAG_SECURE), not the typed passphrase. Cancel
  the dialog and screenshot Settings — that should still work normally
  (FLAG_SECURE cleared on dispose).
- Open the same dialog, type a partial passphrase, rotate the device.
  Verify the field is empty after rotation (was: it survived rotation
  via the savedInstanceState bundle).
- Repeat for the encrypted-import flow.
