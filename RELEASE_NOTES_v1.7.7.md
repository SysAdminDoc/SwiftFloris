# SwiftFloris v1.7.7 - Premium UX polish

**Released:** 2026-05-13
**Type:** Product polish / UX refinement.

This release focuses on making the settings and extension-management experience feel more deliberate, legible, and trustworthy after the v1.7.6 hardening pass.

## Highlights

- Refined the main settings experience with clearer hierarchy, calmer status cards, and more useful action labels.
- Improved first-run setup with stronger privacy framing and clearer recovery expectations.
- Polished voice input setup and status messaging so unavailable, retryable, and enabled states are easier to understand.
- Improved backup and restore copy for destructive or trust-sensitive flows.
- Reworked extension import states with clearer empty, review, skipped-file, and technical-detail surfaces.
- Upgraded extension detail pages with overview, metadata, and management sections instead of flat metadata rows.
- Replaced debug-style component output with structured component metadata rows for themes and language packs.

## Verification

- `:app:compileDebugKotlin`
- `:app:verifyNoInternetPermission`
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:assembleRelease`
- Fresh adb uninstall/install smoke on a connected phone
