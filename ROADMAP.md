# SwiftFloris Roadmap

> Single source of truth for all planned work. Items above the --- are existing plans; items below are research conducted 2026-06-03 and later cycles.

**Current release:** v1.8.247 (versionCode 2047). **Local verification:** subtype switch-by-id crash hardening verified with focused `:app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.core.SubtypeManagerSwitchByIdTest`; full local gate `:app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` also passes with the documented JDK 21 override.

Hard rules still apply (see `AGENTS.md`): no `INTERNET` permission in `:app`; Apache-2.0 ceiling on `:app`; no closed-source blobs; one logical change per commit; every shipped release bumps `gradle.properties` version, writes a `CHANGELOG.md` section, and adds a `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (draft <=480 chars for headroom).

Item IDs trace to their origin research: `F#`/`EI#` from the archived 2026-05-25 research feature plan; `R#`/`O#` from the 2026-05-25 second-pass findings; `WS#` from the archived improvement-plan workstreams; `N#`/`Next-#`/`L#` from the archived roadmap tiers. Shipped items and reframed/rejected items live in `COMPLETED.md`; full release detail in `CHANGELOG.md`. Historical strategy (tiered NOW/NEXT/LATER, sourced appendix) is preserved at `docs/archive/ROADMAP_v5.67_2026-05-18.md`.

> Last researched: Cycle 24 - 2026-06-06.

## ▶ Implementer Instructions (for the build machine)

This roadmap is fed continuously by an automated research machine. On every
pass, the implementing machine should:

1. `git pull --rebase` to get the latest researched items before starting.
2. Work the open 🤖 items top-down by priority (P0 -> P3). Build them properly:
   multi-file structure, real error handling, no runtime auto-install hacks,
   version strings synced, docs/CHANGELOG updated in the same commit.
3. In addition to building items, run a full UX audit each pass. Walk every
   screen / page / dialog / form / table / empty-loading-error-disabled state
   across light/dark/high-contrast themes. Check onboarding, navigation clarity,
   spacing/contrast/alignment, clipping/overflow, hierarchy, microcopy,
   destructive-action guards, keyboard + screen-reader accessibility, and trust
   signals. Fix what you find, or file it back as a new 🤖 roadmap item if it
   is larger than a pass.
4. Check off ✅ each item you complete (leave it in place with the checkmark),
   commit per logical change with a "why" message, and push.
5. Never edit this Implementer Instructions block or the 🔬 Researcher Queue
   headings. Never force-push.

Keep the `:app` invariant strict: no internet/network permissions, Apache-2.0
ceiling, no closed-source blobs, and network or incompatible features only in
isolated addon APKs. Shipped work belongs in `CHANGELOG.md`; completed roadmap
items belong in `COMPLETED.md`.

## Existing Planned Work

### Keyboard surface & visual polish (device-gated)

- [ ] P1 — Keyboard surface polish + manual-override verification (WS11)
  - Why: Candidate-row, smartbar, and software-key states plus the full layout matrix need real-field verification; cannot fully close without a device.
  - Touches: candidate-row selection/pressed/disabled/correction states; smartbar ordering + overflow + long-label resilience; software-key pressed/held/disabled/gesture states; one-handed/floating/split/compact/landscape/tablet layouts.
  - Acceptance: each state and layout verified in real input fields on a device.
  - Source: docs/archive/TODO_2026-06-03.md B / improvement-plan WS11.
- [ ] P1 — Glide-trail theme baselines + low-end perf evidence (F9)
  - Why: Glide-trail themes lack Roborazzi baselines and low-end (<=4 GB) performance evidence.
  - Touches: Roborazzi baselines (device/emulator); trace `swiftfloris.glide.trailDrawMs` on Pixel 4a / Galaxy A12-class.
  - Acceptance: baselines recorded; trail-draw timing captured on low-end hardware.
  - Source: docs/archive/TODO_2026-06-03.md B / research feature plan F9.
- [ ] P2 — F40 Roborazzi capture phase (F40 capture)
  - Why: Screen-level Roborazzi test classes ship baseline-pending (v1.8.201); the baseline PNGs still need on-device capture.
  - Touches: `:app:recordRoborazziDebug` for the A1 test classes, then remove the class-level `@Ignore` from the pending F40 screenshot classes.
  - Acceptance: baseline PNGs captured; `@Ignore` removed; gate green.
  - Source: docs/archive/TODO_2026-06-03.md A1/B / research feature plan F40.
- [ ] P2 — Glide-trail reduced-animation + tooltip verification (EI4 residual)
  - Why: Confirm Rainbow/Aurora/Neon glide trails honour `ANIMATOR_DURATION_SCALE == 0f` on-device; the doc disclosure already shipped (v1.8.182).
  - Touches: GesturesScreen "i" tooltip + on-device animation-scale check.
  - Acceptance: trails respect zero animation scale; tooltip present.
  - Source: docs/archive/TODO_2026-06-03.md B / second-pass EI4.

### Data safety, backup/restore & import (device-gated portions)

- [ ] P1 — Backup/restore + import path-safety device confirmation (WS13 device portions)
  - Why: Unit tests for these paths are Tier A and done; the on-device confirmation is still required.
  - Touches: backup/restore overwrite-vs-merge; clipboard media missing-file/path-safety; extension-import path-traversal; `StickerMediaProvider.openFile` SAF-grant allow-list validation for imported stickers.
  - Acceptance: overwrite/merge, missing-media, traversal, and imported-sticker open-file behaviors are confirmed on-device and by provider-level regression tests; forged encoded sticker URIs are rejected without breaking legitimate user-picked sticker folders.
  - Source: docs/archive/TODO_2026-06-03.md B / improvement-plan WS13; `docs/AUDIT_2026-06-02.md:159-164`.

### CI, build & release hardening

- [ ] P3 — API 37 / Kotlin 2.4 dependency compatibility follow-up
  - Why: The v1.8.216 freshness pass verified Kotlin `2.4.0` and AndroidX Core `1.19.0` as current, but Kotlin has no matching KSP `2.4.0` plugin artifact yet and AndroidX Core `1.19.0` requires `compileSdk 37`.
  - 2026-06-04 recheck: Maven metadata still reports Kotlin `2.4.0` as current and KSP `2.3.9` as the latest KSP Gradle plugin; AndroidX Core `1.19.0` AAR metadata declares `minCompileSdk=37`. This row stays open.
  - Touches: `gradle/libs.versions.toml`, `gradle/tools.versions.toml`, API 37 behavior-gate docs.
  - Acceptance: bump Kotlin only after a compatible KSP plugin is published; bump AndroidX Core only with the compileSdk 37 behavior-gate plan and full Gradle/Roborazzi verification.
  - Source: v1.8.216 dependency freshness pass.

### Docs & hygiene

- [x] P2 — Localization content-quality pass (WS12)
  - Why: Turkish repeated-word lint, vague/abrupt English source labels, and inconsistent failure/destructive copy need cleanup.
  - Touches: native-safe Turkish repeated-word review; tighten English source labels; standardize backup/restore/import/export failure + destructive-confirmation copy; document translation-safe cleanup rules.
  - Acceptance: lint warnings reviewed; copy standardized; rules documented.
  - Source: docs/archive/TODO_2026-06-03.md A5 / improvement-plan WS12.
  - Shipped: v1.8.243 (2026-06-04) with native-safe Turkish repeated-word
    cleanup, clearer theme/import source labels, standardized backup/restore/
    dictionary/extension failure and generic destructive-confirmation copy,
    repo-hygiene translation-safe cleanup rules, and focused
    `LocalizationCopyTest` resource coverage.
- [x] P2 — Visual-QA + manual-QA + release-evidence checklists (WS10 / WS15)
  - Why: No standing checklists for the portrait/landscape/compact/floating/dark/high-font-scale matrix, manual QA, or release evidence.
  - Touches: docs for visual-QA matrix, manual-QA flow, and release-evidence capture.
  - Acceptance: three checklists exist and are referenced from the verification docs.
  - Source: docs/archive/TODO_2026-06-03.md A5 / improvement-plan WS10/WS15.
  - Shipped: v1.8.244 (2026-06-04) with `docs/QA_CHECKLISTS.md` covering the
    visual-QA matrix, manual-QA flow, and release-evidence checklist, plus
    links from local verification, contributing, accessibility, and README docs.
- [x] P3 — Fastlane changelog drafting guide (R5)
  - Why: No documented guidance on drafting the <=480-char fastlane changelog.
  - Touches: add the guide to `docs/LOCAL_VERIFICATION.md` / `docs/REPO_HYGIENE.md`.
  - Acceptance: guide present with the character-budget rule.
  - Source: docs/archive/TODO_2026-06-03.md A5 / second-pass R5.
  - Shipped: v1.8.245 (2026-06-04) with Fastlane changelog drafting rules in
    repo hygiene, local verification, contributor, and operator-facing release
    docs.
- [x] P3 — Document module build-cache survival (O1)
  - Why: `lib/<module>/build/` cache survives `git rm --cached`; this surprises contributors.
  - Touches: note in `docs/REPO_HYGIENE.md`.
  - Acceptance: behavior documented.
  - Source: docs/archive/TODO_2026-06-03.md A5 / second-pass O1.
  - Shipped: v1.8.246 (2026-06-04) with repo-hygiene guidance for
    `git rm --cached`, surviving ignored module `build/` directories, and safe
    local cleanup expectations.

### External-action-blocked / sibling-repo / XL (maintainer decision required)

These are genuine blockers — each needs an account, key, sibling repo, ML infra, or a product decision the code cannot make.

- [ ] P0 — Crowdin sync of v1.8.179 + v1.8.186 string drops (R1)
  - Why: 44 stale translated entries across 22 locales; Crowdin web console is source of truth (lint `UnusedResources` until done).
  - Touches: server-side Crowdin sync/pull.
  - Acceptance: translations synced; stale-entry lint clears.
  - Source: docs/archive/TODO_2026-06-03.md C / second-pass R1.
- [ ] P1 — FlorisBoard `0.6.0-alpha02` cherry-picks (F22)
  - Why: Upstream CLDR 48, Emoji 17, number-field fix, and floating-window foundation are worth picking up; conflict resolution needs iterative on-device builds and risks regressing shipped features.
  - Touches: cherry-pick + conflict resolution across input/emoji/layout.
  - Acceptance: picks merged without regressing shipped features; on-device verified.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F22.
- [ ] P1 — Apache-2.0 glide model trained on the MIT FUTO swipe dataset (F21)
  - Why: A licensed in-tree glide model needs off-device ML training infra (XL, out-of-tree). FUTO Keyboard v0.1.29 now publishes FUTO Swipe, a public 1M-swipe QWERTY English dataset, top-1/top-4 benchmark framing, and an open-source swipe system, sharpening this from "train a model" into "evaluate against a public test set before integrating."
  - Touches: external training pipeline + model integration; candidate-row top-4 display policy if the model exposes alternatives.
  - Acceptance: Apache-2.0-clean model trained and integrated; before merge, report top-1 and top-4 error on the public FUTO filtered test-set framing and document whether SwiftFloris should expose accepted word + 3 alternatives after glide completion. The non-shipping public benchmark harness is tracked separately as R18-1.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F21; https://github.com/futo-org/android-keyboard/releases/tag/0.1.29.
- [ ] P2 — Bundled Vosk small-en-us recognizer addon (F8)
  - Why: Needs a sibling addon repo + JNI; `RECORD_AUDIO` only in the addon, never `:app`.
  - Touches: sibling addon repo, JNI binding.
  - Acceptance: recognizer ships as a signed addon; `:app` stays permission-clean.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F8.
- [ ] P2 — CycloneDX SBOM + SLSA provenance on release (F10)
  - Why: Needs GitHub Attestations onboarding + release-tag dispatch.
  - Touches: release workflow attestation step.
  - Acceptance: SBOM + provenance attached to releases.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F10.
- [ ] P2 — GPG-signed release tags (F11)
  - Why: Needs a maintainer GPG key.
  - Touches: release-tag signing.
  - Acceptance: tags are GPG-signed and verifiable.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F11.
- [ ] P2 — F-Droid `fdroiddata` submission (F12)
  - Why: `dev.patrickgold.florisboard(.beta)` package-id collides with upstream; needs a rename/coexistence decision plus a multi-month review queue.
  - Touches: fdroiddata metadata + package-id decision.
  - Acceptance: submission accepted into the F-Droid queue.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F12.
- [ ] P2 — FunctionGemma 270M MCP-bridge addon (F30)
  - Why: Needs a sibling addon repo.
  - Touches: sibling addon repo + MCP bridge.
  - Acceptance: addon bridges FunctionGemma over MCP without linking into `:app`.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F30.
- [ ] P3 — Cross-platform desktop dictionary-export CLI (F13)
  - Why: Needs a sibling repo.
  - Touches: standalone CLI project.
  - Acceptance: CLI exports the dictionary format cross-platform.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F13.

#### Open questions blocking the external-action items (maintainer decisions)

1. F-Droid package-id: coexist with upstream FlorisBoard `.beta` or rename? (blocks F12)
2. Vosk 40 MB addon in 2026, or voice stays FUTO-handoff-only? (affects F8 + EI7 copy)
3. Maintainer GPG key (Yubikey-backed?) for signed tags? (affects F11)
4. F-Droid submission timing — during the migration spike or a quiet week? (affects F12)

---

## Research-Driven Additions

### Researcher Queue (Cycle 24 - 2026-06-06)

- [x] 🔬 `sync-paired-device-corruption-recovery-recheck-2026-06-06` -
  rechecked paired-device JSON parsing, Settings -> Sync empty-device UI,
  focused tests, existing reset/corruption copy patterns, and Android DataStore
  corruption-handling guidance. This cycle does not duplicate R23-1's secret
  key storage; it adds the user-visible recovery branch for corrupted paired
  remote-device state.

#### Sync paired-device recovery

