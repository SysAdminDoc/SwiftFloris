# Release v1.8.111 — clipboard media intake size caps

Date: 2026-05-18

Seventh-pass clipboard follow-up roster items G2 and G12, plus minimal build-gate repairs needed to verify the slice on the current toolchain.

## What changed

[`ClipboardFileStorage.cloneUri`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt) now receives the clipboard media kind and copies provider-backed files through the existing `ContentResolver.readToFile(..., maxSize)` limit:

- image clips: 32 MiB cap
- video clips: 128 MiB cap
- failed oversize copies delete the partial private file before returning the provider failure URI

[`ClipboardMediaProvider`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt) passes the table-derived image/video kind into that storage boundary, so the cap is enforced before a hostile `content://` stream can fill `noBackupFilesDir/clipboard_files`.

[`FlorisCopyToClipboardActivity`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/FlorisCopyToClipboardActivity.kt) now validates preview dimensions before decode through [`ClipboardPreviewImagePolicy`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardPreviewImagePolicy.kt). The modern `ImageDecoder` path and legacy `BitmapFactory` path both reject unknown or >8192 px bounds before allocating the preview bitmap, while still downscaling accepted previews to a 1024 px longest edge.

## Build-gate repairs

The first verification run reached source compilation and exposed stale HEAD errors unrelated to this clipboard slice. This release fixes them because leaving the repo unbuildable would make the G2/G12 verification meaningless:

- `FlorisImeService` imports `KeyVariation` from the current `ime.text.key` package.
- `AddonsSettingsScreen` uses JetPref's `enabledIf` API for the rescan row.
- `NlpManager` precomputes trailing-context locale frequencies before calling the non-suspend `TrailingContextLanguageBlend.score` callback.
- `AuroraAnimatedThemeBackground` drops the obsolete `matchParentSize` import.
- `QuickActionArrangementTest` calls `contains(...)` explicitly for the default-arrangement quick-action assertions.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardPreviewImagePolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/FlorisCopyToClipboardActivity.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardMediaSafetyPolicyTest.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/addons/AddonsSettingsScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/AuroraAnimatedThemeBackground.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickActionArrangementTest.kt`
- `gradle.properties` — versionCode 1911 / versionName 1.8.111

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.clipboard.ClipboardMediaSafetyPolicyTest"
```

Expected result: five focused clipboard media-safety tests pass, and the task also exercises `verifyNoInternetPermission`, `verifyNoInternetPermissionMergedDebug`, `verifyDataExtractionRules`, and debug Kotlin/unit-test compilation.

Full debug verification for this release:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
