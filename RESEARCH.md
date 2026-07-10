# Research — SwiftFloris

Date: 2026-07-09 — replaces all prior research.

## Executive Summary

SwiftFloris is a Kotlin/Compose Android keyboard whose strongest current shape is a mature, offline-first typing surface: the base app has no `INTERNET` permission, supports local dictionaries, clipboard, snippets, glide, CJK facades, signed addons, backup/restore, diagnostics, and GitHub/Obtainium/F-Droid distribution. The highest-value direction is to close trust-boundary and recovery gaps before adding typing engines. In priority order, the best opportunities are: authenticate Tasker automation; bound and isolate extension payloads; make extension and identity persistence transactional; enforce public trust-document accuracy; encrypt clipboard history; complete backup/device-transfer coverage; make learned-data failures recoverable; announce asynchronous state changes accessibly; make voice commands Unicode-safe; then add scheduled encrypted backups, multilingual emoji search, and bounded glide-alternative history. These findings are Verified unless explicitly labeled otherwise.

## Product Map

- Core workflows: enable/select the IME; type with suggestions, autocorrect, glide, emoji, stickers, snippets, clipboard, hardware layouts, CJK facades, and voice handoff; automate via Tasker/MCP; configure privacy, language, gestures, themes, backups, diagnostics, and addons.
- User personas: privacy-focused Gboard/SwiftKey migrants; multilingual and CJK typists; offline-first users; accessibility and hardware-keyboard users; Tasker users; addon authors; maintainers and F-Droid/Obtainium reviewers.
- Platforms and distribution: Android APK, minSdk 26, targetSdk 36, compileSdk 37; GitHub Releases and Obtainium are canonical, with F-Droid metadata prepared.
- Key integrations and data flows: Room and SQLCipher stores, Tink/Android Keystore secrets, SAF import/export, signed extension archives, external voice IMEs, Tasker broadcasts, MCP AIDL, local diagnostics, and no base-app network path.

## Competitive Landscape

- FlorisBoard: provides the upstream architecture and broad extension/settings baseline. Learn from upstream compatibility and issue demand such as scheduled backups; avoid silently diverging from upstream data formats.
- HeliBoard: excels at simple offline positioning, dictionaries, layouts, and F-Droid credibility. Learn its restrained trust story; avoid sacrificing SwiftFloris's richer migration and diagnostics surfaces.
- FUTO Keyboard: leads nearby OSS work in local prediction/glide quality, recent-candidate recovery, and multilingual emoji search. Learn its bounded correction UX and language aggregation; avoid importing GPL/model-licensed runtime code or data before the existing license and benchmark gates pass.
- AnySoftKeyboard: demonstrates a durable language-pack ecosystem. Learn its package discoverability and explicit compatibility contracts; avoid accepting loosely validated package payloads.
- Fcitx5 Android and Trime/Rime show how heavyweight engines and schemas can remain modular. Learn their engine/package boundaries; avoid embedding every runtime in the base APK.
- Gboard, SwiftKey, and Grammarly set expectations for polished voice, clipboard, backup/sync, and recovery copy. Learn the interaction quality; avoid accounts, telemetry, cloud clipboard, or server rewriting that contradict the no-network base app.
- Tasker: its official plug-in protocol supports configuration activities and structured bundles. Learn that contract for discoverable, authenticated automation; avoid relying on an exported raw broadcast plus a global toggle.

## Security, Privacy, and Reliability

