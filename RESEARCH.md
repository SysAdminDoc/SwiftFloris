# Research — SwiftFloris

Date: 2026-07-29 — replaces all prior research.

## Executive Summary

SwiftFloris is a mature Kotlin/Compose Android IME whose strongest shape is a general-purpose, privacy-first keyboard: the base APK has no network permission or account, while local dictionaries, multilingual prediction, glide, clipboard/media, themes, migration, backup, signed addons, Tasker, MCP, diagnostics, and accessibility foundations are already substantial. The highest-value direction is therefore not another visible feature layer; it is making every privileged boundary uphold the same offline, bounded, recoverable contract that the product promises. Unless marked otherwise, findings below are **Verified**.

Top opportunities, in priority order:

1. Finish the existing P0 Tasker authentication and extension isolation/atomicity work.
2. Bound clipboard text before history ingestion so external multi-megabyte clips cannot stall or exhaust the IME.
3. Reject MCP daemon packages that request network permissions; discovery currently contradicts the documented local-only enrollment rule.
4. Encrypt every portable backup containing clipboard content and make restore tamper-safe and transactional.
5. Complete the existing clipboard-at-rest, sync-identity, and canonical backup-inventory work.
6. Report real MCP binder state instead of treating discovered daemons as “bound.”
7. Add low-RAM/OOM glide degradation and a cross-editor `InputConnection` compatibility contract.
8. Ratchet trust-critical localization coverage and refresh performance evidence with reproducible provenance.
9. Centralize bounded keyboard-mode history to remove panel-specific return-state bugs.
10. Later, accept Unicode Keyboard3 layouts through the hardened local-addon boundary.

## Product Map

- Core workflows: enable/select the IME; type, correct, glide, dictate through an external voice IME, and use emoji, stickers, snippets, clipboard, hardware layouts, and CJK/transliteration paths; configure themes, languages, privacy, automation, addons, backup/restore, and diagnostics.
- User personas: privacy-conscious Gboard/SwiftKey migrants; multilingual and minority-script typists; offline and low-connectivity users; hardware-keyboard, accessibility, Tasker, and addon users; maintainers and F-Droid/Obtainium reviewers.
- Platforms and distribution: Android 8.0+ (`minSdk 26`), target SDK 36, compile SDK 37; local builds plus GitHub/Obtainium artifacts, with F-Droid preparation; Apache-2.0 base application.
- Key integrations and data flows: Room/SQLCipher stores, Tink/Android Keystore secrets, SAF and share-sheet import/export, signed extension/addon APKs, external voice IMEs, exported Tasker automation, local Binder/AIDL MCP daemons, local benchmark/diagnostic evidence, and no base-app network path.

## Competitive Landscape

- FlorisBoard: does upstream architecture and broad customization well. Learn from current demand for scheduled backup, editor compatibility, and Keyboard3 interchange; avoid diverging from compatible data formats without a migration.
- HeliBoard: does a restrained offline keyboard, custom dictionaries, and pragmatic state/resource fixes well. Learn its oversized-clipboard report and bounded prior-layout controller; avoid copying GPL code into the Apache base.
- FUTO Keyboard: leads nearby work in local glide quality, multilingual emoji lookup, correction recovery, and voice. Learn its bounded recent-alternative UX and active-language behavior; avoid its GPL/runtime and separate model-license surface until existing license and benchmark gates pass.
- AnySoftKeyboard: demonstrates a durable language-pack ecosystem and graceful low-memory gesture degradation. Learn its low-RAM/OOM policy and explicit package contracts; avoid loosely validated third-party payloads.
- Fcitx5 Android, Trime/Rime, and Keyman: demonstrate multilingual engines and data packs behind modular boundaries. Learn their package/schema separation; avoid embedding native engines, catalogs, or network update channels in the base APK.
- Unexpected Keyboard, Thumb-Key, and CleverKeys: explore privacy-first layouts and alternate input geometry well. Learn focused interaction experiments and the value users place on a network-free manifest; avoid replacing SwiftFloris’s familiar general-purpose layout model with a specialist geometry.
- Gboard and SwiftKey: set expectations for multilingual correction, migration, backup, translation, and transparent learned-data controls. SwiftKey’s OneDrive transition, completed on 2026-05-31, makes learned data human-readable and user-exportable; learn that recovery transparency, but avoid accounts, cloud clipboard, and server learning.
- Grammarly and Samsung Keyboard: show demand for on-device personalization, writing assistance, rich accessibility modes, and tiered advanced features. Learn explicit consent, mode controls, and local fast-path architecture; avoid cloud rewriting, device lock-in, account requirements, and paywall-driven scope.

