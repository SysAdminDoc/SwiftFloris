# SwiftFloris v1.8.78

**Release date:** 2026-05-17
**Type:** Keyman `.kmp` package import foundation

## What changed

- Added `KeymanPackageParser`, a safe `.kmp` ZIP/package parser for the
  Keyman import track.
- The parser reads `kmp.json` metadata, package options, file entries,
  keyboards, languages, examples, and lexical-model metadata.
- Embedded LDML XML files are parsed through the existing
  `KeymanLdmlParser`, so packages that include importable LDML can produce
  `HardwareKeyboardLayout` descriptors immediately.
- Packages that only contain compiled `.kmx` / `.js` keyboards are classified
  as `CompiledEngineRequired` instead of being misrepresented as runnable in
  the base APK.
- Lexical-model-only and mixed keyboard/model packages are classified
  explicitly for future addon/runtime routing.
- ZIP entries are normalized and unsafe traversal / absolute / drive-letter paths are skipped
  before any future extraction code can trust package paths.

## Privacy / permissions

- No network, account, telemetry, or broad storage permission was added.
- No Keyman engine, JavaScript execution, or compiled `.kmx` runtime was added
  to `:app`.
- This is an import-planning foundation; compiled Keyman execution remains
  future addon/runtime work.

## Tests added

- `KeymanPackageParserTest` covers metadata parsing, compiled-keyboard
  classification, LDML extraction, lexical-model classification, mixed-package
  rejection, unsafe path skipping, and invalid ZIP fallback.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.KeymanPackageParserTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