- Verified — exported automation trust gap: `app/src/main/AndroidManifest.xml` exposes `TaskerActionReceiver` without a permission, and `ime/tasker/TaskerActionReceiver.kt` authorizes `INSERT_TEXT`, clipboard, layout, and voice actions only through `externalAutomationEnabled`. Any installed app can forge those broadcasts after the user enables the feature. A signature permission alone is not viable because Tasker cannot hold SwiftFloris's signature permission; use an official Tasker/Locale configuration contract with a per-install secret and authenticated action bundle.
- Verified — extension payloads are metadata-checked, not payload-checked: `app/ext/ExtensionImportPolicy.kt` validates manifest identity/signature policy, while `lib/ext/ExtensionManager.kt` and `ime/keyboard/KeyboardManager.kt` still read archive/component JSON without byte/count bounds; `LayoutType.entries.first` can throw on an unknown layout type. Manifest-controlled Han SQLite paths also need canonical descendant validation. One malformed installed extension can exhaust memory or terminate the long-lived keyboard collector.
- Verified — extension replacement is destructive on failure: `lib/ext/ExtensionManager.kt` and `lib/io/ZipUtils.kt` delete or overwrite the live archive before a replacement is durably committed, and not every load/export/save path unloads temporary directories in `finally`. Use same-directory temporary files plus `AtomicFile`/fsync/rename semantics and preserve the previous archive on every failure.
- Verified — public trust documentation contains semantic drift: `docs/THREAT_MODEL.md` says clipboard history is AES-GCM encrypted, no AIDL exists beyond IME, the manifest only requests VIBRATE/POST_NOTIFICATIONS, and SQLCipher is 4.16; live code has a plaintext Room clipboard database, MCP AIDL, READ_CALENDAR/BIND_MCP declarations, and SQLCipher 4.17. `docs/PRIVACY_AND_AI.md`, `docs/ACCESSIBILITY.md`, and `CONTRIBUTING.md` also overstate delivered runtimes/verification or carry stale SDK facts. Existing live-doc checks do not catch these false claims.
- Verified — clipboard history is plaintext at rest: `ime/clipboard/provider/ClipboardDatabase.kt` is an ordinary Room database even though keyboard clipboard content can be highly sensitive. SQLCipher already exists in the dependency graph and supports export-based plaintext-to-encrypted migration; persisted media payloads need equivalent protection or explicit non-persistence.
- Verified — sync identity has secret and durability gaps: `ime/sync/SyncIdentityStore.kt` writes `privateKeyPkcs8Base64` in plaintext and its rename fallback deletes the live file before proving the replacement can land. Wrap the private key with the existing Tink/Keystore boundary, version the schema, and make migration/re-pair recovery atomic.
- Verified — backup claims exceed inventory: the manual full-backup path and `res/xml/backup_rules.xml` / `res/xml/data_extraction_rules.xml` do not consistently cover snippets, custom emoji tags, emoji pin groups, imported hardware layouts, and learned/personal dictionary state. Android `<include>` entries turn a backup section into an allowlist, so omissions silently become data loss during device transfer.
- Verified — state/recovery gaps: `app/settings/dictionary/LearnedEntriesScreen.kt` has loading/loaded states but no read-error/retry state, and deletion failures lack user feedback. The calendar-permission flow uses hard-coded English denial copy and no permanently-denied app-settings recovery.

## Architecture Assessment

- Persistence boundary: extract one tested atomic-file helper modeled on `ime/media/emoji/CustomEmojiTagStore.kt`; apply it to `SyncIdentityStore`, `EmojiPinGroupStore`, extension archives, and other state where fallback writes can truncate the live file.
- Extension boundary: validate manifest and component payloads before install, cap manifest/component bytes and component counts, reject unsafe IDs/paths and unknown layout types, and quarantine one bad installed extension without cancelling the global collector.
- UI state and observability: make long-running screens model Loading/Success/Error/Retry explicitly. Logs may record operation/error classes but must not include learned words, clipboard content, keys, tokens, or private material.
- Accessibility: opt shared asynchronous status cards in `lib/compose/.../FlorisCards.kt` into `LiveRegionMode.Polite` or `Assertive` as appropriate. Do not apply live regions to candidate/per-keystroke surfaces. Add semantics assertions, not screenshot-only coverage.
- Internationalization: `ime/voice/VoiceCommandParser.kt` uses ASCII-only `[a-z0-9]` normalization. Replace it with NFC normalization, Unicode letter/number retention, and UAX #29-aware segmentation; property-test Arabic, Cyrillic, CJK, Turkish casing, and combining marks. Localize `PrivacyAuditScreen.kt` display labels and dates while keeping exported JSON fields stable.
- Test architecture: Roborazzi covers only part of settings at one small viewport, and `ThemeContrastTest.kt` asserts tokens different from those used by production status cards. Add RTL, 200% font, wide/landscape, loading/error, and shared-card semantics cases; align contrast assertions with actual `FlorisCards` colors.
- Product opportunities after trust work: scheduled passphrase-encrypted SAF backups with retention; enrolled-locale emoji-search aggregation; and a position/edit-bounded history of glide alternatives. Reuse `AutoCommitUndoSession` rather than creating an overlapping global correction subsystem.
- Dependency/upgrade strategy: exact-pin Tink 1.23.0, Roborazzi 1.67.0, and Kotest 6.2.2 after full local regression. Tink is security-sensitive; Roborazzi releases after the current pin include Gradle 9 output-race fixes and animation capture. Kotlin 2.4.20 remains prerelease and Room 3 remains alpha, so those upgrades stay blocked.
- Category coverage: security, accessibility, i18n/l10n, observability, testing, documentation, distribution/packaging, plugin ecosystem, mobile/large-screen, offline/resilience, migration, and upgrade strategy produced concrete work. Multi-user/cloud collaboration is intentionally rejected because it conflicts with the local single-user keyboard trust model.

