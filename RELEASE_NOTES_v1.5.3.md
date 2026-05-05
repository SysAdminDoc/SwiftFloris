# SwiftFloris v1.5.3

Released: 2026-05-05

## Changes

- Expanded the built-in English autocorrect dictionary to 49,744 entries for stronger offline suggestions and spell correction.
- Fixed immediate autocorrect for standalone `i` so it becomes `I` in the middle of sentences.
- Fixed immediate autocorrect for common English contractions, including `im` -> `I'm`, `ill` -> `I'll`, `id` -> `I'd`, and `ive` -> `I've`.
- Fixed the same contraction autocorrections when typed at the beginning of sentences after auto-capitalization, including `Im`, `Ill`, `Id`, and `Ive`.
- Preserved all-caps acronym behavior so inputs such as `ID` and `ILL` are not rewritten as contractions.
- Added regression coverage for the immediate autocorrect paths and Latin dictionary behavior.

## Verification

- `:app:testDebugUnitTest`
- `:app:lintDebug`
- signed release APK verification with Android `apksigner`
- device install smoke test with `adb install -r`

## APK Details

- File: `SwiftFloris-v1.5.3.apk`
- SHA-256: `1d5f414ab1c0decd74d97c00aadea2769bc07d19010fdbc76f4ed2caaca1e777`
- Signing certificate SHA-256: `b5d537420ded9e11382b3df17dc3616f212b9d9f35138e4fbb3f2adffe50f70a`
