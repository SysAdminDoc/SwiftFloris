# SwiftFloris v1.8.75

**Release date:** 2026-05-17
**Type:** Hardware-keyboard import parser

## What changed

- Added `MacKeylayoutParser` for macOS `.keylayout` XML files.
- Normalizes macOS key-map/modifier-map data into the existing
  `HardwareKeyboardLayout` / `HardwareKeyEntry` representation used by the
  Windows KLC and Keyman LDML importers.
- Supports normal, Shift, Option-as-AltGr, and Shift+Option slots.
- Selects the `<keyMapSet>` referenced by `<layouts>`, with a first-set fallback.
- Captures action-backed dead-key outputs when the action exposes an output.
- Uses the same XXE-hardened `DocumentBuilderFactory` posture as the Keyman
  LDML parser because imported layouts cross a user/addon trust boundary.

## Tests added

- `MacKeylayoutParserTest` covers metadata extraction, modifier-slot mapping,
  command/control-map ignoring, referenced map-set selection, missing
  modifier-map fallback, dead-key action output capture, malformed XML fallback,
  and DOCTYPE / external-entity rejection.

## Verification

- `git diff --check`
- Parser/banned-permission source scan: no app manifest network-permission
  changes.
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.MacKeylayoutParserTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