- [ ] 🤖 P1 — Surface corrupt paired-device state instead of treating it as "no devices" (R24-1)
  - Why: Once Sync pairing becomes real state, corrupt `pairedDevicesJson` means
    "we cannot trust/read the saved device list," not "the user has never paired
    a device." The current helper collapses blank JSON, malformed JSON, and
    validation failures into the same empty list. That hides data loss, makes
    Settings -> Sync show the benign "No paired devices yet" copy, and lets a
    later successful pairing overwrite the unreadable raw state without a clear
    reset/re-pair decision.
  - Evidence: `PairedSyncDevice.kt:60-64` returns `emptyList()` for both blank
    input and `Json.decodeFromString` failure; `PairedSyncDevice.kt:71-76`
    calls `parse(rawJson)` before `upsert`, so a valid new device written after
    corruption discards the raw unreadable list; `SyncSettingsScreen.kt:99-101`
    remembers only the parsed list, with no parse status; `SyncSettingsScreen.kt:283-290`
    renders `settings__sync__no_paired_devices` when the parsed list is empty;
    `SyncPairingUiModelTest.kt:83-84` explicitly pins corrupt JSON to
    `emptyList()`; a string/code scan found no Sync-specific corrupt-state,
    reset, or repair copy today. Android's DataStore docs treat on-disk
    corruption as a rare but explicit state and provide corruption-handler APIs
    for graceful recovery rather than silent equivalence with defaults:
    https://developer.android.com/topic/libraries/architecture/datastore.
  - Touches: `PairedSyncDevice.kt` parse API, `SyncSettingsScreen.kt`,
    `strings.xml`, `SyncPairingUiModelTest.kt`, optional
    `docs/THREAT_MODEL.md` recovery note, and any shared pairing UI helper from
    R19-1/R22-1/R23-1.
  - Acceptance: paired-device parsing exposes a typed result such as
    `Empty`, `Valid(devices)`, and `Corrupt`; Settings -> Sync renders a
    warning/error row for `Corrupt` instead of "No paired devices yet"; the row
    offers reset/re-pair guidance and an explicit destructive reset action;
    `upsert` does not silently discard corrupt raw JSON unless the user has
    confirmed reset or the implementation documents an atomic repair path; bug
    report/diagnostic copy does not include raw public keys by default; tests
    cover blank empty, valid list, malformed JSON, schema-invalid device rows,
    reset behavior, and upsert-after-corruption behavior.
  - Verify: `:app:testDebugUnitTest --tests "*SyncPairingUiModelTest"` plus a
    manual Settings -> Sync state injection smoke that shows the corrupt-state
    row and confirms reset returns to the normal empty state.
  - Complexity: M

### Researcher Queue (Cycle 23 - 2026-06-06)

- [x] 🔬 `sync-key-lifecycle-recheck-2026-06-06` - traced the pairing payload
  generator, sealed-box open/seal APIs, Sync preferences, the existing
  Tink/AndroidKeystore string wrapper, backup/data-extraction rules, and
  official Android key-storage/backup guidance. This cycle does not duplicate
  R22-1's trust confirmation UX; it adds the missing local identity-key
  lifecycle needed before generated public keys can become usable future sync
  recipients.

#### Sync key lifecycle

- [ ] 🤖 P0 — Persist and backup-scope the Sync long-term X25519 identity before transport activation (R23-1)
  - Why: `generatePairingPayload()` currently emits a public key derived from a
    newly generated `KeyPair`, but the private half is not persisted by the UI
    or Sync prefs. A remote device that later encrypts CRDT deltas to that
    advertised public key would produce envelopes this device cannot open after
    the function returns, an app restart happens, or the process is killed.
    Fixing this before transport code lands avoids a subtle "pairing succeeds,
    sync can never decrypt" failure and prevents a rushed future implementation
    from storing sync private-key material in plaintext/backed-up JetPref state.
  - Evidence: `SyncSettingsScreen.kt:218-234` resolves/stores only cluster id
    and device id, then calls `PairingPayloadGenerator.generate(...)` without a
    caller-owned keypair; `PairingPayloadGenerator.kt:27-43` defaults
    `keyPair` to `SealedBoxCrypto.generateKeyPair()` and serializes only
    `keyPair.public` into `pubkeyHex`; `SealedBoxCrypto.kt:86-97` says the
    caller owns the private half for long-lived recipient keys; `SealedBoxCrypto.kt:156-170`
    requires a `recipientKeyPair` to decrypt envelopes; `SyncPrefs.kt:78-93`
    has only `clusterId`, `deviceId`, `pairedDevicesJson`, and
    `manualExportTargetUri`, with no private/public identity key field; a
    production grep for `generateKeyPair`, `KeyPair`, `PrivateKey`, and
    `pubkeyHex` finds no main-code storage owner beyond `PairingPayloadGenerator`
    and `SealedBoxCrypto`; `FlorisUserDictionaryEncryption.kt:65-133` plus
    `TinkStringPreferenceCrypto.kt` already provide the project's local pattern
    for AndroidKeystore/Tink-wrapped app secrets; `AndroidManifest.xml:60-64`
    enables backup with `@xml/backup_rules` and `@xml/data_extraction_rules`,
    and those rules include `jetpref_datastore`, so any future plaintext sync
    secret added to normal prefs would be backup-scoped. Android's Keystore docs
    recommend keeping cryptographic key material non-exportable/device-bound:
    https://developer.android.com/privacy-and-security/keystore. Android Auto
    Backup docs call out include/exclude rules and list device-specific
    generated identifiers as typical backup exclusions:
    https://developer.android.com/identity/data/autobackup.
  - Touches: add a small `SyncIdentityKeyStore`/codec under `ime/sync` or
    `ime/security`, `SyncSettingsScreen.generatePairingPayload()`,
    `PairingPayloadGenerator` API shape, `SyncPrefs` only for non-secret key
    metadata if needed, `TinkStringPreferenceCrypto` reuse or an equivalent
    AndroidKeystore-backed storage helper, backup/data-extraction XML, threat
    model, and focused sync/security tests.
  - Acceptance: the first generated pairing payload creates or loads one stable
    local X25519 identity; repeated QR generations and process restarts produce
    the same public key until the user explicitly resets Sync identity; the
    private key is wrapped with a sync-specific AndroidKeystore/Tink alias (or a
    documented AndroidKeystore-backed X25519 implementation if the min/target
    API path supports it) and is never stored in plaintext JetPref; backup rules
    exclude the sync secret file while allowing non-secret channel metadata to
    remain portable; missing/tampered key material fails closed with a reset and
    re-pair path; `SealedBoxCrypto.open(...)` integration tests prove envelopes
    sealed to the QR public key decrypt after reload.
  - Verify: new focused JVM tests for key-store round-trip/tamper/fingerprint
    stability, existing `:app:testDebugUnitTest --tests "*SyncPairingUiModelTest"
    --tests "*SealedBoxCryptoTest"`, backup-rule guard updates, and a manual
    Settings -> Sync generate -> restart -> generate fingerprint-stability
    smoke on emulator/device.
  - Complexity: L

### Researcher Queue (Cycle 22 - 2026-06-06)

- [x] 🔬 `sync-pairing-trust-confirmation-recheck-2026-06-06` - rechecked
  Settings -> Sync receive/generate pairing flows, paired-device persistence,
  the payload/key model, focused sync tests, local threat-model notes, and
  official Syncthing/libsodium pairing-identity patterns. This cycle does not
  duplicate R19-1's QR accessibility fallback; it adds the trust-confirmation
  step needed before scanned or pasted public-key payloads can become future
  sync recipients.

#### Sync pairing trust and key identity

- [ ] 🤖 P1 — Confirm scanned/pasted Sync pairing payloads before saving remote devices (R22-1)
  - Why: The receive path treats a syntactically valid pairing payload as a
    completed trust decision. That is too early for an E2EE sync control plane:
    display name, channel id, device id, and public key are all supplied by the
    scanned/pasted payload, and the future sealed-box transport will encrypt
    dictionary deltas to that saved public key. This is not a secret-exposure
    issue today because transport is still local/neutral; it is a pre-transport
    UX/security guardrail so users explicitly bind "this is my other device" to
    a stable key fingerprint before the app persists it.
  - Evidence: `SyncSettingsScreen.kt:120-132` parses raw input, constructs
    `PairedSyncDevice.fromPayload(...)`, writes `prefs.sync.pairedDevicesJson`,
    and switches `prefs.sync.channelId` immediately, with only invalid-payload
    rejection; `PairingPayload.kt:52-59` defines the payload-controlled
    `pubkeyHex`, `displayName`, and `syncChannelId`; `PairedSyncDevice.kt:41-47`
    copies those values straight into persisted paired-device state; the paired
    list UI at `SyncSettingsScreen.kt:408-432` displays only name + channel, not
    a fingerprint or replacement warning; `SyncPairingUiModelTest.kt:55-84`
    covers upsert/replacement/corrupt JSON but not confirmation/cancel/no-op
    behavior; `docs/THREAT_MODEL.md:179-188` says pairing payloads carry raw
    X25519 public keys and private keys stay local. Libsodium's sealed-box docs
    frame sealed boxes as encryption to a recipient public key without sender
    identity verification, so the public key accepted during pairing is the
    load-bearing identity: https://doc.libsodium.org/public-key_cryptography/sealed_boxes.
    Syncthing's device-ID docs similarly treat device identity as a property of
    the public key and discuss full-ID confirmation for shorter UX entry:
    https://docs.syncthing.net/v1.23.1/dev/device-ids.html.
  - Touches: `SyncSettingsScreen.kt`, `PairedSyncDevice.kt` or a small sync UI
    helper for short fingerprints, `strings.xml`, focused sync pairing tests,
    and `docs/THREAT_MODEL.md` pairing UX notes.
  - Acceptance: scanned and pasted payloads open a confirmation dialog/sheet
    showing display name, channel, device id, and a grouped short fingerprint
    derived from `pubkeyHex`; cancelling leaves `pairedDevicesJson` and
    `channelId` unchanged; confirming persists the device and clearly labels a
    same-device-id replacement; duplicate device replacement shows old vs new
    fingerprint/channel before overwrite; invalid payloads keep the current
    rejection path; paired-device rows expose the fingerprint after saving.
  - Verify: `:app:testDebugUnitTest --tests "*SyncPairingUiModelTest"` plus a
    manual Settings -> Sync scan/paste -> cancel -> confirm -> duplicate-replace
    smoke on emulator/device.
  - Complexity: M

### Researcher Queue (Cycle 21 - 2026-06-06)

- [x] 🔬 `first-run-import-recovery-recheck-2026-06-06` - resumed from the
  Continuation State, rechecked the first-run setup import step, Settings ->
  User dictionary import launcher, empty states, preview/summary dialogs, and
  Android content-picker behavior. This cycle does not duplicate R18-2's broader
  migration recovery assistant or R20-1's docs cleanup; it adds the smallest
  implementation-ready UX reliability slice needed so a failed or cancelled
  first-run import does not disappear.

#### First-run migration recovery

- [ ] 🤖 P1 — Keep first-run dictionary import recoverable after picker cancel or import failure (R21-1)
  - Why: The setup flow treats tapping "Choose export file" as completing the
    import hint before the Android picker returns. If the user cancels the
    picker, selects the wrong file, or hits an unreadable provider, the setup
    step is already suppressed and the dictionary screen falls back to an empty
    "No words saved" state whose visible action is "Add", with import hidden in
    the overflow menu. For post-retirement SwiftKey users, a recoverable local
    import path is part of the activation funnel, not a one-shot preference.
  - Evidence: `SetupScreen.kt:267-273` sets
    `prefs.internal.firstRunImportHintSeen` before navigating to
    `Routes.Settings.UserDictionary(... action = IMPORT)`; `UserDictionaryScreen.kt:523-529`
    uses `ActivityResultContracts.GetContent()` and silently returns when
    `uri == null`; `UserDictionaryScreen.kt:545-553` consumes the import action
    once and launches `*/*`; `UserDictionaryScreen.kt:811-818` renders the global
    empty dictionary state with an add-word action only; setup copy at
    `strings.xml:1163-1167` correctly lists supported local exports but has no
    cancel/retry state. Android's `GetContent` contract is a user file-pick
    flow returning a picked content URI for `ContentResolver.openInputStream`,
    so a null/no-result path is a normal UX branch, not an exceptional import
    result: https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.GetContent.
  - Touches: `SetupScreen.kt`, `UserDictionaryScreen.kt`,
    `UserDictionaryEntryPolicy.kt` if a small state helper is added,
    `strings.xml`, setup/dictionary tests, and optional migration docs so the
    same post-cutoff wording from R20-1 is reused.
  - Acceptance: tapping "Choose export file" does not permanently mark the
    setup import hint done until the user explicitly skips, successfully imports,
    or dismisses a clear "continue without importing" choice; cancelling the
    picker leaves a visible retry/continue path; dictionary empty states expose
    an import CTA when entry actions are enabled; import failure keeps the retry
    path visible without duplicating rows; focused tests pin setup-step
    transitions and import-action consumption.
  - Verify: `:app:testDebugUnitTest --tests "*SetupStepPolicyTest" --tests
    "*UserDictionaryEntryPolicyTest"` plus a manual setup -> choose file ->
    cancel -> retry smoke on emulator/device.
  - Complexity: M

### Researcher Queue (Cycle 20 - 2026-06-06)

- [x] 🔬 `post-retirement-migration-doc-drift-2026-06-06` - synced `master`
  after the Cycle 18 roadmap update, rechecked the README, migration guide,
  hardware-keyboard shipped notes, and Microsoft's current SwiftKey account
  support copy. This cycle avoids duplicating R18-2's in-app migration assistant
  and adds one documentation/source-of-truth row for stale post-cutoff guidance
  that can mislead users who arrive after 2026-05-31.

#### Migration source-of-truth

