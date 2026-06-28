# Research - SwiftFloris

## Executive Summary
SwiftFloris is a privacy-first Android IME fork whose strongest current shape is local verifiability: no `INTERNET` permission, encrypted local learning, offline migration/imports, addon signing pins, privacy proof surfaces, and an active local verification toolchain. The highest-value direction is still trust-proof cleanup after workflow removal, followed by reliability work in the text surfaces users hit every day. Top opportunities, in order: replace workflow-era release claims with local evidence; add a SQLCipher + Room runtime sentinel; remove personal-dictionary main-thread Room access; cap oversized clipboard text rendering; keep Smartbar actions available in hardware-keyboard/desktop mode; make custom layouts row-count-aware with stable popup anchoring; add public-doc dependency/release drift checks; unblock Compose BOM 2026.06.00; upgrade stable lagging dependencies such as AboutLibraries; add explicit diagnostics for snippet/layout imports; replace the Snygg URI resolver stub; broaden Roborazzi coverage for new settings surfaces.

## Product Map
- Core workflows: enable/setup IME, configure themes/layouts/languages/typing, type with suggestions/glide/snippets/voice handoff, import/export dictionaries/layouts/backups/stickers, review local privacy/release evidence.
- User personas: privacy-conscious Android keyboard users, SwiftKey/proprietary-keyboard migrators, multilingual and RTL users, physical-keyboard/tablet/foldable users, power users building snippets/layouts/themes, addon authors, release/security reviewers.
- Platforms and distribution: Android 8+ base APK, GitHub/Obtainium/F-Droid-prepared channels, Gradle/AGP/Kotlin/Compose build, no Play dependency and no network permission by design.
- Key integrations and data flows: Room + SQLCipher personal dictionary, Tink-wrapped local secrets, local backup/restore archives, FUTO/external voice IME handoff, addon APK discovery/signing pins, signature-protected automation broadcasts, local MCP daemon bridge, SAF sticker folders.

## Competitive Landscape
- FlorisBoard / HeliBoard: broad FOSS keyboard customization and active pressure around backup, stickers, 4-row layouts, popup anchoring, language layouts, emoji, clipboard, and suggestion controls. Learn from live issue demand; avoid GPL code intake into `:app` and avoid silently inheriting upstream reliability bugs.
- FUTO Keyboard: strong offline prediction positioning plus active hardware-keyboard, desktop-mode, RTL, CJK, action-bar, and suggestion-control work. Learn from keeping toolbar actions useful when a physical keyboard is attached; avoid incompatible licensing or bundling opaque model assets into the base APK.
- AnySoftKeyboard / fcitx5-android / Trime: prove sustained demand for no-network multilingual input, CJK/Rime-style engines, modular language packs, and power-user layout controls. Learn from modularity; avoid turning the base APK into a full native engine bundle.
- Keyman / Unicode LDML / keyboard3-style layout ecosystems: mature keyboard data formats show that custom layout validation, row sizing, key metrics, and parse diagnostics are product features, not just importer internals.
- SwiftKey / Samsung / Grammarly-class keyboards: set expectations for writing assistance, migration polish, voice, physical keyboard behavior, and account-backed sync. Learn from polish and onboarding; reject network/account/cloud parity in the base app.
- Espanso and text-expansion tools: snippet import is valuable only when failures are visible and recoverable. Learn from predictable parse diagnostics; avoid silent partial imports.
- F-Droid / Obtainium / Android developer verification: distribution trust is now part of the product. Learn from reproducible-build and sideloading-policy expectations; avoid stale CI/SBOM/SLSA claims after local-build-only cleanup.

