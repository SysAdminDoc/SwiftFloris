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
- `UserDictionaryCombinedListCodec` skips blank export lines so encrypted combined-list exports round-trip through the importer.
- `DictionaryImporter` avoids the API-33-only `ByteArrayOutputStream.toString(Charset)` overload on minSdk 26 devices.
- `HardwareKeyboardRuntimeMapper` tolerates null platform key-code names in JVM/Robolectric and uses an explicit alphanumeric fallback for source-name matching.
- `ZipUtilsTest` now asserts the current abort-on-path-traversal contract instead of the retired ignore-and-continue contract.
- `UserStickerRepositoryTest` matches the MIME-spoof guard by using null SAF MIME for extension fallback coverage.
- `WordStylesCanvasRenderer` selects `Typeface` styles without bitwise flag composition.
- `StateAdapters` remembers the mapped flow before `collectAsState`.
- `data_extraction_rules.xml` suppresses lint's intentional-exclude false positives while preserving the build-pinned no-D2D-leak excludes.
- The debug Roborazzi host activity is no longer a launcher activity.
- X25519 sync pairing is API-gated to Android 13+ so minSdk 26 devices do not reach API-33-only Java crypto classes.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardPreviewImagePolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/FlorisCopyToClipboardActivity.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardMediaSafetyPolicyTest.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/addons/AddonsSettingsScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryImporter.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/UserDictionary.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/SealedBoxCrypto.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairingPayloadGenerator.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/AuroraAnimatedThemeBackground.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/wordstyles/WordStylesCanvasRenderer.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/StateAdapters.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/debug/AndroidManifest.xml`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepositoryTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickActionArrangementTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtilsTest.kt`
- `gradle.properties` — versionCode 1911 / versionName 1.8.111

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
```

Result: pass. The full JVM unit-test suite passes after the stale build-gate repairs.

```powershell
./gradlew.bat :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.