- [ ] 🤖 P2 — Refresh the migration guide and import docs for post-retirement reality (R20-1)
  - Why: The README correctly says Microsoft retired standalone SwiftKey
    accounts and shut down `data.swiftkey.com` on 2026-05-31, but
    `docs/MIGRATE_FROM_SWIFTKEY.md` still opens with pre-cutoff instructions
    and a "Path 0" export route framed as available before the deadline. The
    same table also says Windows `.klc` / macOS `.keylayout` hardware-keyboard
    migration is "Next-6.4 - pending", while README release notes record those
    parser/runtime slices as shipped in v1.8.75 and v1.8.76.
  - Evidence: `README.md:11-14` is already post-cutoff; `README.md:54` lists
    Settings-based Keyman `.kmp`, Windows KLC, and macOS hardware-keyboard
    imports; `README.md:379-380` records the shipped v1.8.75/v1.8.76 hardware
    import/runtime work; `docs/MIGRATE_FROM_SWIFTKEY.md:4-5` and
    `docs/MIGRATE_FROM_SWIFTKEY.md:31-36` still foreground the pre-cutoff
    SwiftKey export path; `docs/MIGRATE_FROM_SWIFTKEY.md:146-150` repeats the
    old cutoff wording and stale hardware-keyboard "pending" status. Microsoft
    support now frames the old SwiftKey account retirement date as 31 May 2026
    and points users to Microsoft-account backup/sync instead:
    https://support.microsoft.com/en-us/topic/account-a3c38581-903f-4d22-a388-cc13c7debf0e.
  - Touches: `docs/MIGRATE_FROM_SWIFTKEY.md`, README migration cross-links if
    needed, import/setup strings only if they point to the old export window,
    and a stale-reference grep in repo hygiene or a focused docs test if the
    project wants to pin post-cutoff wording.
  - Acceptance: the guide opens with the post-retirement state; supported paths
    are sorted by what a user can still do today; pre-cutoff SwiftKey JSON is
    described as "use this only if you already saved the file"; hardware-keyboard
    import status matches v1.8.75/v1.8.76; Microsoft-account/OneDrive recovery
    remains documented as an external vendor path SwiftFloris does not automate;
    R18-2 can reuse the same source-of-truth copy for the in-app assistant.
  - Verify: docs-only `git diff --check`; grep for stale "before 2026-05-31"
    lead copy; optional screenshot/readability pass if Settings strings change.
  - Complexity: S

### Researcher Queue (Cycle 19 - 2026-06-06)

- [x] 🔬 `sync-pairing-accessibility-recheck-2026-06-06` - synced `master`
  after the Cycle 18 roadmap update, rechecked `SyncSettingsScreen`,
  `docs/ACCESSIBILITY.md`, sync pairing tests, and current Android Compose
  accessibility guidance. This cycle adds one focused accessibility/trust row
  for the local sync pairing flow without reopening the already-closed CRDT
  vector, persisted-grant, and radio-row fixes.

#### Sync pairing accessibility

- [ ] 🤖 P2 — Add accessible pairing-payload copy/share fallback to the Sync QR flow (R19-1)
  - Why: Sync pairing currently generates a QR payload and shows it as a
    `Canvas`, while the receiving side can paste a payload only when no scanner
    app is found. A TalkBack user, a user with a broken camera path, or a
    two-device setup where scanning is impractical needs an explicit copy/share
    fallback from the generating device. This keeps the transport local and
    user-chosen while making CRDT pairing reachable without visual QR scanning.
  - Evidence: `SyncSettingsScreen.kt:229-233` generates the serialized
    `PairingPayload`; `SyncSettingsScreen.kt:278-279` passes it only to
    `SyncQrPayloadCard`; `SyncSettingsScreen.kt:358-383` renders "Pairing QR
    ready" plus `SyncQrCodeCanvas(matrix)` and summary text, with no visible
    copy/share action and no semantic description on the custom QR `Canvas`;
    `SyncSettingsScreen.kt:207-214` and `SyncSettingsScreen.kt:318-327` expose
    manual paste only after scanner failure on the receiving side;
    `docs/ACCESSIBILITY.md:16-22` already requires semantics/live-region care
    for dynamic Settings surfaces. Android's Compose accessibility docs state
    that custom components should expose semantics when built-in components do
    not carry enough meaning:
    https://developer.android.com/develop/ui/compose/accessibility/semantics.
  - Touches: `SyncSettingsScreen.kt`, `app/src/main/res/values/strings.xml`,
    focused Sync Settings UI/state tests, and `docs/ACCESSIBILITY.md` manual QA.
    Reuse the existing generated `rawPayload`; do not add any network transport
    or background sync service.
  - Acceptance: generated pairing state offers "Copy payload" and preferably
    "Share payload" actions with localized strings; the QR card has a useful
    TalkBack announcement that does not read raw JSON by default; copy/share
    actions state that the payload contains no private key; receiving paste
    remains available directly, not only after scanner failure; docs include a
    TalkBack/manual no-camera pairing check.
  - Verify: focused JVM/source test for generated-payload actions and strings;
    manual Settings -> Sync -> Pair a new device flow with TalkBack and with no
    scanner app; no changes to `:app` permissions.
  - Complexity: S-M

### Researcher Queue (Cycle 18 - 2026-06-05)

- [x] 🔬 `june-feature-plan-reconciliation-2026-06-05` - synced `master`
  after the June research-feature-plan push, reviewed
  `docs/research-feature-plan-2026-06-05.md`, and reconciled that companion
  plan with the live roadmap. This cycle promotes existing n-gram data-safety
  rows R12-1, R13-1, and R14-1 to P0, promotes MCP identity row R17-1 to P1,
  sharpens the imported-sticker SAF acceptance already present in WS13, and adds
  only the product/trust rows not already represented by existing F21, WS13,
  F10/F11/F12, and API 37 / 16 KB lanes.

#### Glide benchmark and local learning trust

- [ ] 🤖 P1 — Build a public FUTO Swipe glide benchmark harness before model integration (R18-1)
  - Why: SwiftFloris already has benchmark infrastructure and an older swipe
    replay harness, while FUTO Keyboard v0.1.29 introduced a public FUTO Swipe
    dataset and top-1/top-4 evaluation framing. The existing F21 model row should
    not wait for model training to define how glide quality will be measured.
  - Evidence: `README.md` records the active `:benchmark` module and the
    v1.8.47 swipe-trace replay harness; `docs/research-feature-plan-2026-06-05.md`
    identifies FUTO Swipe as the current external benchmark signal; F21 already
    requires top-1/top-4 reporting before any Apache-2.0-clean glide model merge.
    Source: https://github.com/futo-org/android-keyboard/releases/tag/0.1.29
    and https://huggingface.co/datasets/futo-org/swipe.futo.org.
  - Touches: benchmark harness code or a CLI/Gradle task, a pinned public dataset
    subset fixture, `docs/benchmark-results/`, and glide/model planning docs.
    Keep the harness non-shipping and keep any model training outside `:app`.
  - Acceptance: the harness reports top-1 error, top-4 error when alternatives
    are evaluated, runtime per swipe on at least one reference and one low-end
    device class, language/layout scope, exact dataset filtering/exclusion rules,
    and clearly labels benchmark-only results separately from shipping behavior.
  - Verify: focused harness tests plus the relevant benchmark Gradle/CLI task;
    publish only evidence files that identify app version, dataset slice, device
    or emulator, and metric definitions.
  - Complexity: M

#### Post-retirement migration and trust UX

- [ ] 🤖 P2 — Add a local-only migration recovery assistant for post-retirement import paths (R18-2)
  - Why: The SwiftKey account export window has passed. SwiftFloris already
    documents local import routes, but users who arrive after the date still need
    an in-app path that explains what files they can use and what deleted cloud
    data cannot be recovered.
  - Evidence: `README.md` and `docs/MIGRATE_FROM_SWIFTKEY.md` document
    SwiftKey JSON, Gboard XML/ZIP, FlorisBoard CSV / `.flbackup` / `.fldic`, and
    SwiftFloris encrypted imports; `docs/research-feature-plan-2026-06-05.md`
    recommends shifting copy from deadline preparation to post-retirement local
    recovery. Source:
    https://support.microsoft.com/en-us/topic/account-a3c38581-903f-4d22-a388-cc13c7debf0e.
  - Touches: first-run/import Settings surfaces, migration strings, migration
    docs, and focused import-route navigation tests. Do not add network,
    account, or Microsoft cloud sign-in flows.
  - Acceptance: the assistant lists supported local file paths, includes a
    small-word-list clipboard/manual fallback if implemented, routes each choice
    to the existing importer, and states plainly that deleted SwiftKey cloud
    account data cannot be recovered by the keyboard.
  - Verify: focused Settings/import route tests plus manual setup and Settings
    navigation smoke on a device or emulator.
  - Complexity: M

- [ ] 🤖 P2 — Add an addon developer trust kit with signed fixture lanes (R18-3)
  - Why: The addon architecture is now a major differentiator, but reviewers and
    future addon authors need compact fixtures that exercise trust enrollment,
    catalog validation, MCP discovery, and failure modes before more runtime
    features move outside `:app`.
  - Evidence: `docs/addons/apk-validation.md` defines validation requirements;
    `docs/addons/dictionary-pack-spec.md` documents dictionary packs; R17-1
    hardens MCP tool identity before the MCP surface grows; the June companion
    plan calls for minimal dictionary-pack and MCP daemon examples plus negative
    fixtures.
  - Touches: `docs/addons/`, sample/fixture APK or manifest assets, addon
    verification scripts, MCP daemon fixture metadata, and trust/certificate
    regression tests. Keep samples Apache-2.0-compatible and base-app
    permission-clean.
  - Acceptance: maintainers can run one documented verification command over a
    sample addon pack; examples cover a valid dictionary pack and minimal MCP
    daemon; negative fixtures cover changed certificate, missing/malformed
    catalog, duplicate tool names, overlarge payloads, and network-permission
    rejection.
  - Verify: addon verification script, focused addon/MCP fixture tests, and
    manual Settings -> Addons rescan smoke where device access is available.
  - Complexity: M

- [ ] 🤖 P3 — Add a local privacy evidence dashboard in Settings (R18-4)
  - Why: SwiftFloris has strong no-network, local-learning, addon-isolation, and
    release-verification evidence, but most of it lives in docs and CI logs. A
    compact local screen can make the trust posture visible without telemetry or
    outbound checks.
  - Evidence: `docs/PRIVACY_AND_AI.md`, `docs/THREAT_MODEL.md`,
    `docs/LOCAL_VERIFICATION.md`, and `docs/addons/apk-validation.md` already
    describe the base-app and addon boundaries; the June companion plan
    recommends surfacing those facts in Settings.
  - Touches: Settings route/catalog, strings, privacy docs, addon permission
    summary helpers if needed, and focused search/accessibility tests.
  - Acceptance: the screen shows base-app network permissions as absent with the
    relevant build-gate name, local learning controls and reset/export routes,
    addon permissions separately from base-app permissions, backup/data
    extraction policy summary, and release/source verification links or local
    references. It must not fetch remote status or add telemetry.
  - Verify: focused Settings search/route/accessibility tests plus manual
    TalkBack and high-contrast smoke.
  - Complexity: M

#### Release-readiness checklist

- [ ] 🤖 P3 — Add recurring 16 KB page-size dependency/addon review to local verification (R18-5)
  - Why: Native dependencies and addon APKs can regress 16 KB page-size support
    even when the base IME remains mostly Kotlin/Java. The repo already has
    addon validation guidance, but the standard local verification checklist
    should make dependency and addon review recurring.
  - Evidence: `docs/addons/apk-validation.md` documents `zipalign -c -P 16 -v 4`
    for addon APKs; `docs/SQLCIPHER_PROVIDER_MIGRATION.md` documents native
    provider page-size migration triggers; the June companion plan calls for a
    local verification checklist item. Source:
    https://developer.android.com/guide/practices/page-sizes.
  - Touches: `docs/LOCAL_VERIFICATION.md`, `docs/addons/apk-validation.md` only
    if needed, and release-readiness docs.
  - Acceptance: local verification tells maintainers when to re-run 16 KB
    page-size checks for native dependency bumps, addon APKs, SQLCipher provider
    swaps, and release-candidate packaging; it preserves the existing
    no-network and data-extraction gates.
  - Verify: docs-only `git diff --check` plus repo-hygiene script.
  - Complexity: XS

### Researcher Queue (Cycle 17 - 2026-06-04)

- [x] 🔬 `mcp-tool-name-scope-recheck-2026-06-04` - synced `master` after the
  upstream Cycle 16 docs push, rechecked the deferred MCP tool-name audit against
  live daemon discovery, registry, dispatch router, and tests. This cycle adds
  one focused row for constraining advertised tool names and removing
  cross-daemon first-match ambiguity before tool dispatch.

#### MCP daemon tool identity

- [ ] 🤖 P1 — Scope MCP daemon tool dispatch by daemon and constrain tool names (R17-1)
  - Why: MCP daemon discovery trims tool names and rejects only blank strings.
    The registry then resolves `findTool(toolName)` by returning the first daemon
    that advertises that name, and `McpDispatchRouter` dispatches by that global
    string. Two installed daemons can therefore collide on the same tool name,
    and malformed names can enter Settings summaries, disable keys, and dispatch
    logs without a shared shape contract. MCP calls already carry a resolved
    `DaemonKey` at the client boundary, so the ambiguity should be removed before
    dispatch.
  - Evidence: `McpDaemonDiscoverer.kt:91-109` accepts any nonblank parsed
    `name`; `McpToolDescriptor` only requires nonblank names in
    `McpBridgeContract.kt:84-94`; `McpDaemonRegistry.kt:85-93` scans all active
    daemons and returns the first matching tool; `McpDispatchRouter.kt:53-83`
    accepts only `Request.toolName`, resolves it through `RegistryView.findTool`,
    then calls `client.callTool(daemonKey = resolved.daemon, toolName =
    request.toolName, ...)`; `McpDaemonDiscovererTest.kt:85-100` only covers blank
    name skipping; `McpDaemonRegistryTest.kt:53-70` covers multi-daemon lookup
    with distinct names, not duplicate-name behavior; the deferred audit records
    the collision/shadowing risk in `docs/AUDIT_2026-05-28.md:80-82`.
  - Touches: `McpDaemonDiscoverer.kt`, `McpDaemonRegistry.kt`,
    `McpDispatchRouter.kt`, MCP Settings/disable-key surfaces if they depend on a
    flat name, and focused MCP discoverer/registry/router tests. Preserve the
    no-network, signature-permission, payload-size, consent, and sensitive-field
    gates.
  - Acceptance: discovery rejects tool names outside a documented bounded
    charset/length; duplicate names across daemons cannot be dispatched by
    first-match global lookup; dispatch either carries a `DaemonKey` plus tool
    name or uses a generated stable scoped tool id; Settings summaries and
    per-tool enable/disable keys use the same scoped identity; tests fail for
    malformed names and for two daemons advertising the same name where the old
    first daemon would silently shadow the second.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.mcp.*"`
  - Complexity: M