## Rejected Ideas

- Cloud AI, account sync, cloud clipboard, online GIF/search, telemetry prediction, and server grammar rewriting — rejected from Gboard/SwiftKey/Grammarly comparisons because the base app deliberately has no `INTERNET` permission or account model.
- A second broad correction-history subsystem — rejected from FUTO candidate-history research because `AutoCommitUndoSession` already provides bounded, position-aware correction history; only recent glide alternatives are a distinct gap.
- Immediate FUTO Swipe integration — rejected pending the existing license/profiling gate. The public `swipe.futo.org` dataset removes the old data-availability claim, but it does not resolve model/runtime licensing or SwiftFloris benchmark fit.
- A multilingual offensive-word policy now — rejected because no compatible, maintained, licensed dataset was found across supported locales; an English-only blocklist would create false confidence.
- Flexible symbol-stack redesign — deferred after Unexpected Keyboard comparison because it would require layout schema, editor, migration, and discoverability work disproportionate to current trust gaps.
- Room 3 or Kotlin 2.4.20 migration now — rejected because the available releases are alpha/prerelease; keep the existing blocked upgrade entries until stable artifacts exist.
- Re-add Android 17 candidate-selected accessibility or rotation-visibility work — rejected because the current source already implements those behaviors; only physical-password validation remains device-gated.
- Google Play publication and GitHub CI/Dependabot — rejected because repository policy uses locally built artifacts and GitHub/Obtainium/F-Droid distribution.

## Sources

OSS keyboards and package ecosystems:
- https://github.com/florisboard/florisboard
- https://github.com/florisboard/florisboard/issues/3305
- https://github.com/HeliBorg/HeliBoard
- https://github.com/futo-org/android-keyboard
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.29.1
- https://huggingface.co/datasets/futo-org/swipe.futo.org
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/Julow/Unexpected-Keyboard

Commercial and adjacent products:
- https://support.google.com/gboard/answer/11197787
- https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-backup-sync-in-microsoft-swiftkey-keyboard
- https://support.microsoft.com/en-US/swiftkey-keyboard/how-does-the-clipboard-work-with-microsoft-swiftkey-keyboard
- https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide
- https://tasker.joaoapps.com/pluginslibrary.html

Platform, standards, security, and dependencies:
- https://developer.android.com/privacy-and-security/risks/insecure-broadcast-receiver
- https://developer.android.com/reference/android/util/AtomicFile.html
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://developer.android.com/develop/ui/compose/accessibility/testing
- https://developer.android.com/training/permissions/requesting
- https://www.unicode.org/reports/tr15/
- https://www.unicode.org/reports/tr29/
- https://www.zetetic.net/sqlcipher/sqlcipher-api/#sqlcipher_export
- https://github.com/tink-crypto/tink-java/releases
- https://github.com/takahirom/roborazzi/releases
- https://github.com/kotest/kotest/releases

## Open Questions

- None.
