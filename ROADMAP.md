# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P3

## Research-Driven Additions (2026-06-29)

### P1

### P3

## Research-Driven Additions

### P1

### P2

## Research-Driven Additions (2026-06-29 refresh)

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P0

- [ ] P0 — Replace raw Tasker broadcasts with an authenticated plug-in contract
  Why: The exported receiver can perform text, clipboard, layout, and voice actions after only a global toggle, so any installed app can forge an action.
  Evidence: `app/src/main/AndroidManifest.xml`; `ime/tasker/TaskerActionReceiver.kt`; Android insecure-broadcast guidance; Tasker plug-in protocol.
  Touches: manifest; Tasker receiver/config activity and bundle schema; external-automation preferences; Tasker tests and integration documentation.
  Acceptance: Tasker configuration provisions a high-entropy per-install secret; action bundles are schema-checked and compared in constant time; unauthenticated, replayed-after-rotation, malformed, disabled, and sensitive-context actions are rejected; documented Tasker actions still work without a SwiftFloris signature permission; secrets never enter logs/backups.
  Complexity: L

- [ ] P0 — Bound, validate, and quarantine extension payloads
  Why: Metadata validation does not prevent oversized JSON, unsafe component paths, unknown layout types, or one malformed installed extension from exhausting memory or terminating keyboard collection.
  Evidence: `app/ext/ExtensionImportPolicy.kt`; `lib/ext/ExtensionManager.kt`; `ime/keyboard/KeyboardManager.kt`; Android Zip Path Traversal guidance.
  Touches: extension import policy/manager; ZIP readers; keyboard and Han component parsers; installed-extension registry; tests/fixtures.
  Acceptance: imports cap manifests at 256 KiB and component JSON at 1 MiB, cap component counts, reject duplicate/unsafe IDs and non-canonical paths, validate layout types before enum lookup, require Han SQLite paths to remain below the extracted root, and isolate/quarantine only the offending extension with a user-visible diagnostic.
  Complexity: M

- [ ] P0 — Make extension archive replacement transactional and self-cleaning
  Why: Failed save/import/export paths can delete the previous archive, expose a partial replacement, or leave loaded temporary directories behind.
  Evidence: `lib/ext/ExtensionManager.kt`; `lib/io/ZipUtils.kt`; Android `AtomicFile` contract.
  Touches: extension manager; ZIP utilities; shared atomic-file helper; import/export/save failure tests.
  Acceptance: replacements write to a same-directory temporary file, sync and atomically replace only after validation, retain the previous archive on every injected failure, always unload/clean temporary directories in `finally`, and never expose a partial archive to readers.
  Complexity: M

- [ ] P0 — Make public trust and capability claims release-gated truth
  Why: Current security/privacy/accessibility/contribution documents contradict the manifest, AIDL surface, dependency catalog, clipboard implementation, and blocked capability state.
  Evidence: `docs/THREAT_MODEL.md`; `docs/PRIVACY_AND_AI.md`; `docs/ACCESSIBILITY.md`; `CONTRIBUTING.md`; `app/src/main/AndroidManifest.xml`; `gradle/libs.versions.toml`.
  Touches: affected existing docs; capability registry; manifest/AIDL/catalog/doc integrity scripts and tests; release gate.
  Acceptance: false clipboard-encryption, permission, AIDL, SQLCipher, AI-runtime, accessibility-verification, and SDK claims are corrected; a local release gate derives high-risk assertions from live manifest/AIDL/catalog/capability data and fails on reintroduced semantic drift.
  Complexity: M

### P1

- [ ] P1 — Encrypt clipboard history at rest with a reversible migration
  Why: Sensitive clipboard text is stored in an ordinary Room database despite the product's offline privacy positioning.
  Evidence: `ime/clipboard/provider/ClipboardDatabase.kt`; SQLCipher Android documentation; existing Tink/Keystore usage.
  Touches: clipboard database/provider; persisted clipboard media; key storage; migration/recovery UI; backup policy; instrumentation tests.
  Acceptance: new history uses SQLCipher with a Keystore/Tink-wrapped key; existing plaintext rows migrate transactionally with row/content verification and rollback on failure; persisted media is encrypted or not retained; key invalidation produces an explicit recoverable reset path; no destructive fallback silently discards history.
  Complexity: L

- [ ] P1 — Protect and atomically persist sync identity
  Why: The sync private key is plaintext and the rename fallback can delete the only valid identity before a replacement is durable.
  Evidence: `ime/sync/SyncIdentityStore.kt`; Android Keystore and `AtomicFile` guidance.
  Touches: sync identity schema/store; Tink/Keystore bridge; pairing/recovery UI; backup exclusions; corruption/failure tests.
  Acceptance: PKCS#8 material is wrapped at rest, plaintext identities migrate once without changing the public identity, all writes preserve the last valid file on failure, backups exclude key material, and tamper/key-invalidation states lead to an explicit re-pair flow.
  Complexity: M