### Researcher Queue (Cycle 16 - 2026-06-04)

- [x] 🔬 `subtype-switch-by-id-double-read-recheck-2026-06-04` - synced
  `master` after the Cycle 15 docs push, rechecked the deferred subtype
  switch-by-id audit against live subtype manager code, the subtype chooser
  caller, and the closed next/previous subtype fallback audit. This cycle adds
  one focused row for collapsing the switch-by-id path to a single nullable
  subtype lookup before activation.

#### Subtype switch-by-id crash hardening

- [x] ✅ P2 — Collapse subtype switch-by-id to a single nullable lookup (R16-1)
  - Why: `switchToSubtypeById(id)` validates the id against one `subtypes`
    snapshot, then calls `getSubtypeById(id)!!`, which snapshots the list again
    and can throw if the subtype list changes between the two reads. Subtype
    edits, restore flows, or localization updates should turn a stale id into a
    no-op, not a crash from a forced null assertion.
  - Evidence: `SubtypeManager.kt:276-278` snapshots `subtypes` in
    `getSubtypeById(id)`; `SubtypeManager.kt:402-404` performs the separate
    existence check and forced `!!` lookup; `SelectSubtypePanel.kt:83` reaches
    this path from the subtype chooser; `docs/AUDIT_2026-05-28.md:61-63`
    records the TOCTOU/NPE finding; `docs/AUDIT_2026-06-02.md:37` closes only
    the next/previous fallback path, leaving switch-by-id open. Kotlin
    `StateFlow` docs describe thread-safe access, but Kotlin null-safety docs
    still make `!!` an NPE boundary when the second lookup returns null:
    https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/,
    https://kotlinlang.org/docs/null-safety.html.
  - Touches: `SubtypeManager.kt`; add a focused `SubtypeManager` switch-by-id
    regression test or a narrow source-level guard test under
    `app/src/test/kotlin/dev/patrickgold/florisboard/ime/core/`.
  - Acceptance: `switchToSubtypeById(id)` stores one nullable lookup result in a
    local value and returns when absent; valid ids still activate through the
    manual subtype path; stale/mutated ids do not throw; existing
    `switchToNextSubtype` / `switchToPrevSubtype` fallback behavior is
    unchanged.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.core.*Subtype*"`
  - Complexity: XS
  - Shipped: v1.8.247 (2026-06-05) with one nullable `getSubtypeById(id)` lookup before manual activation plus a focused source-level guard test for the stale-id no-op path.

### Researcher Queue (Cycle 15 - 2026-06-04)

- [x] 🔬 `honeycomb-parse-diagnostics-recheck-2026-06-04` - synced
  `master` after the Cycle 14 docs push, rechecked the deferred keyboard-layout
  parse diagnostics audit against live Honeycomb parser and tests. This cycle
  adds one focused row for logging malformed honeycomb layout JSON before the
  fail-safe empty-layout fallback.

#### Honeycomb layout diagnostics

- [ ] 🤖 P2 — Log Honeycomb layout parse failures before fail-safe empty layout (R15-1)
  - Why: `HoneycombLayoutLoader.parse(...)` intentionally returns `emptyList()`
    for malformed layout JSON so a corrupt disk/addon layout does not crash the
    IME, but the catch block drops the exception with no diagnostic. The result
    can be an empty character keyboard with no support signal, while sibling
    parser/degradation paths such as `ZipfFrequencyTable.parse(...)` already log
    before returning a safe empty/default result.
  - Evidence: `HoneycombLayoutLoader.kt:39-43` documents the fail-safe malformed
    input policy; `HoneycombLayoutLoader.kt:59-79` catches `Exception` and
    returns `emptyList()` without `flogWarning`/`flogError`;
    `HoneycombLayoutLoaderTest.kt:152-156` pins the malformed-JSON empty-list
    fallback but not diagnostic emission; `ZipfFrequencyTable.kt:109-117`
    catches parse failures and logs them with `flogError`; the deferred audit
    records the silent empty-keyboard path in `docs/AUDIT_2026-05-28.md:72-74`.
  - Touches: `HoneycombLayoutLoader.kt` and `HoneycombLayoutLoaderTest.kt` or a
    small source-level logging contract test. Preserve the fail-safe return
    value and do not turn malformed layouts into crashes.
  - Acceptance: malformed JSON still returns `emptyList()`, but the catch path
    emits one actionable diagnostic through the project logging stack naming
    `HoneycombLayoutLoader` and the parse failure; valid layouts and modifier
    filtering are unchanged; tests fail if the catch block silently returns
    `emptyList()` again.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.text.keyboard.HoneycombLayoutLoaderTest"`
  - Complexity: XS

### Researcher Queue (Cycle 14 - 2026-06-04)

- [x] 🔬 `personal-ngram-control-token-recheck-2026-06-04` - synced
  `master` after the Cycle 13 docs push, rechecked the deferred TSV
  control-character audit against live bigram/trigram normalization, load, and
  flush paths. This cycle adds one focused row for rejecting personal n-gram
  tokens that cannot be represented safely in the current TSV format.

#### Personal n-gram TSV token safety

- [ ] 🤖 P0 — Reject control separators before personal n-gram TSV persistence (R14-1)
  - Why: Both personal n-gram stores normalize learned words by trimming edge
    punctuation, rejecting digits, and requiring at least one letter, but they do
    not reject interior tab, newline, carriage-return, NUL, or other ISO control
    characters. Flush then writes tokens directly into tab-separated,
    newline-delimited files. A pasted or imported token like `foo\tbar` or a
    token containing `\u0000` can corrupt the next load by shifting TSV fields,
    splitting rows, or colliding with the trigram context delimiter.
  - Evidence: `PersonalBigramStore.kt:82-88` and
    `PersonalTrigramStore.kt:86-92` return lowercased trimmed words without any
    control-character rejection; `PersonalBigramStore.kt:101-111` and
    `PersonalTrigramStore.kt:107-119` parse persisted rows with `split('\t')`;
    `PersonalBigramStore.kt:303-315` and `PersonalTrigramStore.kt:305-319`
    write raw token strings separated by tabs and newlines; `PersonalTrigramStore.kt:51`
    reserves `\u0000` as the in-memory context delimiter; the current
    dictionary source tests cover locale-scoped flush/reset contracts but not
    write-time token safety; the deferred audit records the corruption path in
    `docs/AUDIT_2026-05-28.md:66-68`.
  - Touches: `PersonalBigramStore.kt`, `PersonalTrigramStore.kt`, and a focused
    JVM/source contract test for normalization or learn/flush rejection. Keep the
    current simple TSV format; this row is about rejecting unrepresentable tokens,
    not introducing an escaping migration.
  - Acceptance: after trimming and before lowercasing, both stores reject any
    normalized token containing `'\t'`, `'\n'`, `'\r'`, `'\u0000'`, or
    `Char.isISOControl()`; learned control-character tokens do not reach
    in-memory maps or persisted TSV rows; existing apostrophe/hyphen real-word
    cases continue to work; tests fail if either store can persist a token that
    changes TSV field count or trigram context splitting on reload.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.dictionary.PersonalNgramFlushIsolationTest"`
    or a new focused personal n-gram token-safety test class.
  - Complexity: S

### Researcher Queue (Cycle 13 - 2026-06-04)

- [x] 🔬 `personal-ngram-stats-reset-race-recheck-2026-06-04` - synced
  `master` after the Cycle 12 docs push, rechecked the deferred
  `totalEntryCount()` / `resetAndAwait()` audit against live bigram/trigram
  stores and the typing-stats screen. This cycle adds one focused row for
  serializing personal n-gram stats counting with reset cleanup.

#### Personal n-gram stats consistency

- [ ] 🤖 P0 — Serialize personal n-gram stats counting with reset cleanup (R13-1)
  - Why: Settings -> Typing stats refreshes personal bigram/trigram totals from
    `totalEntryCount()`, but each store builds its persisted-locale set outside
    `loadGuard` and then calls `ensureLoaded(localeTag)`. `resetAndAwait()`
    clears in-memory tables and deletes matching files under `loadGuard`, so a
    stats refresh can observe stale filenames or reload a locale around a reset
    and make a just-cleared learning store appear non-empty. The bug is distinct
    from R12-1's file replacement durability: this is the read/reset
    interleaving visible to the Settings stats UI.
  - Evidence: `PersonalBigramStore.kt:224-242` lists
    `personal_bigrams_*.tsv`, merges `tablesByLocale.keys`, and then calls
    `ensureLoaded(localeTag)` for each tag without holding `loadGuard`;
    `PersonalBigramStore.kt:367-376` clears bigram tables and deletes matching
    files under `loadGuard`; `PersonalTrigramStore.kt:229-245` and
    `PersonalTrigramStore.kt:368-375` repeat the same count/reset shape for
    trigrams; `TypingStatsScreen.kt:137-143` displays both counts immediately in
    Settings; `PersonalNgramFlushIsolationTest.kt:64-68` only checks that reset
    is the broad cleanup path, not that stats counting is serialized with it;
    the deferred audit records the race in `docs/AUDIT_2026-05-28.md:58-60`.
  - Touches: `PersonalBigramStore.kt`, `PersonalTrigramStore.kt`, and a focused
    JVM/source contract test such as `PersonalNgramFlushIsolationTest` or a new
    personal n-gram stats/reset test. Keep the R12-1 atomic-replace work and the
    v1.8.234 per-locale flush isolation intact.
  - Acceptance: both stores compute `totalEntryCount()` under the same
    serialization boundary as `resetAndAwait()` or from a reset-safe snapshot;
    file enumeration, `tablesByLocale` key collection, and `ensureLoaded()` do
    not interleave with reset deletion; a reset followed by or racing with a
    stats refresh returns zero rather than resurrecting deleted locales; tests
    fail if either store reintroduces unlocked file enumeration plus
    `ensureLoaded()` in `totalEntryCount()`.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.dictionary.PersonalNgramFlushIsolationTest"`
    or the new focused stats/reset test class.
  - Complexity: S

### Researcher Queue (Cycle 12 - 2026-06-04)

- [x] 🔬 `personal-ngram-atomic-replace-recheck-2026-06-04` - synced
  `master` after the Cycle 11 docs push, rechecked the deferred personal
  n-gram persistence data-loss audit against live bigram/trigram stores and the
  current locale-isolation tests. This cycle adds one focused row for atomic
  replacement of the personal n-gram TSV files.

#### Personal n-gram persistence

- [ ] 🤖 P0 — Replace personal n-gram files atomically without deleting live data first (R12-1)
  - Why: Both personal n-gram stores write a `.tmp` file, attempt
    `renameTo(dst)`, then delete the live destination before a second rename if
    the first rename fails. That fallback breaks the atomicity the temp-write
    pattern is meant to provide: a transient rename failure, destination-exists
    edge case, or second rename failure can remove the last known-good personal
    prediction data for that locale. The current tests pin per-locale flush
    isolation, but not safe replacement semantics.
  - Evidence: `PersonalBigramStore.kt:303-320` writes a temp file and falls back
    to `fileFor(localeTag).delete(); tmp.renameTo(fileFor(localeTag))`;
    `PersonalTrigramStore.kt:305-324` uses the same fallback; both stores have
    minSdk-compatible access to `java.nio.file.Files.move` via Android API 26+;
    `PersonalNgramFlushIsolationTest.kt:28-50` covers bigram/trigram locale
    flush targeting; `PersonalNgramFlushIsolationTest.kt:53-69` inspects the
    flush body for locale scope and broad reset cleanup, but does not reject
    destination deletion or require atomic/replace-existing moves; the deferred
    audit records the data-loss window in `docs/AUDIT_2026-05-28.md:31-34`.
  - Touches: `PersonalBigramStore.kt`, `PersonalTrigramStore.kt`, and
    `PersonalNgramFlushIsolationTest.kt` or a small file-replacement helper test.
    Keep the v1.8.234 per-locale flush isolation and concurrency guards intact.
  - Acceptance: the stores never delete the destination before a successful
    replacement exists; the preferred path uses `Files.move(tmp, dst,
    REPLACE_EXISTING, ATOMIC_MOVE)` where supported; fallback handles
    `AtomicMoveNotSupportedException` without removing the live file until a
    replacement is durable; `.tmp` cleanup is best-effort and never sacrifices
    the destination; tests fail if either store reintroduces
    `dst.delete(); tmp.renameTo(dst)` or equivalent live-file deletion before
    success.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.dictionary.PersonalNgramFlushIsolationTest"`
    plus a small manual/debug file-write smoke if the implementation extracts a
    helper for replace failure injection.
  - Complexity: S

### Researcher Queue (Cycle 11 - 2026-06-04)

- [x] 🔬 `prefs-init-splash-recovery-recheck-2026-06-04` - synced
  `master` after the Cycle 10 docs push, rechecked the older async preference
  initialization audit against live startup code after v1.8.218 closed only the
  synchronous staged-exception path. This cycle adds one focused row for
  preference-store failures that happen inside `FlorisApplication.init()`'s
  launched coroutine.

#### Startup recovery

