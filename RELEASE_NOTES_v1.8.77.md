# SwiftFloris v1.8.77

**Release date:** 2026-05-17
**Type:** User-imported sticker folder

## What changed

- Added `prefs.sticker.userFolderUri` and a Settings → Emoji & stickers
  "Imported sticker folder" SAF tree picker with a clear action.
- Added `UserStickerRepository`, which turns a persisted local document-tree
  URI into an `Imported` sticker pack.
- Imported packs accept PNG, WebP, JPEG, and GIF documents, with extension
  fallback when Android reports a generic MIME type.
- `StickerPaletteView` appends the imported pack to the bundled sticker packs
  and decodes local previews off the main thread.
- `StickerMediaProvider` now proxies imported sticker content through the
  existing provider authority, so rich-content insertion continues to use the
  same `InputConnectionCompat.commitContent` path.

## Privacy / permissions

- No network, account, broad media-library, or gallery permission was added.
- The selected folder is read through a user-granted SAF URI.
- File deletion from the chosen folder is intentionally left for a later
  explicit SAF write-flow polish item.

## Tests added

- `UserStickerRepositoryTest` covers supported image filtering, extension
  fallback, empty-folder handling, imported-pack caps, and duplicate URI
  collapse.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.media.sticker.UserStickerRepositoryTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
