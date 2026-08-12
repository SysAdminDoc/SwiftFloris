# SwiftFloris Threat Model

**Last updated:** 2026-07-02 (v1.9.54)
**Scope:** SwiftFloris Android IME, base APK only (no optional cloud-bound modules — none ship today, none are planned).
**Audience:** maintainers, reviewers, and security-conscious users evaluating SwiftFloris vs proprietary keyboards.

### What changed since the v1.8.68 baseline

The v1.8.85 → v1.8.122 sixth- and seventh-pass audits closed the following live attack-surface
items. They are referenced by their original finding IDs so the audit trail is traceable through
the local audit archive and the README release log; the public trust state below is the canonical
clean-clone summary:

- **v1.8.85** — `verifyNoInternetPermission` now scans **merged** manifests in addition to source
  manifests, and honours `tools:node="remove"`. Source-only scanning could miss a transitively-
  declared INTERNET permission from an aggregated library; the merged-manifest scan closes it.
- **v1.9.59** — the same fail-closed enrollment permission allowlist now drives the runtime addon/MCP
  policy, source and merged manifest gates, and addon APK validation. The registry also pins the
  allowlist to `NoNetworkPermissionPolicy` so an unknown permission cannot pass one surface and fail another.
- **v1.8.85** — Personal dictionary now excluded from cloud-backup AND device-to-device transfer.
  New `app/src/main/res/xml/data_extraction_rules.xml` ships the Android-12+ schema with explicit
  excludes for the SQLCipher database and its Tink-wrapped passphrase.
- **v1.8.86 / v1.8.87** — FLAG_SECURE coverage extended via `keyVariation == PASSWORD` propagation
  for `TYPE_NUMBER_VARIATION_PASSWORD` and a Compose `DisposableEffect` on the encrypted-dictionary
  passphrase dialog. Previously the numeric-PIN entry path skipped the IME-side screenshot block.
- **v1.8.95** — `verifyDataExtractionRules` build gate added. Pins the load-bearing
  data_extraction_rules.xml excludes against accidental rewrite (e.g. a tool that "normalizes" the
  XML and drops the personal-dictionary include path).
- **v1.8.85 / v1.8.89** — `ZipUtils.unzip` now aborts atomically on security violations (path
  traversal, oversized entries, decompression bombs). Benign anomalies (a single corrupted entry
  in an otherwise valid archive) continue with a warning so a partially-corrupted dictionary
  import still works.
- **v1.8.104** — App-declared `IME_FLAG_NO_PERSONALIZED_LEARNING` now always forces
  `isIncognitoMode = true` regardless of the user's `prefs.suggestion.incognitoMode` preference.
  Previously a `FORCE_OFF` user preference silently overrode cross-app sensitive-field
  declarations (Signal, ProtonMail, banking).
- **v1.8.105** — Clipboard cut/copy now gates on `isIncognitoMode` in addition to
  `isPasswordField()`. Closes the cross-app leakage path where a user typing in an incognito-
  declared field could Cut text into IME-local clipboard history.
- **v1.8.106** — Voice handoff now early-returns in PASSWORD / numeric-PIN / web-password /
  incognito fields. External voice IMEs (which typically have full network permission) cannot
  receive sensitive-field audio.
- **v1.8.111** — Provider-backed clipboard media clones now have a 32 MiB image cap and 128 MiB
  video cap; oversized preview dimensions (> 8192 px) are rejected before decode.
- **v1.8.112** — Provider-backed media closed on automatic history rotation / timed expiry
  before the Room rows are deleted. Closes the URI-permission leak window.
- **v1.8.113** — `VoiceInputSetupActivity` pinned as `android:exported="false"` plus
  setup-intent extras validation.
- **v1.8.114** — External voice IMEs must hold `RECORD_AUDIO` permission before SwiftFloris
  considers handoff "ready." Prevents handoff to an IME that lacks mic access.
- **v1.8.122** — `KenLmTrieReader.readBytesAt(...)` now rejects header/pre-body absolute offsets
  instead of aliasing them to trie-body zero. Header probe also avoids large-file `toInt()`
  overflow. Closes a precondition-violation bug class on adversarially-crafted KenLM model files.
- **v1.8.123** — Roborazzi visual-regression is now a hard CI gate (was `continue-on-error`).
  Catches accidental theme regressions that would expose hidden states.
