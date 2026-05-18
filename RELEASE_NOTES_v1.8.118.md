# Release v1.8.118 — clipboard media clone failure guard

Date: 2026-05-18

Seventh-pass clipboard agent finding #2.

## What changed

Foreign `content://` image/video clipboard URIs that fail during provider cloning no longer create phantom IME-local history rows.

Previously `ClipboardMediaProvider.insert(...)` caught every media-clone failure and returned a synthetic provider URI ending in `/0`. `ClipboardItem.fromClipData(...)` accepted that URI, so the clipboard history could contain image/video entries whose private backing file and provider metadata never existed. This release makes clone failures propagate, rejects null or sentinel provider insert results before a `ClipboardItem` is created, and logs/skips failed system-clipboard imports in `ClipboardManager`.

The provider also stops running image EXIF orientation parsing for videos, so valid video clipboard imports are not rejected by image-only metadata parsing before the copy step.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaClonePolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardMediaSafetyPolicyTest.kt`
- `gradle.properties` — versionCode 1918 / versionName 1.8.118

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardMediaSafetyPolicyTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.