- [x] 🤖 P2 — Guard async preference-store init failures before the splash wait (R11-1)
  - Why: `FlorisApplication.onCreate()` now stages and surfaces synchronous
    startup exceptions, but `init()` still launches `FlorisPreferenceStore`
    initialization on a plain background scope and flips
    `preferenceStoreLoaded` only after that suspend call succeeds. If
    `initAndroid(...)` throws or the plain parent job fails, the Settings
    activity keeps the splash screen because its keep condition waits on the
    same false flow value, and the existing staged-startup crash redirect has
    already run. This is separate from R2-1: the failure happens after
    `onCreate()` returns and outside the synchronous `try/catch`.
  - Evidence: `FlorisApplication.kt:82` creates
    `CoroutineScope(Dispatchers.Default)` without a `SupervisorJob`;
    `FlorisApplication.kt:161-170` launches `FlorisPreferenceStore.initAndroid`
    and sets `preferenceStoreLoaded.value = true` only after logging the
    successful result; there is no `try/catch`, `finally`, or failure state in
    that coroutine; `FlorisAppActivity.kt:102-115` checks only already-staged
    startup exceptions before installing the splash keep condition
    `!appContext.preferenceStoreLoaded.value`; `FlorisAppActivity.kt:155-167`
    defers `setContent` until that flow becomes true; `FlorisAppActivity.kt:313-319`
    consumes only pre-existing staged exceptions; `StartupCrashRecoveryTest.kt:50-78`
    covers staged exception persistence/redirects but not async preference
    initialization failure; the deferred audit tracks this distinct path in
    `docs/AUDIT_2026-05-28.md:127-129`.
  - Touches: `FlorisApplication.kt` and focused startup tests, likely by
    extracting a small preference-init helper or injectable initializer so
    tests can force an `initAndroid` failure without corrupting real datastore
    files. Keep the existing R2-1 staged-crash behavior intact.
  - Acceptance: the preference-init coroutine uses a supervised/error-guarded
    scope; failures are logged and routed to an existing crash/recovery surface
    or a deliberately degraded startup state; `preferenceStoreLoaded` cannot
    remain false indefinitely after init failure; Settings does not keep the
    splash forever; tests simulate a failing preference initializer and verify
    crash/recovery visibility plus splash unblock behavior.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.app.*Startup*"` plus a manual debug smoke with
    a forced preference-init failure confirming Settings leaves the splash and
    shows the intended recovery path.
  - Complexity: M
  - Shipped: v1.8.240 (2026-06-04) with a supervised application scope,
    testable preference-init helper, guarded async init failure staging,
    `preferenceStoreLoaded` finally-unblock behavior, loaded-time crash-dialog
    redirect before normal Settings content, and focused startup recovery tests.
    Manual forced-failure smoke remains pending.

### Researcher Queue (Cycle 10 - 2026-06-04)

- [x] 🔬 `editor-content-job-lifecycle-recheck-2026-06-04` - synced
  `master` after the Cycle 9 docs push, rechecked the deferred editor
  content-generation lifecycle audit against live `AbstractEditorInstance`
  after v1.8.233 closed only the synchronous batch-edit critical sections.
  This cycle adds one focused row for stale async content jobs that can still
  publish against a finished input session.

#### Editor session lifecycle

- [x] 🤖 P2 — Cancel stale editor content-generation jobs on reset/finishInput (R10-1)
  - Why: `handleStartInputView(...)` and `handleSelectionUpdate(...)` launch
    background content generation using a captured `InputConnection`, then
    publish `activeCursorCapsMode`, `activeContent`, shift-state reevaluation,
    and composing-region updates when the coroutine resumes. `reset()`,
    `handleFinishInputView()`, and `handleFinishInput()` clear editor state, but
    they do not cancel those launched jobs or gate publication by a session
    generation. A delayed job can therefore repopulate editor state and call
    `setComposingRegion` / `finishComposingText` on an old connection after the
    IME has switched fields or finished input. v1.8.233 closed the selected
    synchronous batch critical sections; this is the remaining async lifecycle
    boundary.
  - Evidence: `AbstractEditorInstance.kt:69` owns a `MainScope`;
    `AbstractEditorInstance.kt:141-153` launches from `handleStartInputView`
    with a captured `ic` and publishes state plus `ic.setComposingRegion(...)`;
    `AbstractEditorInstance.kt:165-209` repeats the pattern for selection
    updates; `AbstractEditorInstance.kt:212-227` resets active info, caps,
    content, expected-content queue, and last-commit position without cancelling
    launched jobs or incrementing a request/session generation;
    `AbstractEditorInstance.kt:303-308` maps invalid composing ranges to
    `finishComposingText()`, so stale jobs can still mutate the old editor;
    `EditorInputConnectionBatchTest.kt:27-189` covers synchronous batch helper
    call ordering but no delayed content-generation lifecycle; the deferred
    audit already isolates this as distinct from the v1.8.233 batch fix
    (`docs/AUDIT_2026-05-28.md:51-57`).
  - Touches: `AbstractEditorInstance.kt` plus focused JVM tests under
    `app/src/test/.../ime/editor`; introduce a small pending-content `Job`,
    generation token, or request object as needed. Keep the v1.8.233
    `EditorInputConnectionBatch` helper behavior unchanged.
  - Acceptance: launching a new start-input-view or selection-update content
    pass cancels or supersedes any previous pending pass; `reset()`,
    `handleFinishInputView()`, and `handleFinishInput()` prevent pending jobs
    from publishing state or touching their captured `InputConnection`; resumed
    jobs re-check the active generation and current connection identity before
    setting caps/content, reevaluating shift state, or setting/finishing a
    composing region; tests simulate a delayed first job, then reset or switch
    sessions, and prove only the current session can publish.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.editor.*"` plus a manual smoke switching
    fields while composing text and confirming no stale composing region appears
    in the previous field.
  - Complexity: M
  - Shipped: v1.8.239 (2026-06-04) with a pending content-generation `Job`,
    generation token, current-connection identity check before generation and
    publication, reset/finishInput cancellation, start/selection supersession,
    and focused delayed-job JVM/Robolectric coverage. Manual field-switch smoke
    remains pending.

### Researcher Queue (Cycle 9 - 2026-06-04)

- [x] 🔬 `nlp-request-privacy-snapshot-recheck-2026-06-04` - synced
  `master` at the Cycle 8 pushed tip, rechecked the remaining suggestion
  privacy/state audit against live `NlpManager.suggest`, field-start incognito
  resolution, smart-compose sensitive-field guards, and Android `EditorInfo`
  privacy docs. Existing `SuggestionPrivacyPolicy` tests cover the policy
  decisions, but not the async request boundary. This cycle adds one focused
  privacy row for request-scoped candidate generation inputs.

#### NLP request privacy

- [x] 🤖 P2 — Snapshot suggestion privacy inputs before async candidate generation (R9-1)
  - Why: `NlpManager.suggest` captures the `EditorContent` and `Subtype` for a
    request, then launches background work that re-reads live preference,
    incognito, and editor-info state before calling suggestion providers,
    recording typing traces, and deciding whether smart-compose ghost text is
    allowed. If the user toggles incognito or the IME switches fields before
    that coroutine reaches those gates, candidate generation can use privacy
    facts from a different field/session than the text snapshot it is
    processing. The request-id guard prevents older results from overwriting
    newer candidates, but it does not freeze provider inputs or side effects
    that already ran inside the stale request.
  - Evidence: `NlpManager.kt:211-214` captures `content`, `subtype`, and a
    `requestId` before `scope.launch`; `NlpManager.kt:216-244` reads
    `prefs.emoji.suggestionEnabled`, `prefs.suggestion.blockPossiblyOffensive`,
    `prefs.suggestion.enabled`, and `keyboardManager.activeState.isIncognitoMode`
    inside the coroutine; `NlpManager.kt:277-286` records typing-trace evidence
    using the live incognito flag; `NlpManager.kt:295` and
    `NlpManager.kt:322-324` let ghost text consult `editorInstance.activeInfo`
    after the async boundary; `NlpManager.kt:299-306` orders publication by
    request id but only after providers and trace logging have run;
    `EditorInstance.kt:151-155` resolves `activeState.isIncognitoMode` from
    each field's `IME_FLAG_NO_PERSONALIZED_LEARNING` and incognito preference;
    `KeyboardManager.kt:739-741` mutates the same live state for dynamic
    incognito toggles; `SuggestionPrivacyPolicyTest.kt:24-113` covers the
    policy functions but not request-scoped propagation through `NlpManager`.
    Android `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` docs define the
    host-app privacy request for typing history / personalized language models
    (`https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING`).
  - Touches: `NlpManager.kt`, a small immutable request/context data class if
    helpful, `SuggestionPrivacyPolicy` only if request-snapshot helpers belong
    there, and focused fake-provider tests under `app/src/test/.../ime/nlp`.
  - Acceptance: `suggest(...)` snapshots privacy-relevant inputs before
    launching provider work; emoji/word providers, typing-trace recording, and
    ghost-text gating all consume the same request-scoped `isPrivateSession`,
    offensive-content flag, enabled flags, and active editor sensitivity; no
    content-scoped async path reads `keyboardManager.activeState.isIncognitoMode`
    or `editorInstance.activeInfo` after the launch boundary; existing request
    ordering still prevents stale candidate publication; tests simulate
    incognito/field changes while a fake provider is delayed and prove the
    original request's privacy decision is preserved.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.nlp.*"` plus a manual smoke where dynamic
    incognito is toggled during typing and suggestions/typing-trace output stay
    aligned with the request that produced the candidates.
  - Shipped: v1.8.236 (2026-06-04) with immutable
    `SuggestionRequestPrivacySnapshot` propagation through emoji/word
    providers, trace gating, and ghost-text sensitivity checks. Focused
    source/request contract tests passed; manual dynamic-incognito smoke remains
    pending.
  - Complexity: M

### Researcher Queue (Cycle 8 - 2026-06-04)

- [x] 🔬 `user-dictionary-back-feedback-recheck-2026-06-04` - synced
  `master` at the Cycle 7 pushed tip, rechecked the user-dictionary operation
  gating audit against live Compose back handling, nearby policy tests, and
  AndroidX `BackHandler` docs, and avoided duplicating the already-shipped
  dictionary transfer progress cards. This cycle adds one focused navigation
  feedback row for blocked back gestures during active dictionary work.

#### User-dictionary navigation feedback

- [x] 🤖 P3 — Give blocked user-dictionary back gestures explicit feedback (R8-1)
  - Why: save/delete/import/export operations deliberately block leaving the
    user-dictionary screen so mutable dictionary work is not interrupted. The
    toolbar back button is disabled and progress cards explain that work is in
    flight, but the system back gesture is still intercepted by an enabled
    `BackHandler` and then silently does nothing while an operation is active.
    That makes a protected in-progress state look like a frozen settings
    screen.
  - Evidence: `UserDictionaryScreen.kt:171-179` derives
    `isEntryOperationInProgress`, `isDictionaryTransferInProgress`, and
    `canLeaveDictionaryScreen`; `UserDictionaryScreen.kt:626-650` disables the
    visible navigation button while `canLeaveDictionaryScreen` is false;
    `UserDictionaryScreen.kt:722-727` enables `BackHandler` for current-locale
    or active-operation state but only handles the current-locale escape when
    `canLeaveDictionaryScreen` is true; `UserDictionaryScreen.kt:734-758` and
    `strings.xml:942-945` / `strings.xml:997-1005` already expose progress-card
    copy for import/export/save/delete; `UserDictionaryEntryPolicy.kt:45-65`
    and `UserDictionaryEntryPolicyTest.kt:23-51` pin the block-leaving policy;
    `docs/AUDIT_2026-05-28.md:160-162` records the same swallowed-back gap.
    AndroidX `BackHandler` docs define the `enabled` flag as the control for
    whether the handler is active, so an enabled no-op handler consumes the
    gesture (`https://developer.android.com/reference/kotlin/androidx/activity/compose/BackHandler.composable`).
  - Touches: `UserDictionaryScreen.kt`, `UserDictionaryEntryPolicy.kt` if a
    small operation-to-feedback helper is useful, localized strings for the
    back-blocked message, and focused JVM/Compose tests around the policy or
    screen behavior.
  - Acceptance: system back during save, delete, import, and export keeps the
    user on the dictionary screen but surfaces clear feedback using existing
    in-progress wording or a dedicated toast/snackbar/live announcement; system
    back still closes the selected locale when no operation is active; with no
    selected locale and no active operation, this screen does not consume back;
    the toolbar button disabled state and existing progress cards remain
    unchanged; TalkBack users receive equivalent feedback.
  - Verify: focused unit tests for the extracted feedback decision plus manual
    Settings -> Dictionary smoke for save/delete/import/export in progress,
    hardware/gesture back, toolbar back, and TalkBack announcement behavior.
  - Complexity: S
  - Shipped: v1.8.232 (2026-06-04) with operation-specific blocked-back toasts,
    a pure `UserDictionaryBlockedBackNotice` resolver, and focused policy
    coverage for save/delete/import/export/idle decisions. Manual system-back
    and TalkBack proof remains device-gated.

### Researcher Queue (Cycle 7 - 2026-06-04)

- [x] 🔬 `flag-secure-incognito-toggle-recheck-2026-06-04` - synced
  `master` at the Cycle 6 pushed tip, rechecked the remaining `FLAG_SECURE`
  audit note against live IME startup and smartbar incognito-toggle code, and
  avoided duplicating older password-field `FLAG_SECURE` coverage. This cycle
  adds one focused privacy row for mid-session incognito toggles.

#### Sensitive-window privacy

- [x] 🤖 P2 — Re-apply `FLAG_SECURE` when incognito mode toggles mid-session (R7-1)
  - Why: `FLAG_SECURE` now covers password fields and incognito state when a
    field starts, but the user can toggle incognito from the smartbar while
    staying in the same ordinary text field. In that path, the IME updates
    privacy state and toasts, but does not re-run the secure-window policy until
    the next `onStartInputView`, leaving the keyboard screenshot-/record-able
    during the current private session.
  - Evidence: `FlorisImeService.kt:599` calls
    `applyFlagSecureForCurrentField(editorInfo)` only during input-view start;
    `FlorisImeService.kt:617-636` adds `FLAG_SECURE` for password fields or
    `activeState.isIncognitoMode` and explicitly notes the missing mid-session
    toggle callback; `KeyboardManager.kt:738-755` flips
    `activeState.isIncognitoMode` for `KeyCode.TOGGLE_INCOGNITO_MODE` and shows
    a toast without notifying the service/window; `docs/AUDIT_2026-06-02.md:89-92`
    records the same follow-up. Android `WindowManager.LayoutParams.FLAG_SECURE`
    docs define the flag as preventing the window content from appearing in
    screenshots or on non-secure displays
    (`https://developer.android.com/reference/android/view/WindowManager.LayoutParams`).
  - Touches: `FlorisImeService.kt`, `KeyboardManager.kt` or a small
    `FlagSecurePolicy` helper, focused unit tests around password/incognito/off
    combinations, and `docs/THREAT_MODEL.md` / `docs/PRIVACY_AND_AI.md` if the
    runtime guarantee is documented there.
  - Acceptance: toggling dynamic incognito on in a non-password field applies
    `FLAG_SECURE` immediately; toggling it off clears the flag only when the
    active field is not password/no-personalized-learning protected; password
    fields remain secure across toggle attempts; existing field-start behavior
    is unchanged; manual screenshot or screen-recording evidence confirms the
    current IME window blocks capture after the toggle.
  - Verify: focused JVM tests for the extracted policy/callback plus manual
    IME smoke with a normal field, password field, dynamic incognito on/off, and
    screenshot/screen-recording attempt.
  - Complexity: S-M
  - Shipped: v1.8.231 (2026-06-04) with a lifecycle-cleared
    `KeyboardManager` -> `FlorisImeService` incognito callback, main-thread
    secure-window reapplication, pure `FlagSecurePolicy` coverage, and updated
    privacy/threat-model docs. Device screenshot/screen-recording proof remains
    hardware-gated.