- **v1.8.124** — Addon trust pins now expose targeted revoke (`AddonSigningPinSet.withoutPackage`)
  and reset-all actions in Settings → Addons. Users can disenroll a single compromised addon
  without losing trust for the rest of the addon set.
- **v1.8.125** — Addon dictionary-pack assets mount via `PackageManager#getResourcesForApplication`,
  no extraction, no temp copies. Closes the symlink-vs-extracted-file confusion class.
- **v1.8.229** — First-seen non-co-signed addon APKs now stay rejected until Settings records an
  explicit signing-certificate pin for the displayed fingerprint; co-signed addons still enroll
  automatically. Closes the discovery-as-consent gap in optional addon enrollment.
- **v1.8.230** — Sync sealed-box envelopes now have deterministic v1 schema/vector coverage:
  fixed X25519 keys pin the ephemeral-public-key + nonce + AES-GCM ciphertext shape before any
  CRDT sync transport persists envelopes.
- **v1.8.231** — Dynamic incognito toggles now re-apply the IME window `FLAG_SECURE`
  policy immediately for the active field. Plain fields become screenshot-blocked when
  incognito turns on, and password / no-personalized-learning fields stay protected when
  the user attempts to toggle incognito off.
- **v1.8.236** — Async suggestion candidate generation now consumes an immutable request
  privacy snapshot. Emoji/word providers, typing-trace gating, and smart-compose ghost
  text use the field/session privacy facts captured before coroutine launch instead of
  re-reading live incognito or editor-info state after a field switch or privacy toggle.
- **v1.8.174** — Repo-hygiene CI gate now rejects root-level `*.apk` / `*.aab` / `*.jks` /
  `*.keystore` / `local.properties` / `*.backup*` / large branding PNGs. Closes the supply-
  chain footgun where a maintainer's working-tree keystore could land in a commit.
- **v1.8.175** — F-Droid metadata gate (`scripts/check-fastlane-metadata.sh`) prevents stale
  upstream FlorisBoard branding from publishing on F-Droid; rejects future versionCode bumps
  that ship without a matching per-versionCode changelog file.

