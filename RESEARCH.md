# Research — SwiftFloris

## Executive Summary

SwiftFloris is a privacy-first Android IME fork whose strongest current shape is local verifiability: no `INTERNET` permission, encrypted local learning, offline migration/imports, addon signing pins, privacy proof surfaces, and an active local verification toolchain. The project has recently shipped public-doc drift checkers, Compose BOM 2026.06.00, Gradle 9.6.1, clipboard preview capping, Snygg typed-failure resolution, physical-keyboard Smartbar-only mode, and import diagnostics at the parser tier.

The highest-value direction is now: (1) unblock compileSdk 37 — AGP 9.2.1 already supports it, correcting a false blocker that held back Android 17 APIs and AboutLibraries 15.x; (2) clean up stale trust claims in release notes and THREAT_MODEL.md left by workflow removal and the `allowMainThreadQueries()` fix; (3) wire the already-shipped parser-side import diagnostics into the snippet/layout import UI; (4) complete custom-layout row-count-aware sizing; (5) expand Roborazzi baselines for the six new settings surfaces; (6) evaluate FUTO Swipe as a GPL-licensed addon (cannot enter `:app`); (7) add a scrollable/expanded suggestion strip mode, which is the single most-requested UX feature across HeliBoard/community issue traffic.

## Product Map

- Core workflows: enable/setup IME, configure themes/layouts/languages/typing, type with suggestions/glide/snippets/voice handoff, import/export dictionaries/layouts/backups/stickers, review local privacy/release evidence.
- User personas: privacy-conscious Android keyboard users, SwiftKey/proprietary-keyboard migrators, multilingual and RTL users, physical-keyboard/tablet/foldable users, power users building snippets/layouts/themes, addon authors, release/security reviewers.
- Platforms and distribution: Android 8+ base APK, GitHub/Obtainium/F-Droid-prepared channels, Gradle/AGP/Kotlin/Compose build, no Play dependency and no network permission by design.
- Key integrations and data flows: Room + SQLCipher personal dictionary, Tink-wrapped local secrets, local backup/restore archives, FUTO/external voice IME handoff, addon APK discovery/signing pins, signature-protected automation broadcasts, local MCP daemon bridge, SAF sticker folders.

## Competitive Landscape