### Researcher Queue (Cycle 6 - 2026-06-04)

- [x] 🔬 `editor-batch-critical-section-recheck-2026-06-04` - synced
  `master` at the Cycle 5 pushed tip, rechecked the editor hot-path audit
  against live `AbstractEditorInstance` code and Android `InputConnection`
  contracts, and avoided duplicating already-closed unbalanced-batch fixes. This
  cycle adds one focused reliability row for batch-edit critical sections.

#### Editor hot-path reliability

- [x] 🤖 P2 — Keep `InputConnection` batch edits free of `runBlocking` and queue-lock work (R6-1)
  - Why: `beginBatchEdit()` / `endBatchEdit()` should bracket the smallest
    synchronous editor mutation set. Several IME hot paths currently open a
    batch edit before doing `runBlocking`, generating expected content, and
    acquiring the `ExpectedContentQueue` lock. That keeps the target editor in a
    nested batch while coroutine/queue work runs on the UI path and also leaves
    `endBatchEdit()` outside `try/finally` in fragile code paths.
  - Evidence: `AbstractEditorInstance.kt:311-325` opens a batch in
    `setSelection`, then enters `runBlocking` to compute `newContent`, push the
    expected-content queue, and call `setSelection` / `setComposingRegion`;
    `AbstractEditorInstance.kt:396-414` and `AbstractEditorInstance.kt:429-445`
    show the same pattern in text commit/finalize paths;
    `ExpectedContentQueue` uses suspending `withLock` helpers at
    `AbstractEditorInstance.kt:679-708`; `docs/AUDIT_2026-05-28.md:54-56`
    records this exact `setSelection` risk. Android `InputConnection` docs
    define `beginBatchEdit`, `endBatchEdit`, `setSelection`, and composing
    region/text mutations as editor-facing operations
    (`https://developer.android.com/reference/android/view/inputmethod/InputConnection`).
  - Touches: `AbstractEditorInstance.kt`, a small fake/stub `InputConnection`
    test harness under `app/src/test/.../ime/editor`, and comments only where a
    hot path must intentionally keep a batch open.
  - Acceptance: `setSelection`, `commitTextInternal`, `finalizeComposingText`,
    and the `commitChar` replacement branch have no suspending/blocking queue
    work between `beginBatchEdit()` and `endBatchEdit()`; batch pairs use
    `try/finally`; expected-content ordering remains correct for
    `onUpdateSelection`; tests assert batch depth returns to zero and that the
    order of `InputConnection` calls is unchanged for representative cursor,
    commit, and composing-finalization cases.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.editor.*"` plus manual sustained-typing
    smoke on a low-end device if available.
  - Complexity: M
  - Shipped: v1.8.233 (2026-06-04) with expected-content generation and queue
    pushes moved before the selection, text-commit, composing-finalize, and
    composing-region replacement batches; `EditorInputConnectionBatch` pairs
    begin/end with `try/finally`, and `EditorInputConnectionBatchTest` pins
    representative call order and batch depth. Low-end sustained-typing smoke
    remains device-gated.

### Researcher Queue (Cycle 5 - 2026-06-04)

- [x] 🔬 `addon-trust-boundary-recheck-2026-06-04` - synced
  `master` at the v1.8.226 release-ledger baseline, checked the older
  trust-boundary audit backlog against live addon code and current Android
  package-visibility/signature-permission docs, and avoided duplicating the
  already-fixed signing-history and Tasker extras-bounds findings. This cycle
  adds one implementation-ready addon trust row.

#### Addon trust boundary

- [x] 🤖 P1 — Require explicit first-run trust for non-co-signed addon APK enrollment (R5-1)
  - Why: SwiftFloris' addon contract says addon packages must be co-signed with
    the IME or explicitly trusted by the user in Settings, but the current
    first-seen registry path auto-pins any package that passes manifest,
    no-network, and descriptor screening. Because Android package visibility
    `<queries>` intentionally lets the IME discover packages matching addon
    intent signatures, discovery should not be treated as user consent.
  - Evidence: `AddonContract.kt:108-110` and `AndroidManifest.xml:15-19`
    promise co-signed addons or explicit Settings opt-in; `AddonEnumerator.kt:118-183`
    accepts any package with addon metadata, no banned network permission,
    required descriptor/version/license fields, and a readable signing
    fingerprint; `AddonRegistry.kt:55-60` auto-pins first-seen signing
    certificates when `pinnedFingerprint == null`; `AddonRegistryTest.kt:48-72`
    and `AddonRegistryStartupTest.kt:46-56` currently assert first-seen
    auto-enrollment from an empty pin set; `AddonsSettingsScreen.kt:104-145`
    only exposes rescan/reset flows, while `AddonsSettingsScreen.kt:252-292`
    can trust a changed certificate only after an existing pin rejected it;
    `docs/AUDIT_2026-05-28.md:84-86` records the same contract mismatch.
    Android's `<permission>` docs define `signature` permissions as granted
    only to apps signed with the same certificate
    (`https://developer.android.com/guide/topics/manifest/permission-element`);
    Android package-visibility docs say apps can use `<queries>` intent
    signatures to discover matching packages, and that package visibility is a
    privacy-sensitive capability
    (`https://developer.android.com/training/package-visibility`,
    `https://developer.android.com/training/package-visibility/declaring`).
  - Touches: `AddonRegistry.kt`, `AddonRegistryStartup.kt`,
    `AddonsSettingsScreen.kt`, addon strings, `docs/addons/dictionary-pack-spec.md`,
    `docs/THREAT_MODEL.md`, `AddonRegistryTest`, `AddonRegistryStartupTest`,
    and focused Settings tests if the pending-trust UI state is extracted.
  - Acceptance: first-seen packages whose signing fingerprint does not match
    `SigningFingerprint.sha256(context)` are shown as pending/rejected until the
    user explicitly trusts that fingerprint; co-signed packages still enroll
    without extra prompts; changed-certificate packages remain rejected until a
    separate explicit trust action; reset-all trust clears both accepted and
    pending decisions; docs describe the exact trust states.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.addon.*"` plus a manual Settings -> Addons
    pass with co-signed, unsigned-untrusted, unsigned-trusted, changed-cert, and
    reset-all cases.
  - Shipped: v1.8.229 (2026-06-04) with focused addon registry/startup/pin-set
    coverage under the broad debug JVM unit-test task. Manual Settings ->
    Addons package-state smoke remains device-gated.
  - Complexity: M

### Researcher Queue (Cycle 4 - 2026-06-04)

- [x] 🔬 `locale-a11y-mime-native-audit-2026-06-04` - synced
  `master`, confirmed the Cycle 3 docs push is now at `dc72e32`, refreshed the
  post-v1.8.225 release-ledger evidence to the rewritten pushed hashes, checked
  current audit carry-forwards for duplicates, and widened into language-tag,
  Compose semantics, MIME-filter, and ByteBuffer platform contracts. Existing
  RA-9 and device-gated work remain valid; R3-1/R3-4 and RA-4 have since
  closed. This cycle adds four small, implementation-ready
  correctness/a11y/contract items and sharpens WS13 with the deferred
  sticker-provider SAF validation.

#### Locale correctness

- [x] 🤖 P1 — Correct Japanese locale capability gates and pin them with tests (R4-1)
  - Why: `FlorisLocale.supportsAutoSpace` disables auto-space for `"jp"`, but
    Android/BCP-47 Japanese locales use language subtag `"ja"`; `"JP"` is only a
    region subtag. Japanese therefore falls through as an auto-space language,
    and the adjacent capitalization table has no regression coverage for the
    same class of language-tag mistakes.
  - Evidence: `FlorisLocale.kt:219-231` hard-codes no-capitalization and
    no-auto-space language lists, including `"jp"` but not `"ja"`;
    `EditorInstance.kt:701` and `KeyboardManager.kt:678,728` consume
    `primaryLocale.supportsAutoSpace`; `LayoutScriptClassifier.kt:139` already
    classifies `"ja"` as Japanese; IANA Language Subtag Registry lists `Subtag:
    ja` / `Description: Japanese` and region `Subtag: JP` / `Description:
    Japan` (`https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry`);
    Android `Locale` docs recommend BCP 47 `forLanguageTag` / `toLanguageTag`
    for conforming locale strings (`https://developer.android.com/reference/java/util/Locale`).
  - Touches: `FlorisLocale.kt`, new `FlorisLocaleTest` or equivalent JVM test,
    `docs/AUTOCORRECT_LIFECYCLE.md` if the locale capability contract is
    documented there.
  - Acceptance: `FlorisLocale.from("ja").supportsAutoSpace == false` and
    `FlorisLocale.from("ja").supportsCapitalization == false`; existing `zh`,
    `ko`, `th`, `bn`, `hi`, and a Latin control locale are pinned; no
    regression to `languageTag()` / `localeTag()` serialization.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.lib.FlorisLocaleTest"` plus the existing
    editor spacing policy tests.
  - Shipped: v1.8.227 (2026-06-04) with `FlorisLocaleTest` coverage.

#### Clipboard media accessibility

- [x] 🤖 P3 — Add TalkBack descriptions for clipboard image/video history tiles (R4-2)
  - Why: Text clips can surface URL/email/phone descriptions, but image and
    video history tiles expose only visual thumbnails. A screen-reader user can
    focus and activate the tile without hearing whether it is an image, video,
    pinned/recent item, or the copied timestamp. The prior audit deferred this
    only to avoid Crowdin churn, not because the gap was invalid.
  - Evidence: `ClipboardInputLayout.kt:282-357` renders image/video `Image`
    thumbnails and the video overlay icon with `contentDescription = null`;
    `docs/AUDIT_2026-05-29.md:163-164` records the missing clipboard
    image/video `contentDescription`; Compose semantics docs say semantic
    properties give accessibility services additional context and that
    `contentDescription` conveys an icon/image's meaning
    (`https://developer.android.com/develop/ui/compose/accessibility/semantics`).
  - Touches: `ClipboardInputLayout.kt`, `strings.xml` / translations,
    `docs/ACCESSIBILITY.md`, optional semantics or screenshot test if the helper
    can be extracted without brittle IME rendering.
  - Acceptance: clipboard image and video tiles expose localized, non-sensitive
    labels such as "Clipboard image" / "Clipboard video" plus pinned/recent and
    copied-time context where available; decorative overlay icons remain hidden;
    sensitive text-description protections remain unchanged.
  - Verify: `./gradlew.bat :app:testDebugUnitTest` plus manual TalkBack pass over
    text/image/video clipboard history, long-press popup, and paste/delete
    actions.
  - Shipped: v1.8.238 (2026-06-04) with localized image/video media labels,
    Pinned/Recent/Other group context, copied-time context, decorative thumbnail
    overlay semantics preserved, focused JUnit/resource/source coverage, and
    manual TalkBack smoke still pending.

#### MIME helper contract

- [x] 🤖 P3 — Pin `MimeTypeFilter` aggregate semantics and remove constructor stdout (R4-3)
  - Why: The shared MIME helper is used by extension-file import and
    copy-to-clipboard image routing, but its aggregate helpers still carry
    "document and test" TODOs and the constructor prints compiled regex filters
    to stdout. It also deliberately permits wildcard fragments like
    `application/font-*`, which differs from AndroidX `MimeTypeFilter`; that
    divergence should be explicit and tested before more provider/import code
    depends on it.
  - Evidence: `MimeTypeFilter.kt:31-127` documents wildcard-at-any-position
    behavior, has `println(filters)` in the constructor, and leaves
    `matchesAll`, `matchesAny`, and `matchesOne` undocumented/test TODOs;
    `MimeTypeFilterTest.kt:23-124` covers only single-MIME `matches`; AndroidX
    `MimeTypeFilter` allows wildcards only as the whole type/subtype and notes
    Android framework MIME matching is case-sensitive
    (`https://developer.android.com/reference/androidx/core/content/MimeTypeFilter`);
    Android `ClipDescription.compareMimeTypes` documents the platform pattern
    comparison used elsewhere in the IME
    (`https://developer.android.com/reference/android/content/ClipDescription#compareMimeTypes(java.lang.String,java.lang.String)`).
  - Touches: `lib/kotlin/src/main/kotlin/org/florisboard/lib/kotlin/MimeTypeFilter.kt`,
    `lib/kotlin/src/test/kotlin/org/florisboard/lib/kotlin/MimeTypeFilterTest.kt`,
    call-site comments if any behavior is intentionally broader than AndroidX.
  - Acceptance: no constructor stdout; aggregate helpers have KDoc and tests for
    null/empty lists, exactly-one vs many matches, case-sensitive behavior, and
    the intentional fragment-wildcard cases used by font/image import filters.
  - Verify: `./gradlew.bat :lib:kotlin:testDebugUnitTest --tests
    "org.florisboard.lib.kotlin.MimeTypeFilterTest"`.
  - Shipped: v1.8.241 (2026-06-04) with constructor stdout removed,
    aggregate KDoc, null/empty/all/any/exactly-one/case-sensitive focused
    coverage, and explicit legacy fragment-wildcard coverage for font/import
    MIME strings. Verified with the pure-JVM `:lib:kotlin:test --tests
    "org.florisboard.lib.kotlin.MimeTypeFilterTest"` task because
    `lib/kotlin` is not an Android unit-test module.

#### Native bridge hardening

