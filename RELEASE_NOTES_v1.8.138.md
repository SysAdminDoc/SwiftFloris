# SwiftFloris v1.8.138

Released: 2026-05-18

## Changed

- Completed the first conservative `UnusedResources` review.
- Removed obsolete launcher/branding resources superseded by `@mipmap/ic_launcher`.
- Removed dead legacy color tokens with no manifest, code, asset, test, or dynamic lookup references.
- Documented the remaining `UnusedResources` shape so string, theme-palette, and spec-dimension buckets are handled by separate semantic review.

## Verification

- `./gradlew.bat :app:lintDebug :app:assembleDebug`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