## Security, Privacy, and Reliability

- **Verified — unbounded clipboard ingress:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt:183-204` materializes and persists text without a byte limit; only the UI preview is capped. HeliBoard issue #2697 reports multi-second/unresponsive behavior around a 120 KiB clip. Oversized clips should remain available for one-shot system paste but never enter history, search, classification, deduplication, or encryption.
- **Verified — plaintext portable clipboard backup:** `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt:311-362` writes non-sensitive clipboard text and media into an ordinary ZIP; the one-tap full-backup path selects all local clipboard classes. The share path at `BackupScreen.kt:386-404` drops workspace ownership so the receiver can keep reading, leaving no deterministic app-owned cleanup point. Android guidance recommends excluding highly sensitive data or requiring end-to-end encryption.
- **Verified — MCP enrollment does not enforce its privacy contract:** `docs/PRIVACY_AND_AI.md:213-227` and `README.md:294-298` say enrollable daemons cannot declare `INTERNET`, but `ime/mcp/McpAndroidDiscoverer.kt` checks metadata, catalog, signature, and bind permission only. A trusted separate APK can therefore receive selected text and still request network access. Reuse the network-permission denylist already implemented by `ime/addon/AddonEnumerator.kt:51-73`.
- **Verified — MCP status can be false:** `ime/mcp/McpServiceLifecycle.kt` publishes discovered daemons before asynchronous binding and ignores the bind result; `app/settings/mcp/McpSettingsScreen.kt:134-161` derives “bound” counts from `McpDaemonRegistry`, not live binders. `McpServiceConnectionManager` handles false, null, dead, and disconnected bindings internally but exposes no observable state to Settings.
- **Verified — existing P0/P1 trust gaps remain correctly prioritized:** the exported Tasker receiver is unauthenticated; extension components lack complete payload/path isolation and transactional replacement; clipboard history is plaintext Room storage; and `ime/sync/SyncIdentityStore.kt` persists a plaintext private key with a destructive rename fallback. These are already in `ROADMAP.md` and must not be duplicated.
- **Verified — build-tool advisory remains blocked, not fixed by the latest stable patch:** Kotlin 2.4.0 is below the 2.4.20 fix for CVE-2026-53914’s unsafe build-cache deserialization, while 2.4.10 was the latest stable release on 2026-07-29. This is a privileged local build-cache risk, not an IME runtime vulnerability. The existing blocked upgrade is still correct.
- **Verified — advisory query scope:** an OSV batch query on 2026-07-29 found no additional direct advisories for the queried Tink 1.22.0, SQLCipher Android 4.17.0, Kotlin stdlib 2.4.0, coroutines/serialization, Coil 3.4.0, Room 2.8.4, AndroidX Core 1.18.0, ZXing 3.5.4, Robolectric 4.16.1, or Kotest 6.1.11 coordinates. This does not replace a resolved-transitive dependency audit.

## Architecture Assessment

- Clipboard boundary: introduce one ingress policy before `ClipboardItem` creation that caps retained UTF-8 bytes, sensitive classification, previews, search, and deduplication. It must precede the existing clipboard-encryption migration so expensive work never receives unbounded external input.
- Backup boundary: add one versioned authenticated-encryption envelope shared by manual and scheduled backups. Build plaintext workspace data only in app-private cache, expose ciphertext only, verify credentials/authentication before restore mutation, retain the last valid state on failure, and prune abandoned export workspaces.
- MCP boundary: make discovery return explicit accepted/rejected reasons including network permission; keep discovery, trust, binding, and enabled-tool state distinct. Publish `Pending`, `Connected`, `Failed`, `Dead`, and `Disabled` state from `McpServiceConnectionManager`, and dispatch only through live binders.
- IME compatibility: upstream issue #3313 and HeliBoard discussion #2137 show editor-specific physical-space and suggestion behavior. Add fake `InputConnection`/`EditorInfo` fixtures plus foreground Chrome and Word/Collabora smoke checks; diagnostics may record editor metadata, never entered text.
- Memory policy: `ime/nlp/latin/LatinLanguageProvider.kt` fixes a 120,000-word glide set and `ime/text/gestures/StatisticalGlideTypingClassifier.kt` retains multiple pruners/caches without a low-RAM tier. Skip construction on `ActivityManager.isLowRamDevice`, clear partial allocations after injected OOM, and disable glide for the session with localized explanation.
- Mode state: direct mutations in `ime/keyboard/KeyboardManager.kt` plus the clipboard panel’s forced return to text create inconsistent nested-panel behavior. A constant-space, capped prior-mode controller should own every keyboard/media/clipboard/numpad transition and reset across editors.
- Localization: the base file has 2,359 translatable resources, while representative locale files contain only 836–1,005 (`values-ar`, `values-de`, `values-fr`, `values-es`, `values-ja`, `values-ru`, `values-zh-rCN`). Add deterministic critical-flow coverage and regression reporting; distinguish complete UI locales from fallback/typing-only coverage rather than implying full translation.
- Test and performance evidence: 31 Roborazzi snapshots are concentrated at 360×640/SDK 35, already covered by the existing matrix item. Separately, all six committed benchmark references in `docs/benchmark-results/` date to 2026-05-18 despite later NLP, rendering, backup, and Compose changes. Refresh them on a release-like physical device with commit, app version, OS/device, compilation mode, iterations, and variance metadata.
- Dependency strategy: the existing exact-pin item is still valid but its evidence moved to Tink 1.23.0, Roborazzi 1.70.0, and Kotest 6.2.3 by 2026-07-20. SQLCipher Android 4.17.0 and Gradle 9.6.1 are current. Review changelogs and resolved transitives, then run the full local release lane; do not fold Kotlin 2.4.20, AGP 10, or Room 3 prereleases into that batch.
- Category coverage: security, privacy, reliability, accessibility, i18n/l10n, observability, testing, documentation, distribution/packaging, plugin ecosystem, mobile/large-screen behavior, offline/resilience, migration, and upgrade strategy produced concrete work. Cloud multi-user collaboration is intentionally excluded because it conflicts with the local single-user IME trust model.

## Rejected Ideas

- Cloud AI, account sync, cloud clipboard, online GIF/search, telemetry, and server rewriting — rejected from Gboard, SwiftKey, Grammarly, and Samsung comparisons because they contradict the base app’s no-network/no-account contract.
- Immediate SwiftKey OneDrive importer — deferred from Microsoft’s replacement account flow completed on 2026-05-31: the files are documented as human-readable/exportable, but no public schema or redacted fixture was found, so an importer would be speculative.
- Bundled FUTO swipe, HeliBoard, Trime, Unexpected Keyboard, CleverKeys, or Thumb-Key code — rejected because GPL/AGPL code cannot be combined into the Apache-only base; FUTO models also have a separate license boundary.
- A bundled Rime/Keyman/LLM runtime or online addon store — rejected as base-APK bloat and a maintenance/network expansion; retain explicit local addon packages and existing runtime gates.
- Silent clipboard truncation or unlimited history — rejected from clipboard issue research because truncation corrupts user data and unlimited retention preserves the denial-of-service path; skip persistence while preserving one-shot paste.
- A second global correction-history system — rejected because `AutoCommitUndoSession` already provides position/edit invalidation; extend the existing bounded glide-alternative item only.
- A full in-keyboard text editor, automatic URL rewriting, or specialist layout redesign — rejected because each changes user data or the core interaction model without stronger project-specific demand than the current trust/reliability gaps.
- Multi-user/shared keyboard profiles — rejected because identity, conflict, authorization, and network requirements contradict the project’s local single-user design; transport-neutral personal-dictionary sync remains the correct boundary.
- Google Play publication, remote CI provenance, and automatic online dependency updates — rejected because the repository deliberately uses local release evidence and GitHub/Obtainium/F-Droid channels.
- Persistent physical-keyboard status icons — under consideration only; fcitx5-android validates the pattern, but no SwiftFloris-specific demand outweighs permanent status-bar clutter.

## Sources

OSS keyboards, issues, releases, and ecosystems:

- https://github.com/SysAdminDoc/SwiftFloris
- https://github.com/florisboard/florisboard
- https://github.com/florisboard/florisboard/issues/3305
- https://github.com/florisboard/florisboard/issues/3313
- https://github.com/florisboard/florisboard/discussions/3295
- https://github.com/HeliBorg/HeliBoard
- https://github.com/HeliBorg/HeliBoard/issues/2697
- https://github.com/HeliBorg/HeliBoard/pull/2648
- https://github.com/HeliBorg/HeliBoard/discussions/2137
- https://github.com/futo-org/android-keyboard
- https://github.com/futo-org/android-keyboard/issues/2192
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.29.1
- https://gitlab.futo.org/keyboard/swipe-library/-/blob/master/LICENSE
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/4840
- https://github.com/Julow/Unexpected-Keyboard/issues/1358
- https://github.com/dessalines/thumb-key
- https://github.com/tribixbite/CleverKeys
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/keymanapp/keyman/blob/master/android/README.md
- https://github.com/seedvault-app/seedvault
- https://github.com/termux/termux-tasker

Commercial and adjacent products:

- https://support.microsoft.com/en-us/swiftkey-keyboard/swiftkey-backup-and-sync-with-onedrive
- https://support.microsoft.com/en-us/swiftkey-keyboard/account
- https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-backup-sync-in-microsoft-swiftkey-keyboard
- https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-microsoft-swiftkey-keyboard-with-more-than-one-language
- https://support.google.com/gboard/answer/12373137
- https://support.google.com/gboard/answer/10742542
- https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide
- https://www.grammarly.com/blog/engineering/personal-language-model/
- https://www.grammarly.com/plans
- https://www.samsung.com/us/support/answer/ANS10000943/
- https://docs.syncthing.net/users/versioning.html
- https://docs.syncthing.net/users/syncing.html
- https://tasker.joaoapps.com/pluginslibrary.html

Awesome lists and community signal:

- https://github.com/pluja/awesome-privacy
- https://albertomosconi.github.io/foss-apps/categories/keyboards.html
- https://news.ycombinator.com/item?id=40831489
- https://news.ycombinator.com/item?id=25669538
- https://www.reddit.com/r/androidapps/comments/1u5s0yc/what_are_good_keyboards_not_swiftkeygoogle/
- https://www.reddit.com/r/DigitalPrivacy/comments/1uxghrw/privacy_smartphone_keyboard_app_with_swipe/
- https://stackoverflow.com/questions/tagged/android-input-method?tab=Newest

Platform, standards, research, and engineering:

- https://developer.android.com/reference/android/content/pm/PackageInfo
- https://developer.android.com/develop/background-work/services/bound-services
- https://developer.android.com/reference/android/content/ServiceConnection
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- https://developer.android.com/reference/android/app/ActivityManager
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://developer.android.com/develop/ui/compose/accessibility/testing
- https://developer.android.com/guide/topics/resources/localization
- https://developer.android.com/guide/topics/resources/pseudolocales
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- https://modelcontextprotocol.io/specification/2025-03-26/index
- https://modelcontextprotocol.io/docs/tutorials/security/security_best_practices
- https://www.unicode.org/reports/tr35/tr35-keyboards.html
- https://www.unicode.org/reports/tr15/
- https://www.unicode.org/reports/tr29/
- https://unicode.org/versions/Unicode17.0.0/
- https://aclanthology.org/2026.acl-demo.32/
- https://pmc.ncbi.nlm.nih.gov/articles/PMC7881442/
- https://arxiv.org/abs/2410.18100
- https://research.google/blog/improving-gboard-language-models-via-private-federated-analytics/

Dependencies and advisories:

- https://github.com/tink-crypto/tink-java/releases/tag/v1.23.0
- https://github.com/takahirom/roborazzi/releases/tag/1.70.0
- https://github.com/kotest/kotest/releases/tag/v6.2.3
- https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.17.0
- https://kotlinlang.org/docs/releases.html
- https://nvd.nist.gov/vuln/detail/CVE-2026-53914
- https://www.jetbrains.com/privacy-security/issues-fixed/
- https://osv.dev/

## Open Questions

- A redacted post-2026-05-31 SwiftKey OneDrive export fixture and its provenance are required before a OneDrive learned-data importer can be specified or prioritized correctly.
- Which locales does the project intend to advertise as fully localized Settings UI, rather than typing-language or fallback coverage? The repository contains resources but no explicit completeness policy; that decision sets the release-gate threshold.