- [x] 🤖 P3 — Make `NativeStr.toJavaString()` honor ByteBuffer position/limit/arrayOffset (R4-4)
  - Why: The native string bridge currently returns the whole backing array
    whenever `hasArray()` is true, ignoring `position()`, `limit()`, and
    `arrayOffset()`. The current caller surface is small, but CJK/native addon
    work will make this bridge harder to reason about if sliced heap buffers
    decode stale prefix/suffix bytes.
  - Evidence: `Native.kt:39-46` uses `array()` directly on heap-backed buffers
    but copies only `remaining()` bytes for direct buffers; `docs/AUDIT_2026-05-29.md:165-166`
    records the latent offset/position bug; Android `ByteBuffer` docs state
    `hasArray()` permits `array()`/`arrayOffset()`, and buffer content-sensitive
    operations depend on remaining elements from `position()` to `limit() - 1`
    (`https://developer.android.com/reference/java/nio/ByteBuffer`).
  - Touches: `Native.kt`, new focused JVM test for heap, sliced heap, direct,
    read-only/direct-equivalent, and non-zero-position buffers.
  - Acceptance: `toJavaString()` decodes exactly the remaining bytes without
    mutating the caller-visible position, or documents and tests the mutation if
    preserving position is not feasible; direct and heap-backed buffers behave
    the same.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.lib.NativeStrTest"`.
  - Shipped: v1.8.242 (2026-06-04) with duplicate-view decoding that respects
    `position()`, `limit()`, and sliced heap `arrayOffset()` without consuming
    the caller-visible buffer. Focused coverage pins heap, sliced heap, direct,
    read-only, non-zero-position, and `toNativeStr` round-trip behavior.

### Researcher Queue (Cycle 3 - 2026-06-04)

- [x] 🔬 `post-1.8.225-sync-and-futo-swipe-refresh-2026-06-04` - synced
  `master`, reconciled the post-v1.8.225 local fixes against the current
  roadmap, later confirmed the pushed docs state at `dc72e32`, and checked
  current competitor/standards sources. Existing RA-9, device-gated visual
  work, and maintainer-gated release items remain valid; this cycle adds only
  net-new release-ledger, clipboard-search, and sync-crypto-contract work, plus
  sharper evidence on F21 from the FUTO Keyboard v0.1.29 swipe release.

#### Release/source-of-truth hygiene

- [x] 🤖 P0 — Reconcile post-v1.8.225 local fixes into a versioned release ledger (R3-1)
  - Why: The branch is `v1.8.223-6-gdc72e32`, `HEAD` is untagged, and three
    local code-fix commits after the v1.8.225 docs marker change privacy,
    crypto, i18n, and theme-engine behavior without a matching new version,
    fastlane changelog, or top-of-README release entry. That breaks the repo's
    own "one shipped release = version + changelog + fastlane metadata + tag"
    contract and makes it hard for Obtainium/F-Droid/reproducible-build readers
    to know which APK contains the fixes.
  - Evidence: `git describe --tags --dirty --always` -> `v1.8.223-6-gdc72e32`;
    `git tag --points-at HEAD` is empty; `CHANGELOG.md:5-78` documents
    v1.8.225 but not commits `4fda240`, `86c9885`, or `76a74c2`;
    `gradle.properties:18-19` still reports versionCode 2025 / versionName
    1.8.225 after those commits.
  - Touches: `CHANGELOG.md`, `README.md`, `PROJECT_CONTEXT.md`,
    `gradle.properties`, `fastlane/metadata/android/en-US/changelogs/2026.txt`,
    `COMPLETED.md` if any roadmap/audit rows are closed, and the release tag.
  - Acceptance: a new release marker (or explicitly documented untagged-dev
    marker) covers the n-gram/thread-safety/crypto/privacy, shared-secret
    scrubbing/Arabic-shaping, and Snygg selector/contentScale fixes; fastlane
    metadata exists for the new versionCode; `git describe` resolves to the new
    tag once released.
  - Verify: `git describe --tags --dirty --always`; `bash
    scripts/check-fastlane-metadata.sh`; full release gate after the build
    machine performs the version bump.
  - Shipped: v1.8.226 (2026-06-04) with versionCode 2026, fastlane changelog
    `2026.txt`, release tag `v1.8.226`, and a local verification caveat in
    `CHANGELOG.md#v1.8.226`.
  - Complexity: S-M

#### Clipboard UX

- [x] 🤖 P1 — Wire clipboard-history text search into the in-keyboard clipboard palette (R3-2)
  - Why: SwiftFloris already has a tested pure `ClipboardHistoryFilter` and a
    `historySearchEnabled` preference, but the live `ClipboardInputLayout`
    exposes only type filters. FUTO Keyboard v0.1.29 added clipboard-history
    search in its latest release, reinforcing this as table-stakes for long
    local clipboard histories.
  - Evidence: `ClipboardHistoryFilter.kt:22-68` defines the query contract and
    says the search wire-up is missing; `ClipboardPrefs.kt:152-162` defines the
    default-on UI-density toggle; `ClipboardInputLayout.kt:151-163` filters only
    by active `ItemType`; FUTO Keyboard v0.1.29 release notes list clipboard
    history search: https://github.com/futo-org/android-keyboard/releases/tag/0.1.29.
  - Touches: `ClipboardInputLayout.kt`, clipboard strings, `ClipboardScreen.kt`
    if the existing toggle needs a visible settings row, and focused Compose or
    policy tests around query + type-filter composition.
  - Acceptance: when history search is enabled, the clipboard palette offers a
    compact search affordance, filters text clips through
    `ClipboardHistoryFilter.filterByQuery`, composes correctly with image/video
    type filters, shows a clear/no-results state, preserves sensitive-field and
    lock-screen redaction behavior, and resets scroll to the first match on
    query/type changes.
  - Verify: `:app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.clipboard.*"`; manual keyboard clipboard
    palette smoke with text, sensitive text, image, video, no-results, and
    device-locked states.
  - Shipped: v1.8.228 (2026-06-04) with `ClipboardHistoryFilterTest` coverage
    for query plus type-filter composition and a local verification caveat for
    manual TalkBack/device smoke.
  - Complexity: M

#### Sync crypto contract