The remaining open audit items (see `.ai/research/2026-05-17/SEVENTH_PASS_FINDINGS.md` §2 / §3)
are local-recogniser bring-up gated (the voice-route findings #1–6, #8, #10), perf polish on
non-hot paths, or UX polish on dead code paths.

This document enumerates the realistic attacker scenarios SwiftFloris defends against, the
attack surfaces it deliberately closes, and the gaps that remain. It is the structured
counterpart to the no-network promise — keep both in sync on every change that touches
permissions, IPC surfaces, or persistence.

---

## 1. Trust posture

The product wedge is **"every paywalled cloud feature, fully on-device, fully auditable, with
zero account requirement"**. Three load-bearing implications:

1. **Zero network permissions on the base APK.** No `INTERNET`, no
   `ACCESS_NETWORK_STATE`, no `ACCESS_WIFI_STATE`. Pinned by
   `:app:verifyNoInternetPermission` Gradle task. The build
   fails if any AndroidManifest declares a permission outside the explicit
   enrollment allowlist. Users can verify this by inspecting the installed
   APK's manifest (`aapt dump permissions`).

2. **No vendor account, no telemetry.** Crash reporting (if ever introduced)
   would be opt-in only, never auto-upload. Federated learning to vendor cloud
   is on the rejected list in the privacy policy.

3. **Auditability over performance/feature debt.** No closed-source `.so` blobs
   (e.g. Google's `libjni_latinimegoogle.so`). Reproducible-build verification
   on F-Droid is a Now item (N6.3).

---

## 2. Threat actors

| Actor | Motivation | Capability |
|---|---|---|
| **Co-installed app (low-priv)** | Keylogging, credential exfil, profiling | Holds default permissions only; cannot read foreground app's text fields directly |
| **Co-installed app (high-priv)** | Same | Holds `READ_USER_DICTIONARY`, `READ_LOGS`, etc.; can query the system `UserDictionary` ContentProvider |
| **Lock-screen attacker** | Read shoulder-surfed input, fingerprint window contents | Physical access to unlocked / locked device |
| **Supply-chain attacker** | Distribute a modified APK that exfiltrates input | Repacks + resigns with their own keystore, hosts the modified APK on a fake mirror |
| **Network attacker (MITM)** | Intercept input, inject responses | Can read/modify network traffic |
| **Hostile editor (CAKI)** | Inject KeyEvents at the IME from a non-foreground app | Can construct synthetic IPC messages bypassing focus checks |

Out of scope:
- Root / system-level adversary (any guarantees collapse).
- Malicious host app (it owns the input field; nothing the IME can do).
- Forensic disk recovery on lost device (handled by full-disk encryption at the OS layer).

---

## 3. Defenses (live in v1.8.68 unless flagged)

### 3.1 No-network contract
- No `INTERNET` permission. Build-time gated (N7.1).
- Network attacker has nothing to intercept because no traffic is generated.

### 3.2 Personal dictionary isolation
- `learnWord` writes only to the app-private Floris Room database under
  `getDataDir()`. **Never** to the system `UserDictionary` ContentProvider, which is
  queryable by any app holding `READ_USER_DICTIONARY`.
- Regression-tested in `PersonalDictionaryIsolationTest` — static-content
  inspection of `DictionaryManager.kt` `learnWord` body fails the build if a
  future contributor accidentally references `systemUserDictionaryDao`.
- `enableSystemUserDictionary` is opt-in; even when on, it only **reads** from
  the system provider, never writes. System mode exposes a read-only DAO, and
  the SwiftFloris Settings screen disables add/edit/delete/import actions. Users
  who want to change those shared entries are sent to Android's system dictionary
  settings, outside SwiftFloris's provider access.

### 3.3 Password-field hardening
- Suggestions disabled when `keyVariation == PASSWORD` (composing flagged off).
- Auto-learn skipped on password fields even if the host app forgets to set
  `IME_FLAG_NO_PERSONALIZED_LEARNING` — many do (see HeliBoard #2124,
  AnySoftKeyboard #1399).
- IME-local clipboard history skips writes from `performClipboardCut` /
  `performClipboardCopy` when the active field is a password variation.
- `WindowManager.LayoutParams.FLAG_SECURE` is set on the IME window for password
  fields and when incognito mode is active, including mid-session dynamic
  incognito toggles. This keeps the suggestion strip and long-press preview out
  of screenshots, screen recordings, and non-secure external displays during
  private typing.

### 3.4 Clipboard history
- Clipboard history is SQLCipher-encrypted at rest when the local SQLCipher
  provider is available; the 64-byte passphrase is wrapped by a
  Keystore-backed Tink key. Existing plaintext history is staged, migrated with
  row-count verification, and preserved on failure. If SQLCipher cannot load,
  the store reports an explicit unencrypted state and keeps the history in
  plaintext Room storage rather than hiding the downgrade.
- Cloned clipboard media is stored as plaintext under the app-private no-backup
  directory. Android app sandboxing and the non-exported provider restrict
  ordinary cross-app reads.
- Sensitive-clip flag (Android 13+) is preserved on clipboard ingestion and
  honored on display.
- Clipboard data selected for portable export is never exposed as a ZIP:
  SwiftFloris seals the app-private staging archive into a versioned
  passphrase-derived AES-256-GCM `.sfbak` envelope before SAF or FileProvider
  handoff. Restore authenticates before extraction, uses a pre-mutation
  recovery snapshot for rollback, and keeps earlier plaintext ZIPs only as a
  visibly warned migration path. This export envelope is separate from the
  live clipboard database and cloned media storage described above.

### 3.5 SQLCipher personal dictionary
- The app-private personal Room dictionary opens through SQLCipher
  (`net.zetetic:sqlcipher-android` 4.17.0). The 64-byte SQLCipher passphrase is
  generated locally, wrapped by Tink `Aead`, and protected by an
  AndroidKeystore-held AES-256-GCM key.
- Existing AndroidX encrypted-preference passphrase payloads migrate once into
  the Tink-wrapped `sqlcipher_passphrase_tink_v1` shape. If legacy keysets exist
  but cannot recover the passphrase, the app fails closed instead of generating
  a new passphrase that would orphan an existing encrypted database.

### 3.6 Sync sealed-box envelopes
- Base-APK sync remains transport-neutral: SwiftFloris can write encrypted CRDT deltas to the
  user's chosen local folder / Syncthing / Nextcloud-style channel, but it still declares no
  network permission and does not contact a vendor server.
- Pairing payloads carry raw 32-byte X25519 public keys as lowercase hex. Private keys stay on
  the device that generated them.
- The sealed-box envelope schema is v1 and intentionally fixed before transport activation:
  `ephemeralPublicKey(32) || nonce(12) || aesGcmCiphertextPlusTag(n + 16)`.
- Key material is `HKDF-SHA-256(sharedX25519, zeroSalt, info="swiftfloris-sealed-box-key-v1")`.
  The nonce is `SHA-256(derivedKey || ephemeralPublicKey || recipientPublicKey)[0..12)`.
- Incompatible envelope or KDF changes must introduce a new schema/version before persisted
  envelopes exist; silent reinterpretation of v1 bytes is not allowed.
- `SealedBoxCryptoTest` pins a deterministic fixed-key v1 vector plus malformed/tampered
  envelope null-return behavior. Raw X25519 output and temporary KDF/nonce buffers are scrubbed
  where the JVM exposes mutable arrays.

### 3.7 Supply-chain integrity
- APK signing fingerprint visible in Settings → About → APK signing fingerprint
  (N7.5). Users can compare the SHA-256 against the value pinned in the README
  to detect a swap.
- First-party build-twice APK self-verification exists in
  `scripts/verify-reproducible-apk.sh` and is composed by
  `scripts/release-evidence.ps1`; F-Droid verified rebuild remains the public
  distribution target.

### 3.8 CAKI (cross-app KeyEvent injection)
- SwiftFloris contains a client-side `IMcpDaemon.aidl` Binder contract and
  binds services exported by separately installed daemon apps. SwiftFloris
  does not export an MCP AIDL service of its own; its platform IME and
  spellchecker services remain protected by the platform binding
  permissions.
- Before certificate trust, registry enrollment, or binding, MCP discovery
  rejects a daemon package requesting `INTERNET`, network-state, or Wi-Fi
  state/change permissions. The check uses requested permissions regardless
  of current grant state and is repeated on explicit Settings rescans.
- KeyEvent dispatch in `AbstractEditorInstance.sendDownUpKeyEvent` always
  attaches `KeyCharacterMap.VIRTUAL_KEYBOARD` source; the host editor remains
  authoritative for whether a synthetic event is honored.
- We do **not** currently inspect / filter incoming `dispatchKeyEvent` for
  cross-app origin (the platform handles this); a defense-in-depth pass on
  `metaState` validation is on the longer roadmap.

### 3.9 Auditability
- Apache-2.0 codebase, no obfuscation in debug builds, ProGuard rules visible
  in `app/proguard-rules.pro` for release builds.
- No closed-source binary blobs (e.g. `libjni_latinimegoogle.so`); the base
  APK ships **zero** native code as of v1.8.185 (the prior `:lib:native` Rust
  placeholder was dropped — see `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md` EI11 + the
  v1.8.185 CHANGELOG entry). When optional native runtimes (LiteRT-LM,
  whisper.cpp, librime, etc.) land they ship as out-of-tree signed addon
  APKs with their own auditable sources, never as a hidden `:app`
  dependency.
- F-Droid metadata + reproducible-build target are Now items (N6.2 / N6.3).

---

## 4. Known gaps (informational)

| Gap | Severity | Tracker |
|---|---|---|
| Reproducible-build verification not yet active on F-Droid | Medium | N6.3 |
| Voice-command parser uses external FUTO Voice Input — that app has its own threat model and permissions | Low (FUTO is offline; user makes the trust decision when installing it) | Documented under "External components" in README |

---

## 5. Verification checklist (run on every release)

- [ ] `aapt dump permissions app-release.apk` matches
  `android.permission.VIBRATE`, `android.permission.POST_NOTIFICATIONS`,
  `android.permission.READ_CALENDAR`,
  `android.permission.QUERY_ADVANCED_PROTECTION_MODE`, and
  `io.github.sysadmindoc.swiftfloris.permission.BIND_MCP` (and any new entry
  is justified in the release notes + threat model).
- [ ] `:app:verifyNoInternetPermission` passes locally.
- [ ] `PersonalDictionaryIsolationTest` passes locally.
- [ ] `PersonalDictionaryEncryptionTest` passes locally; this includes the SQLCipher/Tink guard and confirms the personal dictionary Room source no longer contains `allowMainThreadQueries`.
- [ ] APK signing fingerprint matches the value pinned in README (release-build only; debug builds use a per-developer keystore).
- [ ] No new `TODO()` runtime stubs introduced (lint check or grep).

---

## 6. Reporting a vulnerability

Open a GitHub issue or, for embargo-required disclosures, contact the maintainer
listed in `LICENSE` / repository profile. There is no separate security@ alias —
the project is small enough that public-issue triage is the operating mode.
