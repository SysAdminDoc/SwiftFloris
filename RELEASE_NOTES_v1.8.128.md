# SwiftFloris v1.8.128

Released: 2026-05-18

## Nastaliq Urdu font bundle

- Bundled the official OFL-1.1 Noto Nastaliq Urdu hinted TTF at `app/src/main/assets/fonts/NotoNastaliqUrdu-Regular.ttf` with the OFL text beside it.
- Extended `NastaliqFontProvider` with a Compose `FontFamily` wrapper and routing predicates for Urdu Arabic-script labels.
- Added an optional font-family override to `SnyggText` and routed Urdu subtype key labels/hints through the bundled font while keeping Latin and non-Urdu labels on the active Snygg theme font.
- Added tests that pin the committed TTF, OFL attribution file, and Urdu-only routing conditions.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.bidi.NastaliqFontProviderTest`