## Security, Privacy, and Reliability
- Verified: `.github/workflows` is absent after `73dc7d15`, but live docs still promise workflow-backed no-network scans, dependency scans, release workflows, SLSA attestations, SBOMs, emulator smoke, benchmark workflows, and Dependabot (`README.md`, `docs/SECURITY.md`, `docs/REPRODUCIBLE_BUILDS.md`, `docs/PRIVACY_AND_AI.md`, `docs/addons/apk-validation.md`, `.github/PULL_REQUEST_TEMPLATE.md`). This remains the top trust bug.
- Verified: live docs still route contributors to missing canonical files. `AGENTS.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and `.github/workflows` are absent, while `CLAUDE.md`, `CONTRIBUTING.md`, several docs, and README live sections still reference them. Canonical tracked release notes are README release bullets plus `fastlane/metadata/android/en-US/changelogs/*.txt`.
- Verified: public dependency/release copy drifted. `README.md` still has a `v1.9.52` highlight header while `gradle.properties` is `1.9.53`; live docs cite Tink 1.21.0 and Roborazzi 1.63.0 while `gradle/libs.versions.toml` has Tink 1.22.0 and Roborazzi 1.64.0.
- Verified: `DictionaryManager.kt` still uses `allowMainThreadQueries()` on the personal dictionary path, and `docs/THREAT_MODEL.md` already calls this a known UI-lag risk. The exact Room 2.8.4 + SQLCipher 4.16.0 pair also intersects SQLCipher issue #81.
- Verified: `ClipboardInputLayout.kt` renders `item.displayText()` without a preview length cap in `ClipItemView`; upstream FlorisBoard PR #3303 fixes a clipboard crash by truncating long displayed text. SwiftFloris should cap render text without truncating stored/pasted content.
- Verified: physical-keyboard policy suppresses the entire input view when a hardware keyboard is available (`FlorisImeService.onEvaluateInputViewShown`, `PhysicalKeyboardPolicy.inputViewVisibilityDecision`). FUTO PR #2138 and issue #2137 show users expect a hardware-keyboard/desktop mode where the touch keyboard can stay hidden while toolbar/suggestion actions remain available.
- Verified: custom-layout editing exists, but row-count-aware runtime sizing/popup anchoring is not explicit in the current rendering contract. HeliBoard issues #2542 and #2543 show 4-row custom layouts can become unusable when extra rows shrink into a fixed height or popups shift off their expected base keys.
- Verified: `lib/snygg/.../SnyggUriValue.kt` still returns `Result.failure(NotImplementedError(...))` for default asset path resolution. Theme import/rendering should fail with typed user-actionable errors, not an implementation stub.
- Verified: snippet, hardware-layout, and benchmark import paths tolerate malformed lines, but some parsers silently skip records (`EspansoMatchParser`, `KlcLayoutParser`, `SwipeTraceImporter`). Dictionary import already has better skipped-count UI; snippets/layouts need matching diagnostics.
- Verified: OSV-facing dependency posture is not currently blocked by public advisories found in this pass, but AboutLibraries 15.0.2 is now stable while SwiftFloris remains on 14.2.0 after previously skipping only the 15.0 beta.
- Missing guardrails: one local release-evidence entry point, live-doc canonical-source integrity, README release-state truth checks, Room/SQLCipher runtime sentinel, bounded clipboard preview rendering, hardware-keyboard Smartbar-only mode tests, row-count-aware custom-layout tests, and Roborazzi coverage for new settings surfaces.

## Architecture Assessment
- Boundary improvement: release/security proof must now be local-first. A single script or Gradle task should compose no-network, data-extraction, fastlane/release-front-door, repo hygiene, OSV, reproducible build, lint/test/build, and artifact-hash evidence.
- Refactor candidate: move personal dictionary Room operations behind an IO-bound repository boundary and remove production `allowMainThreadQueries()` in `DictionaryManager.kt`.
- Refactor candidate: separate clipboard stored content from UI preview content so very large text clips paste intact but render as bounded, accessible summaries.
- Refactor candidate: split physical-keyboard visibility into touch-keyboard visibility and Smartbar/action-surface visibility so desktop and USB/Bluetooth keyboard users keep useful editing controls.
- Refactor candidate: make custom layout row count and popup origin part of the layout policy, with tests for 3-row, 4-row, number-row, and popup mapping behavior.
- Refactor candidate: make public docs derive or verify dependency/tool versions from `gradle/libs.versions.toml`, `gradle-wrapper.properties`, and `gradle.properties`.
- Refactor candidate: add a live-doc integrity checker for non-archive Markdown so missing-file links and stale canonical-source references cannot reappear.
- Refactor candidate: replace the Snygg default URI resolver stub with a typed unsupported-path result and tests.
- Test gaps: `PersonalDictionaryEncryptionTest` checks wiring but not the Room + SQLCipher runtime transaction path; Roborazzi baselines omit custom layout editor, snippets, privacy audit, sync, backup, and restore; physical-keyboard tests do not cover Smartbar-only presentation.
- Documentation gaps: docs are strong but stale around CI, release provenance, dependency versions, canonical source files, release-state labels, and local evidence commands.

## Rejected Ideas
- Base-APK cloud rewrite/translation/sync parity: rejected because SwiftKey/Samsung/Grammarly-style parity would require network/account paths that contradict `CONTRIBUTING.md`, `README.md`, and `docs/PRIVACY_AND_AI.md`.
- Re-adding GitHub Actions, Dependabot, Renovate, SLSA, or SBOM automation: rejected because current repo rules explicitly removed workflows and dependency bots; solve with local commands and docs that describe the real trust chain.
- Pulling GPL keyboard code from HeliBoard/fcitx5/Trime into `:app`: rejected because SwiftFloris keeps the base APK Apache-2.0-compatible.
- Full Rime/fcitx native engine in the base APK: rejected because SwiftFloris already routes native/heavy capabilities through isolated signed addons.
- Direct SwiftKey account import: rejected because the cloud export endpoint is retired and the repo already supports local export/import paths.
- Device-only TalkBack/Switch Access/API 37/tablet/foldable validation as active roadmap work: rejected until a device lane exists; keep those checks parked in `Roadmap_Blocked.md`.

## Sources
OSS keyboards:
- https://github.com/florisboard/florisboard
- https://github.com/florisboard/florisboard/pull/3303
- https://github.com/florisboard/florisboard/issues/3300
- https://github.com/florisboard/florisboard/pull/3302
- https://github.com/HeliBorg/HeliBoard
- https://github.com/HeliBorg/HeliBoard/issues/2587
- https://github.com/HeliBorg/HeliBoard/issues/2585
- https://github.com/HeliBorg/HeliBoard/issues/2584
- https://github.com/HeliBorg/HeliBoard/issues/2547
- https://github.com/HeliBorg/HeliBoard/issues/2542
- https://github.com/HeliBorg/HeliBoard/issues/2543
- https://github.com/futo-org/android-keyboard
- https://github.com/futo-org/android-keyboard/pull/2138
- https://github.com/futo-org/android-keyboard/issues/2137
- https://github.com/futo-org/android-keyboard/issues/2133
- https://github.com/futo-org/android-keyboard/issues/2108
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/keymanapp/keyman
- https://github.com/osfans/trime

Commercial, adjacent, and community:
- https://support.microsoft.com/en-us/swiftkey-keyboard/
- https://www.samsung.com/us/support/answer/ANS10001617/
- https://support.grammarly.com/hc/en-us/categories/115000018611-Grammarly-for-Mobile
- https://espanso.org/docs/get-started/

Platform, dependency, and security:
- https://developer.android.com/developer-verification
- https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
- https://repo1.maven.org/maven2/com/mikepenz/aboutlibraries-core/maven-metadata.xml
- https://github.com/sqlcipher/sqlcipher-android/issues/81
- https://unicode.org/reports/tr35/tr35-keyboards.html

## Open Questions
None. The remaining blocked decisions require credentials, device hardware, external releases, or maintainer policy choices already represented outside the active roadmap.