- [ ] P1 — Complete full-backup and device-transfer coverage from one data inventory
  Why: Manual and Android backup rules can omit snippets, emoji metadata, hardware layouts, and learned/personal dictionary data while the UI calls the archive complete.
  Evidence: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt`; `app/src/main/res/xml/backup_rules.xml`; `app/src/main/res/xml/data_extraction_rules.xml`; Android Auto Backup allowlist semantics.
  Touches: backup/restore manager and screens; backup manifest/schema; snippets; emoji stores; hardware layouts; dictionaries; data-extraction rules; round-trip tests.
  Acceptance: a canonical inventory classifies every persisted store as included, sensitive-excluded, or ephemeral; manual archives round-trip every included class; Android transfer rules match that inventory; restore is transactional and versioned; UI copy names exclusions; fixtures prove upgrade from the prior archive version.
  Complexity: L

- [ ] P1 — Make learned-entry failures retryable and observable
  Why: A read exception can leave the learned-entries screen loading forever, and deletion failures have no user feedback.
  Evidence: `app/settings/dictionary/LearnedEntriesScreen.kt`.
  Touches: learned-entry screen/view state; dictionary repository error mapping; toast/status/log tests.
  Acceptance: read failures render an error with Retry, deletion failures retain the row and announce failure, successful retry recovers without reopening the screen, and logs contain only operation/error classes—not learned words.
  Complexity: S

- [ ] P1 — Announce asynchronous status cards through shared live-region semantics
  Why: Backup, restore, sync, dictionary, and hardware-keyboard state changes are visible but are not announced to screen-reader users.
  Evidence: `lib/compose/src/main/kotlin/org/florisboard/lib/compose/FlorisCards.kt`; Compose accessibility semantics guidance.
  Touches: shared status cards; backup/restore/sync/dictionary/hardware screens; Compose semantics tests.
  Acceptance: status components opt into polite or assertive live regions by severity, progress-to-success/failure transitions are announced once, candidate/per-keystroke UI is excluded, and semantics tests verify the behavior with duplicate-announcement guards.
  Complexity: S

- [ ] P1 — Add localized calendar-permission recovery
  Why: Permission denial currently produces hard-coded English copy and offers no recovery when the permission is permanently denied.
  Evidence: quick-action calendar flow; Android runtime-permission guidance; localized resource architecture.
  Touches: calendar quick action; permission policy/state; string resources/locales; guarded app-settings launcher; tests.
  Acceptance: first denial shows localized rationale/retry, permanent denial offers a localized guarded app-settings action, unavailable settings activities fail safely, and grant/deny/permanent-deny tests cover every branch.
  Complexity: S

- [ ] P1 — Make voice-command normalization Unicode-safe
  Why: ASCII-only normalization collapses commands and dictation boundaries for non-Latin scripts.
  Evidence: `ime/voice/VoiceCommandParser.kt`; Unicode UAX #15 and UAX #29.
  Touches: voice command parser; locale-aware command resources; parser property and regression tests.
  Acceptance: matching uses NFC, preserves Unicode letters/numbers, applies locale-aware casing and word/grapheme boundaries, keeps punctuation commands deterministic, and passes Arabic, Cyrillic, CJK, Turkish, emoji, and combining-mark property cases without regressing English commands.
  Complexity: M

### P2

- [ ] P2 — Add scheduled encrypted local backups
  Why: Users need recurring recovery without introducing accounts or network access, and upstream FlorisBoard users explicitly request scheduled backups.
  Evidence: FlorisBoard issue #3305; Android SAF; completed canonical backup inventory prerequisite.
  Touches: WorkManager scheduler; SAF tree grants; passphrase encryption; retention policy; backup settings/status/notifications; failure tests.
  Acceptance: users choose a SAF directory, schedule, retention count, and passphrase; jobs persist grants, write versioned encrypted archives atomically, prune only verified older SwiftFloris archives, expose last success/failure, and never require `INTERNET`; implementation depends on the P1 backup-inventory item.
  Complexity: L

- [ ] P2 — Retain bounded recent glide alternatives for cursor-return correction
  Why: Glide alternatives disappear after the single pending commit, while the existing position-aware undo model can safely retain a small recent history.
  Evidence: `ime/nlp/AutoCommitUndoSession.kt`; current pending-glide commit path; FUTO Keyboard recent-candidate behavior.
  Touches: glide commit/session state; candidate presentation; editor invalidation rules; privacy/incognito gates; tests.
  Acceptance: up to five recent glide commits retain ranked alternatives; returning the cursor to an unchanged committed range restores them; edits, cursor drift, focus changes, sensitive fields, incognito, and timeout clear the entry; memory is bounded and never persisted.
  Complexity: M

- [ ] P2 — Search emoji across enrolled subtype locales
  Why: Multilingual users currently search only one resolved emoji annotation locale even when multiple subtype languages are active.
  Evidence: emoji locale resolver/assets; FUTO Keyboard 0.1.29.1 multilingual emoji search.
  Touches: emoji annotation loader/index; subtype locale registry; search ranking/deduplication; palette UI tests.
  Acceptance: the primary locale ranks first, enrolled subtype locales contribute fallback matches, identical emoji are deduplicated, missing assets degrade safely, index memory/time stay bounded, and tests cover mixed Latin/non-Latin queries.
  Complexity: M

- [ ] P2 — Expand headless UI and accessibility regression matrices
  Why: Current screenshot coverage emphasizes initial 360×640 screens and the contrast test does not assert the colors used by production status cards.
  Evidence: Roborazzi test inventory; `ime/theme/ThemeContrastTest.kt`; `FlorisCards.kt`; Compose accessibility testing guidance.
  Touches: screenshot/semantics fixtures; settings screens; theme contrast tests; local verification scripts.
  Acceptance: representative settings states run at compact, wide/landscape, RTL, and 200% font scales; loading/error/empty states are captured; shared controls have semantics assertions; contrast tests evaluate actual production color pairs in dark and light themes.
  Complexity: M

- [ ] P2 — Refresh Tink, Roborazzi, and Kotest exact pins
  Why: Tink 1.23.0, Roborazzi 1.67.0, and Kotest 6.2.2 are newer maintained releases; Roborazzi releases after the current pin include Gradle 9 output-race fixes.
  Evidence: `gradle/libs.versions.toml`; upstream Tink, Roborazzi, and Kotest release notes.
  Touches: version catalog; encryption/keystore tests; screenshot harness/baselines; unit tests; dependency documentation.
  Acceptance: exact pins update together only after changelog/license review; dependency resolution, full unit/instrumented-available tests, lint, screenshot verification, release assembly, and security gates pass locally with no unreviewed transitive runtime additions.
  Complexity: M

- [ ] P2 — Localize privacy-audit display formatting without changing its schema
  Why: The on-screen audit uses hard-coded English labels and `Locale.US` date formatting even though the app ships broad locale coverage.
  Evidence: `app/settings/privacy/PrivacyAuditScreen.kt`; stable privacy-audit JSON exporter.
  Touches: privacy-audit screen; string/date resources; JSON snapshot and locale tests.
  Acceptance: visible labels and timestamps follow the app locale and accessibility formatting, exported JSON field names/timestamps remain stable and locale-independent, and tests cover at least English, Arabic RTL, and a non-Gregorian device locale.
  Complexity: S

- [ ] P2 — Refresh stale external-blocker evidence
  Why: The FUTO swipe dataset is now public and the AGP 10 migration guide exists, although runtime licensing/benchmark and stable-release gates remain unresolved.
  Evidence: `Roadmap_Blocked.md`; `https://huggingface.co/datasets/futo-org/swipe.futo.org`; Android Gradle Plugin 10 migration guide.
  Touches: existing blocked-roadmap entries only; benchmark/license acceptance wording; upgrade acceptance wording.
  Acceptance: remove only disproven availability claims, preserve FUTO model/runtime license and benchmark gates, preserve the AGP 10 stable-release gate, and do not move either implementation into the actionable roadmap prematurely.
  Complexity: S

### P3

- [ ] P3 — Add programmable Page Up and Page Down keys
  Why: Terminal/navigation layouts expose Home and End but lack standard page navigation available in comparable hardware-oriented keyboards.
  Evidence: current `KeyCode` and terminal layout definitions; Unexpected Keyboard navigation-key set.
  Touches: key-code model/parser; built-in and custom layout schemas; labels/accessibility; terminal layout; tests.
  Acceptance: Page Up/Down can be declared in custom layouts, emit Android's page key events, have localized labels/content descriptions, remain backward-compatible with existing layouts, and are available in the terminal/navigation layer.
  Complexity: S

- [ ] P3 — Guard physical-keyboard settings launch failures
  Why: The physical-keyboard system-settings launch lacks the failure handling already used by voice-input settings.
  Evidence: `app/settings/advanced/PhysicalKeyboardScreen.kt`; guarded voice-input settings launcher.
  Touches: physical-keyboard settings action; shared intent-launch policy; localized failure copy; tests.
  Acceptance: missing/restricted settings activities cannot crash the app, failure produces localized status/toast feedback, and success/failure paths have deterministic tests.
  Complexity: S