- [x] 🤖 P1 — Freeze the sealed-box envelope/KDF contract with vectors before sync transport ships (R3-3)
  - Why: The local sealed-box scaffold now uses X25519 + AES-GCM with an
    RFC-5869-style HMAC KDF and scrubs the derived shared secret, but tests only
    cover generated-key round-trips. Before CRDT sync starts persisting or
    exchanging envelopes, the byte format and derivation constants need
    deterministic vectors so a future KDF tweak does not silently strand paired
    devices.
  - Evidence: `SealedBoxCrypto.kt:96-143` emits `ephemeralPub || nonce ||
    ciphertext+tag` and opens the same shape; `SealedBoxCrypto.kt:166-170`
    derives key material from X25519 output; `SealedBoxCryptoTest.kt:24-68`
    lacks deterministic vectors or schema/version assertions; libsodium's
    sealed-box docs define the same ephemeral-public-key-prefixed shape and
    erase the ephemeral secret after encryption
    (https://doc.libsodium.org/public-key_cryptography/sealed_boxes); RFC 5869
    defines HKDF's extract-then-expand construction and publishes SHA-256 test
    vectors (https://datatracker.ietf.org/doc/html/rfc5869).
  - Touches: `SealedBoxCrypto.kt`, `SealedBoxCryptoTest.kt`,
    `docs/THREAT_MODEL.md` or `docs/SECURITY.md` for the sync-envelope contract.
  - Acceptance: tests pin at least one deterministic vector for nonce/key or a
    fixed test-key envelope; docs state the envelope schema/version and
    compatibility policy; raw X25519 output and temporary nonce/KDF buffers are
    scrubbed where practical; malformed-envelope diagnostics remain non-leaky.
  - Verify: `:app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.sync.SealedBoxCryptoTest"`; manual review
    that no network permission or native dependency was introduced.
  - Shipped: v1.8.230 (2026-06-04) with explicit v1 envelope constants,
    fixed-key deterministic vector coverage, malformed-envelope null-return
    coverage, temporary-buffer scrubbing, and threat-model compatibility docs.
  - Complexity: M

#### Regression coverage

- [x] 🤖 P2 — Backfill focused regression tests for the untested post-v1.8.225 hotfix surfaces (R3-4)
  - Why: The newest local fixes cover fragile crash/privacy/i18n paths, but the
    changed behavior is not fully pinned by tests. Without focused tests, the
    same regressions can reappear while the release ledger says the fixes are
    shipped.
  - Evidence: `ArabicShaperTest.kt:24-58` lacks a combining-mark case even
    though `86c9885` changed mark-skipping join context; `SnyggRuleTest.kt`
    covers valid/invalid selectors but not unknown-selector fallback after
    `76a74c2`; `rg "contentScale|SnyggContentScaleValue" lib/snygg/src/test`
    finds no serializer-id test; `SwiftKeyTypingTraceRecorder.kt` gained
    private-session gates in `4fda240` without a focused recorder test.
  - Touches: `ArabicShaperTest.kt`, `SnyggRuleTest.kt` / Snygg value tests,
    `SwiftKeyTypingTraceRecorder` tests, and n-gram per-locale flush tests if
    the stores already expose a tractable test seam.
  - Acceptance: combining-mark Arabic shaping, unknown Snygg selector import,
    `contentScale` serialization, private-session trace suppression, and
    per-locale n-gram flush behavior each have focused regression coverage or a
    documented reason they require an extraction seam first.
  - Verify: focused test packages plus full Gradle gate.
  - Shipped: v1.8.234 (2026-06-04) with combining-mark Arabic shaping
    coverage, unknown-selector rejection coverage, full `contentScale`
    serialization/default coverage, private-session recorder suppression
    coverage, and source-level locale-scoped bigram/trigram flush guards.
  - Complexity: M

### Researcher Queue (Cycle 2 - 2026-06-04)

- [x] 🔬 `startup-diagnostics-and-docs-refresh-2026-06-04` - re-read the
  current v1.8.218 repo state, the committed audit docs, the last 15 shipped
  releases, and current upstream/standards sources. Existing settings-search,
  dependency, upstream FlorisBoard, CLDR/Emoji, F-Droid, device-gated, and
  maintainer-gated rows remain correctly represented below; this cycle adds
  only the net-new startup diagnostics and source-of-truth documentation gaps.

#### Reliability & diagnostics

- [x] 🤖 P1 — Persist or surface staged startup exceptions before Settings opens (R2-1)
  - Shipped v1.8.218: `CrashUtility.consumeStagedException(...)` persists
    staged init exceptions without invoking the process-killing uncaught
    handler, and `FlorisAppActivity` opens `CrashDialogActivity` before the
    splash keep condition can hang on `preferenceStoreLoaded`.
  - Why: A synchronous `FlorisApplication.onCreate()` failure is staged and the
    application returns, but no production call drains the staged exception into
    the existing crash-file / notification path. On a privacy keyboard, a silent
    startup failure is a trust problem even if the failure is rare.
  - Evidence: `FlorisApplication.kt:100-156` installs Flog/CrashUtility, then
    catches `Exception` with `CrashUtility.stageException(e); return`;
    `CrashUtility.kt:159-170` stores and drains staged exceptions; `rg
    "handleStagedButUnhandledExceptions" app/src/main/kotlin app/src/test/kotlin`
    finds no production/test call site; `FlorisAppActivity.kt:100-170` opens the
    Settings activity without reading `CrashUtility`; `docs/AUDIT_2026-05-28.md:16-17`
    independently verified the same path.
  - Touches: `FlorisApplication.kt`, `CrashUtility.kt`, `FlorisAppActivity.kt`,
    and a focused JVM/Robolectric test around the chosen staging/drain policy.
  - Acceptance: an injected synchronous app-init failure creates a persisted
    stacktrace or visible crash/recovery surface; the splash screen does not
    hang silently; the implementation documents whether it intentionally calls
    the existing uncaught handler (process-killing) or writes a recoverable
    staged-init stacktrace without killing the Settings activity.
  - Verify: `:app:testDebugUnitTest`; manual debug build with a temporary
    injected pre-`init()` failure before removing the injection.
  - Complexity: M
- [x] 🤖 P2 — Replace remaining restore/crash diagnostic `printStackTrace()` paths with project logging plus user-safe fallback copy (R2-2)
  - Shipped v1.8.219: restore archive-load, per-section restore, restore
    launcher, and top-level restore failures now route diagnostics through
    `flogError`, restore cards/toasts use `BackupRestorePolicy.restoreErrorMessage(...)`
    to avoid null/blank user copy, and crash stacktrace write failures use the
    `CRASH_UTILITY` logging topic instead of raw `printStackTrace()`.
  - Why: The restore flow and crash-file write helper still fall back to raw
    `printStackTrace()` on exceptional diagnostic paths, while adjacent code
    already uses `flogError`. The fix should improve consistency and user-facing
    failure text without overstating release-build file-log coverage, because
    `Flog` is debug-gated and `fileLog()` is still a stub.
  - Evidence: `RestoreScreen.kt` failure paths are called out in
    `docs/AUDIT_2026-05-28.md:19-22`; sibling `BackupScreen.kt:205` and
    `BackupScreen.kt:338` use `flogError`; `CrashUtility.kt:366-370` still
    catches crash-file write failures with `e.printStackTrace()`;
    `Flog.kt:326` tracks the file-logging TODO.
  - Touches: `RestoreScreen.kt`, `CrashUtility.kt`, possibly `Flog.kt` only if
    a minimal release-safe sink is added; add focused tests for non-null restore
    failure messages where practical.
  - Acceptance: restore failure cards/toasts use stable fallback text when
    `localizedMessage` is null; diagnostic exceptions route through the project
    logging idiom; docs/changelog do not claim persisted release logs unless a
    real persisted sink is implemented.
  - Verify: `:app:testDebugUnitTest`; manual restore-failure smoke with a bad
    archive on the Android SDK host.
  - Complexity: S-M

#### Docs & source-of-truth

- [x] 🤖 P2 — Refresh root onboarding docs to the v1.8.220 source of truth (R2-3)
  - Shipped v1.8.220: root onboarding docs now route open work to
    `ROADMAP.md`, shipped state to `COMPLETED.md`, release notes to
    `CHANGELOG.md` plus fastlane metadata, and archived parity/improvement
    plans are clearly historical context.
  - Why: The live roadmap is current, but the fast onboarding docs still mixed
    older stack facts, archived-plan routes, and retired release-note
    instructions. Future build passes use these docs first, so stale routing
    increases the chance of wrong release or planning edits.
  - Evidence: the pre-fix stale scan found outdated stack/version facts and
    root release-note/planning routes in `PROJECT_CONTEXT.md`,
    `ARCHITECTURE.md`, `CONTRIBUTING.md`, `README.md`, `docs/REPO_HYGIENE.md`,
    and `AGENTS.md`.
  - Touches: `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`,
    `README.md`, `docs/REPO_HYGIENE.md`, `AGENTS.md`, and release ledgers.
  - Acceptance: root docs agree that `ROADMAP.md` is the open-work source,
    `COMPLETED.md` is shipped-state summary, `CHANGELOG.md` is the only release
    note stream, and current stack/release facts match v1.8.220.
  - Verify: stale reference scan; `:app:verifyNoInternetPermission`;
    `:app:testDebugUnitTest`; `:app:lintDebug`; `:app:assembleDebug`;
    fastlane metadata check; repo hygiene check.
  - Complexity: M

### Researcher Queue (Cycle 1 - 2026-06-04)

- [x] 🔬 `voice-copy-dependency-refresh-2026-06-04` - rechecked the v1.8.207
  voice-copy slice and public dependency metadata without running Gradle on this
  VM. FUTO Voice Input remains the correct privacy-preserving handoff for voice
  copy, Android 17/API 37 remains future behavior-gate work, and dependency
  drift is low-risk maintenance rather than a security item. The new P3
  dependency freshness row is the build-lane handoff.

*Research conducted 2026-06-03. Items below are new — not duplicates of Existing Planned Work.*

This pass focused on the v1.8.204 **settings search** drop (the newest feature, shipped this release) and a few cross-cutting gaps the three deep audits (`docs/AUDIT_2026-05-28/29` + `2026-06-02`) and the existing roadmap do not already cover. The search subsystem is a hand-maintained static catalog that mirrors the navigation graph; v1.8.221 adds a drift guard, v1.8.222 adds a no-results escape hatch, v1.8.223 adds high-traffic synonym coverage, v1.8.224 resets result scrolling when the query changes, and v1.8.235 adds the TalkBack/accessibility pass, while the remaining search work is highlight-lifecycle polish.

### Quick Wins

All current quick wins shipped through v1.8.215. Remaining settings-search work is listed under Larger Bets.

### Larger Bets

- [x] P1 — Drift guard test: every `SettingsSearchDestination` is navigable + every entry resId resolves (RA-1)
  - Shipped v1.8.221: `SettingsSearchIndexIntegrityTest` now fails on
    duplicate entry IDs, missing/blank real string resources, fake resolver
    fallback text, and destination-route mapping drift. The screen navigation
    path uses the same `SettingsSearchDestination.toSearchRoute()` helper the
    test pins.
  - Why: The search catalog is a 33-value enum + ~100 hand-curated entries that mirror the navigation graph and reference real string resIds. Nothing fails the build when a Settings screen is added without a search entry, an entry points at a deleted/renamed pref label, or a `destination` loses its `Routes.*` arm. The only existing test (`SettingsSearchIndexTest.kt`) uses a fake `resolve` map and asserts ranking, not integrity. This is the same registry-drift failure mode the project already hit elsewhere (see the partitioned-prefs golden test).
  - Evidence: pre-fix `SettingsSearchIndex.kt` held enum/catalog rows directly and `SettingsSearchScreen.kt` kept destination routing inside a private navigation function; the existing `SettingsSearchIndexTest` resolved strings through a fake map.
  - Touches: `SettingsSearchScreen.kt`; `SettingsSearchIndexIntegrityTest.kt`.
  - Acceptance: deleting a referenced string res or adding an unmapped destination fails the test; passes today.
  - Verify: `:app:testDebugUnitTest --tests "dev.patrickgold.florisboard.app.settings.search.*"`; full Gradle gate.
  - Complexity: M
- [x] P2 — No-results fallback action in settings search (RA-2)
  - Shipped v1.8.222: zero-result searches now show a centered
    `Browse all settings` text button that navigates to `Routes.Settings.Home`.
  - Why: An empty result set renders only gray "no results for X" text — a dead-end. There's no escape hatch (browse-all / jump to Settings home) and, notably, no link into the Android **system** keyboard settings, which is where a missing pref often actually lives (the search index is app-internal only).
  - Evidence: pre-fix `SettingsSearchScreen.kt` rendered only a no-results
    `Text`; the shipped branch now renders the message plus action.
  - Touches: `SettingsSearchScreen`; default `settings__search__browse_all`
    string.
  - Acceptance: from a zero-result query the user can reach Settings home in one tap; copy is translation-safe.
  - Verify: focused search tests plus `:app:assembleDebug`; full Gradle gate.
  - Complexity: S
- [x] P2 — Reset settings-search result scroll when the query changes (RA-10)
  - Shipped v1.8.224: `SettingsSearchScreen` now scrolls populated
    non-blank result sets back to item 0 whenever the query changes, while
    blank and no-result states stay untouched. `SettingsSearchScreenStateTest`
    pins the reset guard.
  - Why: settings search ranks results per query, but the list keeps one
    `LazyListState` across every edit. A user who scrolls down one query and
    then types a different query can land mid-list for the new result set,
    hiding the highest-ranked destination until they manually scroll back up.
  - Evidence: pre-fix `SettingsSearchScreen` recomputed `results` from
    `searchQuery` but created one unkeyed `rememberLazyListState()` for the
    lifetime of the screen; only the initial-focus `LaunchedEffect` existed.
  - Touches: `SettingsSearchScreen`; `SettingsSearchScreenStateTest`.
  - Acceptance: after changing a non-blank query, the result list starts at the
    first/highest-ranked result; clearing or entering a no-results query does not
    leave the next populated query scrolled into the middle.
  - Verify: focused search package tests plus full Gradle gate.
  - Complexity: S
- [x] P2 — Keyword/synonym coverage audit for high-traffic settings terms (RA-3)
  - Shipped v1.8.223: `SettingsSearchIndex` now adds targeted keyword
    synonyms for theme mode, haptic feedback, trace/shape-writing gestures,
    punctuation spacing, and privacy audit rows. `SettingsSearchIndexTest`
    pins the requested "dark theme", "haptic", "trace", "punctuation", and
    "privacy" queries to the expected destinations, with exact target-row
    checks for dark mode, punctuation, and privacy.
  - Why: Search matches title/summary/screen-title/keywords substrings, but many discoverable prefs have sparse `keywords` (e.g. "haptic" only on input-feedback, "dark"/"light" not on theme.mode, "swipe" present on gestures but "shape writing"/"trace" partial). Users search by capability words, not the exact shipped label.
  - Evidence: pre-fix `SettingsSearchIndex` rows had no targeted synonyms for
    theme mode, glide trace/shape-writing, punctuation spacing, or privacy
    audit capability queries; the shipped rows now carry those keywords.
  - Touches: `SettingsSearchIndex.entries` keyword strings only (no code path change); `SettingsSearchIndexTest`.
  - Acceptance: a documented set of capability synonyms each resolve to the right destination; test pins them.
  - Verify: focused search package tests plus full Gradle gate.
  - Complexity: M
- [x] P2 — Accessibility/TalkBack pass over the search screen + result list (RA-4)
  - Why: `ACCESSIBILITY.md` does not yet cover the new search surface. The result `JetPrefListItem`s are `clickable` with no `role`/merged-semantics announcement of "result N of M", the leading icon is correctly `contentDescription = null` (decorative) but the field itself has no labelled state, and the empty/no-results text isn't a live region — a TalkBack user won't hear result-count changes as they type.
  - Evidence: `SettingsSearchScreen.kt:82-143` — no `Modifier.semantics{}`/`liveRegion`/`role` on the field, results, or the count-changing branches; `docs/ACCESSIBILITY.md` "Manual QA checklist" has no search entry.
  - Touches: `SettingsSearchScreen` semantics (field label, results `role = Role.Button`/merged, `liveRegion = Polite` on the result-count container); add a search row to the `docs/ACCESSIBILITY.md` manual-QA checklist.
  - Acceptance: TalkBack announces a labelled search field, reads each result's screen + title, and reports result-count changes; checklist documents the flow.
  - Verify: manual TalkBack on-device; `:app:assembleDebug`.
  - Shipped: v1.8.235 (2026-06-04) with labelled/stateful search-field
    semantics, polite live-region status updates, merged button-role result-row
    labels with result position/screen/title/summary context, a focused
    accessibility contract test, and manual checklist coverage. Manual TalkBack
    smoke remains device-gated.
  - Complexity: M
- [x] P2 — Consume or dismiss Settings search highlight state after the target screen is reached (RA-9)
  - Why: the search-result highlight card is stored in a process-wide Compose
    singleton and rendered by the shared settings scaffold. Because production
    code never clears it, the same "Search result" card can reappear whenever
    the user later visits the matching settings screen, pushing content down
    after the original search context is gone.
  - Evidence: `SettingsSearchScreen.kt:158-164` calls
    `SettingsSearchHighlightStore.mark(...)`; `FlorisScreen.kt:234-247`
    renders a `FlorisInfoCard` whenever `activeTarget.screenTitle == title`;
    `SettingsSearchIndex.kt:84-99` stores already-resolved display strings and
    exposes `clear()`, but `rg "SettingsSearchHighlightStore.clear"` finds only
    the JVM test caller.
  - Touches: `SettingsSearchHighlightStore` plus the `FlorisScreen` search-card
    rendering path. Prefer a one-shot `consumeTargetFor(screenTitle)` API or a
    local displayed-target copy with a dismiss action, so the card survives the
    first target-screen composition but does not persist across later visits.
    If practical, match by a stable destination/screen key instead of localized
    title text.
  - Acceptance: selecting a search result still shows the destination card once;
    leaving and returning to that screen without a new search does not show the
    stale card; users can dismiss the card explicitly if it remains visible.
  - Verify: focused JVM test for the consume/clear contract; `:app:assembleDebug`;
    optional manual Settings search -> destination -> back -> destination smoke.
  - Shipped: v1.8.237 (2026-06-04) with one-shot
    `SettingsSearchHighlightStore.consumeTargetFor(...)`, local
    `FlorisScreen` displayed-target state, an explicit close action on the
    highlight card, and focused JUnit coverage. Manual navigation smoke remains
    pending.
  - Complexity: M
- [x] P3 — Surface settings search from Settings home (entry-point discoverability) (RA-8)
  - Confirmed 2026-06-04: Settings Home already exposes the search route as a
    top app-bar action with `settings__search__title` content description, so
    search is reachable from the first Settings screen without scrolling.
  - Why: Search is a registered route but reaching it depends on the home-screen wiring; a top-of-home search affordance (or app-bar icon) is the conventional discovery point and matches how Gboard/SwiftKey expose their settings search.
  - Evidence: `HomeScreen.kt:68-75` defines `actions { FlorisIconButton(...) }`,
    `onClick = { navController.navigate(Routes.Settings.Search) }`, icon
    `Icons.Default.Search`, and content description
    `R.string.settings__search__title`.
  - Touches: none; source already satisfies the row.
  - Acceptance: search is reachable from the first screen of Settings without scrolling.
  - Verify: source inspection; optional manual on-device smoke.
  - Complexity: S

## Continuation State

### Last Completed Cycle

Cycle 24 - paired-device corruption recovery (2026-06-06).

### Current Focus

Continue with Cycle 25 by consolidating the Sync pairing hardening cluster.
R19-1, R22-1, R23-1, and R24-1 now touch the same Settings -> Sync workflow;
the next pass should inspect whether they need one shared helper/phase plan for
payload copy/share, confirmation, fingerprint formatting, stable local identity,
and corrupt-state recovery.

### Important Findings So Far

- Cycle 19 added R19-1 for accessible Sync pairing payload copy/share fallback.
- Cycle 20 added R20-1 for post-retirement migration guide/source-of-truth drift.
- Cycle 21 added R21-1 for first-run dictionary import recovery after picker
  cancel or import failure.
- Cycle 22 added R22-1 for explicit Sync pairing confirmation/fingerprint review
  before scanned or pasted public-key payloads are persisted.
- Cycle 23 added R23-1 for stable, encrypted, backup-scoped local X25519 Sync
  identity storage before transport activation.
- Cycle 24 added R24-1 for corrupt paired-device JSON recovery instead of
  silently rendering the benign empty-device state.
- Cycle 18 remains the highest-value product/trust cluster: public FUTO Swipe
  glide benchmark, migration recovery assistant, addon developer trust kit,
  privacy evidence dashboard, and recurring 16 KB review.
- Maintainer-gated items remain blocked on accounts, sibling repos, hardware,
  keys, or product decisions; do not mark device-gated rows complete without
  hardware proof.

### Next Best Actions

1. Re-read R19-1/R22-1/R23-1/R24-1 plus `SyncSettingsScreen.kt`,
   `SyncQrCode.kt`, sync tests, and `strings.xml` to decide the minimal
   implementation order and shared helper boundaries.
2. Inspect `docs/ACCESSIBILITY.md` and `docs/THREAT_MODEL.md` for the doc/test
   updates that should land with the Sync pairing hardening cluster.
3. Run targeted external-source refresh for Android Compose accessibility,
   clipboard/share affordances for payload fallback, and local pairing
   fingerprint UX.
4. After the Sync cluster is sequenced, return to non-Sync release-readiness
   leads such as F-Droid/16 KB checks and post-retirement migration docs.

### Unprocessed Leads

- Settings -> Sync generated-payload card has QR-only output today.
- `docs/MIGRATE_FROM_SWIFTKEY.md` still leads with pre-cutoff export copy.
- README and migration guide disagree on hardware-keyboard import status.
- `docs/ACCESSIBILITY.md` does not yet list a Sync pairing/no-camera check.
- Sync pairing hardening is now split across four roadmap rows and may need an
  implementation-order note to prevent duplicate UI helpers or conflicting
  tests.

### Files Still To Inspect

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/SyncQrCode.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairedSyncDevice.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairingPayloadGenerator.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/SealedBoxCrypto.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/sync/`
- `app/src/main/res/values/strings.xml`
- `docs/ACCESSIBILITY.md`
- `docs/THREAT_MODEL.md`

### Searches Still To Run

- `Android Compose accessibility QR code copy fallback official`
- `Android share sheet copy payload accessibility official`
- `local sync device fingerprint confirmation UX official docs`
- `F-Droid reproducible builds Android 16 KB page size addon APK`