- **FUTO Keyboard** (v0.1.29.1, Jun 2026): Strongest competitor. Shipped FUTO Swipe (7.38% top-1 error vs Gboard 11.05%), but the library is **GPLv3** with a proprietary model-weights license — incompatible with Apache-2.0 `:app`. Integration is only possible as an isolated addon APK. FUTO also ships a custom 1MB emoji compat font (vs emoji2's 9MB), swipe memory for past-12-word context, and local transformer prediction. SwiftFloris should evaluate the addon-APK integration path and learn from the lightweight emoji approach; avoid taking GPL code into the base APK.
- **HeliBoard** (v4.0-beta1, Jun 2026): Active with internal refactoring, key hint font size, Korean fixes. Top user requests: scrollable suggestion strip (#2584), translation support (#2518), emoji sort by usage (#2506). Still depends on Google's proprietary gesture library for glide. SwiftFloris should learn from the scrollable-suggestion demand signal; avoid the Google-library dependency.
- **FlorisBoard** (v0.6.0-alpha02, Jan 2026): Stalled — 5+ months since last release. Clipboard, suggestions, and glide remain alpha-quality. The perpetual-rewrite pattern is a cautionary example. SwiftFloris already diverged successfully.
- **FOSS keyboard community consensus** (Reddit/Lemmy/Techlore/F-Droid): The #1 complaint is glide typing quality. #2 is autocorrect confidence and multi-language awareness. #3 is CJK input as a single-keyboard solution. #4 is reproducible-build trust signals. #5 is TalkBack accessibility — zero FOSS keyboards advertise it.
- **Android platform** (API 36-37): AGP 9.2.0+ supports compileSdk 37, correcting the blocked-roadmap claim that AGP 9.3.0 is required. Android 17 brings `TextAttribute` for CJK accessibility, physical keyboard password visibility settings, and IME visibility non-restoration after config changes. Compose BOM 2026.06.00 adds `InputTextSuggestionState` and `TextCompositionRange` for transliteration composition tracking. Google developer verification enforcement begins September 2026 for pilot regions.

## Security, Privacy, and Reliability

- **Stale trust claim (README)**: The v1.9.44 release note says "Release workflow gains SLSA Build Level 2 provenance attestation and SPDX SBOM generation" but workflows were deleted in commit `73dc7d15`. `docs/SECURITY.md` correctly disclaimed remote attestation, but the README release bullet is misleading. Should append a correction note.
- **Stale gap claim (THREAT_MODEL.md)**: Line 232 still lists `allowMainThreadQueries()` as a known gap, but commit `765295b9` moved personal dictionary Room access to IO. The threat-model verification checklist should reflect the fix.
- **DictionaryManager.kt runBlocking**: Line 546 uses `runBlocking(Dispatchers.IO)` which is not on the CI allowlist (`scripts/runblocking-allowlist.txt`). The gate should either allowlist it with rationale or the code should migrate to a suspend path.
- **Kotlin CVE-2026-53914** (MEDIUM, CVSS 6.7): Kotlin 2.4.0 has an unsafe-deserialization vulnerability in build cache metadata. Code execution possible if an attacker can inject malicious cache entries. Fixed in 2.4.20 with no API changes. Low risk for solo/small-team builds; real risk for CI with shared Gradle caches. Drop-in upgrade.
- **Dependency posture**: SQLCipher 4.16.0, Tink 1.22.0, Room 2.8.4, Compose BOM 2026.06.00 are current with no known public advisories. SQLCipher 4.14.0's WAL-mode corruption fix is already in the 4.16.0 pin. Tink has a documented (no CVE) envelope AEAD malleability limitation — practical risk is low for the SQLCipher passphrase wrapping use case.
- **compileSdk 37 blocker is false**: `Roadmap_Blocked.md` says "AGP 9.3.0 is not yet available" as the blocker for compileSdk 37. AGP 9.2.0+ supports API 37. This false blocker holds back Android 17 TextAttribute APIs, physical keyboard password behavior verification, and AboutLibraries 15.x.

## Architecture Assessment

- **Unblocked upgrade path**: compileSdk 36 → 37 is now possible on AGP 9.2.1. This unlocks `TextAttribute` for CJK accessibility, `show_passwords_physical` behavior testing, and the AboutLibraries 15.x upgrade (which requires `androidx.core:core-ktx:1.19.0`, which requires compileSdk 37).
- **Compose transliteration APIs**: BOM 2026.06.00 ships `InputTextSuggestionState` and `TextCompositionRange`. These map directly to SwiftFloris's CJK composition path and could improve the Han shape-based provider's feedback to Compose text fields.
- **Sizing dead space**: `FlorisImeSizing.keyboardUiHeight()` uses `rowCount.coerceAtLeast(4)`, so 3-row layouts get 4 rows of total height. Combined with the per-row cap at `keyboardRowBaseHeight * 1.12f` in `TextKeyboardLayout.kt`, this leaves ~40dp dead space on 3-row layouts. The existing roadmap item covers this.
- **Suggestion strip UX**: The current strip shows 3 fixed candidates. HeliBoard #2584 and community posts show demand for a scrollable/expanded strip showing more candidates.
- **Android 17 config change behavior**: `CONFIG_KEYBOARD` and `CONFIG_KEYBOARD_HIDDEN` no longer trigger activity recreation by default. `FlorisImeService.onConfigurationChanged` should be verified to still handle keyboard attach/detach correctly.
- **Test gaps**: Roborazzi baselines still missing for CustomLayoutEditor, SnippetSettings, PrivacyAuditLog, Sync, Backup, Restore (existing roadmap item). Smartbar-only mode has policy tests but no visual baseline. Import diagnostics have parser tests but no UI-side test.

## Rejected Ideas

- **FUTO Swipe in `:app`**: Rejected — GPLv3 + proprietary model license is incompatible with Apache-2.0. Can only ship as an isolated addon APK under its own license. Source: `gitlab.futo.org/keyboard/swipe-library` license review.
- **Re-adding GitHub Actions/SLSA/SBOM**: Rejected — repo rules explicitly removed workflows; solve with local commands. Source: commit `73dc7d15`, global CLAUDE.md rules.
- **emoji2-to-custom-font migration**: Rejected for now — the FUTO approach (custom 1MB font) saves 8MB APK but requires maintaining the font asset pipeline. Low priority against reliability work. Source: FUTO v0.1.29 release notes.
- **Full CJK Pinyin/Wubi engine in base APK**: Rejected — production CJK data sourcing/licensing not resolved; addon architecture is the correct path. Already in Roadmap_Blocked.md.
- **Scrollable suggestion strip as P1**: Rejected at P1 tier because it's a UX enhancement, not a trust/reliability fix. Placed at P2 behind the compileSdk unblock and doc corrections.

## Sources

OSS keyboards:
- https://github.com/Helium314/HeliBoard (v4.0-beta1, Jun 2026)
- https://github.com/Helium314/HeliBoard/issues/2584
- https://github.com/Helium314/HeliBoard/issues/2518
- https://github.com/Helium314/HeliBoard/issues/2506
- https://github.com/futo-org/android-keyboard (v0.1.29.1, Jun 2026)
- https://gitlab.futo.org/keyboard/swipe-library (GPLv3 license)
- https://swipe.futo.tech/
- https://github.com/florisboard/florisboard (v0.6.0-alpha02, Jan 2026)
- https://github.com/nicknisi/Unexpected-Keyboard (v2.0.4, May 2026)

Platform and dependencies:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://developer.android.com/build/releases/about-agp
- https://developer.android.com/jetpack/androidx/releases/compose
- https://developer.android.com/jetpack/androidx/releases/room
- https://kotlinlang.org/docs/whatsnew24.html
- https://github.com/sqlcipher/sqlcipher-android/releases

Community demand:
- https://discuss.techlore.tech/t/what-keyboard-are-you-using-on-android/6588
- https://lemmy.world/post/12769494
- https://f-droid.org/en/2025/03/04/even-my-keyboard-is-built-reproducibly.html
- https://www.androidpolice.com/spent-years-switching-android-keyboards-this-one-changed-everything/

Distribution and verification:
- https://developer.android.com/developer-verification
- https://support.google.com/android-developer-console/answer/16561738

## Open Questions

1. **compileSdk 37 naming mismatch**: GitHub Actions runner-images issue #13859 reports `android-37.0` vs `android-37` naming inconsistency. For local builds this may not matter, but verify that the local Android SDK installs `platforms/android-37` correctly before bumping compileSdk.
