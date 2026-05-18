# Release v1.8.109 — clipboard backup excludes sensitive items + video clear-all leak

Date: 2026-05-17

Two seventh-pass audit findings from the clipboard agent (#11 + #19),
both about user-data leaks the existing eviction logic was skipping.

## What changed

### BackupScreen: drop `isSensitive` rows before writing the backup zip

[`app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt#L213) —
the clipboard-history backup path previously serialised every history
row into the zip regardless of `isSensitive`. Backup zips are
user-portable artifacts (Syncthing, USB, cloud sync at user's choice)
and the file is **not passphrase-encrypted** (unlike the personal-
dictionary backup, v1.8.65). Any password / OTP / TOTP code that
landed in the clipboard history before v1.8.105's primary-clip
`EXTRA_IS_SENSITIVE` gate landed could be serialised in plaintext into
the backup zip.

The fix filters `clipboardHistory.filterNot { it.isSensitive }`
before the three serialisation paths (text, image, video) split it
by `ItemType`. Both legacy rows (carrying the sensitive bit from
pre-v1.8.105 history) and any future rows that slip through are
covered by a single point of filtering.

### ClipboardItem.close: extend to `ItemType.VIDEO`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt#L226) —
the prior `close(context)` impl deleted the content-provider URI only
when `type == ItemType.IMAGE`. Two parallel leaks for video clipboard
items:

1. **Storage leak.** The file under `noBackupFilesDir/clipboard_files/<id>`
   never gets garbage-collected. Every video clip the user clears
   leaves the on-disk bytes behind.
2. **Privacy leak.** Per-receiver `grantUriPermission` calls issued
   through `ClipboardMediaProvider` are only revoked when the
   provider's `delete(uri)` is called. Skipping `delete` on video
   keeps the grants live until receiver-process death — apps that
   were granted READ on the video URI keep the read window open even
   after the user explicitly cleared history.

The fix extends the gate to `type == ItemType.IMAGE || type == ItemType.VIDEO`.
Text items remain a no-op (no provider-backed URI).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`
- `gradle.properties` — versionCode 1909 / versionName 1.8.109

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction:

**Backup-sensitive test (API 33+):**
- Copy a credential from a password manager that sets
  `EXTRA_IS_SENSITIVE`.
  (Without v1.8.105 the row will be in history; v1.8.105+ refuses to
  insert, so this test reproduces the leak in legacy history rows.)
- Trigger Settings → Backup & restore → Backup; unzip the resulting
  archive and open `clipboard/clipboard_text_items.json`.
  - **Pre-fix:** the credential text appears in plaintext.
  - **Post-fix:** the credential is omitted.

**Video clear-all test:**
- Use `adb shell content insert` or a video-share-to-IME app to push
  a video clipboard item that lands in history.
- Verify `adb shell ls /data/data/dev.patrickgold.florisboard.debug/no_backup/clipboard_files/`
  contains the video file.
- In the IME, clear all clipboard history.
- **Pre-fix:** the video file remains on disk; per-receiver
  `grantUriPermission` is not revoked.
- **Post-fix:** the video file is gone; subsequent
  `openFile` from a previously-granted receiver fails with
  `FileNotFoundException`.
