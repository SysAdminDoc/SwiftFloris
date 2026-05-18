# Release v1.8.115 — sensitive clipboard description guard

Date: 2026-05-18

Seventh-pass follow-up roster item G10.

## What changed

Clipboard item description badges no longer classify sensitive text by running URL, email, or phone-number detection over the raw clip contents.

The pin-popup text path already rendered sensitive clips through the redacted `displayText()` placeholder, but the description row still built an unredacted `stringRepresentation()` and passed it into `NetworkUtils.isUrl(...)`. A sensitive URL-like clip could therefore reveal structural information through the link badge. This release moves description classification behind `clipboardItemDescriptionKind(item)`, which returns no badge for sensitive or non-text clipboard items before reading the raw text.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardItemDescriptionKindTest.kt`
- `gradle.properties` — versionCode 1915 / versionName 1.8.115

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardItemDescriptionKindTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
