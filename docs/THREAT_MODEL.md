# SwiftFloris Threat Model

**Last updated:** 2026-05-17 (v1.8.68)
**Scope:** SwiftFloris Android IME, base APK only (no optional cloud-bound modules — none ship today, none are planned).
**Audience:** maintainers, reviewers, and security-conscious users evaluating SwiftFloris vs proprietary keyboards.

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
   `:app:verifyNoInternetPermission` Gradle task (ROADMAP §6 N7.1). The build
   fails if any AndroidManifest declares a network permission. Users can verify
   this by inspecting the installed APK's manifest (`aapt dump permissions`).

2. **No vendor account, no telemetry.** Crash reporting (if ever introduced)
   would be opt-in only, never auto-upload. Federated learning to vendor cloud
   is on the rejected list (ROADMAP §10).

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
  the system provider, never writes (the existing `UserDictionaryDao` interface
  exposed to system mode does not back `insert` / `update` / `delete` to the
  ContentResolver writer paths).

### 3.3 Password-field hardening
- Suggestions disabled when `keyVariation == PASSWORD` (composing flagged off).
- Auto-learn skipped on password fields even if the host app forgets to set
  `IME_FLAG_NO_PERSONALIZED_LEARNING` — many do (see HeliBoard #2124,
  AnySoftKeyboard #1399).
- IME-local clipboard history skips writes from `performClipboardCut` /
  `performClipboardCopy` when the active field is a password variation.
- **Pending:** `WindowManager.LayoutParams.FLAG_SECURE` on suggestion-strip popups
  (next pass — N7.2 still has open work).

### 3.4 Encrypted clipboard
- Clipboard items are AES-256-GCM encrypted at rest (max 50 entries). The
  serialized history payload is wrapped by Tink `Aead` with an
  AndroidKeystore-held AES-256-GCM key, hardware-backed when the device
  supports it. Older AndroidX `EncryptedSharedPreferences` payloads migrate
  once when their legacy keysets are still readable.
- Sensitive-clip flag (Android 13+) is preserved on clipboard ingestion and
  honored on display.

### 3.5 SQLCipher personal dictionary
- The app-private personal Room dictionary opens through SQLCipher
  (`net.zetetic:sqlcipher-android` 4.16.0). The 64-byte SQLCipher passphrase is
  generated locally, wrapped by Tink `Aead`, and protected by an
  AndroidKeystore-held AES-256-GCM key.
- Existing AndroidX encrypted-preference passphrase payloads migrate once into
  the Tink-wrapped `sqlcipher_passphrase_tink_v1` shape. If legacy keysets exist
  but cannot recover the passphrase, the app fails closed instead of generating
  a new passphrase that would orphan an existing encrypted database.

### 3.6 Supply-chain integrity
- APK signing fingerprint visible in Settings → About → APK signing fingerprint
  (N7.5). Users can compare the SHA-256 against the value pinned in the README
  to detect a swap.
- First-party build-twice APK self-verification exists in
  `.github/workflows/reproducible-build.yml`; F-Droid verified rebuild remains
  the public distribution target.

### 3.7 CAKI (cross-app KeyEvent injection)
- IME does not expose AIDL services beyond the platform `InputMethodService`.
- KeyEvent dispatch in `AbstractEditorInstance.sendDownUpKeyEvent` always
  attaches `KeyCharacterMap.VIRTUAL_KEYBOARD` source; the host editor remains
  authoritative for whether a synthetic event is honored.
- We do **not** currently inspect / filter incoming `dispatchKeyEvent` for
  cross-app origin (the platform handles this); a defense-in-depth pass on
  `metaState` validation is on the longer roadmap.

### 3.8 Auditability
- Apache-2.0 codebase, no obfuscation in debug builds, ProGuard rules visible
  in `app/proguard-rules.pro` for release builds.
- No closed-source binary blobs (e.g. `libjni_latinimegoogle.so`); the only
  native code is `lib/native` (Rust ICU helpers, source visible).
- F-Droid metadata + reproducible-build target are Now items (N6.2 / N6.3).

---

## 4. Known gaps (informational)

| Gap | Severity | Tracker |
|---|---|---|
| `FLAG_SECURE` not set on suggestion-strip popups (screen-recorders may capture them) | Medium | N7.2 follow-up |
| Reproducible-build verification not yet active on F-Droid | Medium | N6.3 |
| `allowMainThreadQueries()` on the personal dictionary Room DB — small UI lag risk on cold reads | Low | Risk-register entry, see ROADMAP §14 |
| Voice-command parser uses external FUTO Voice Input — that app has its own threat model and permissions | Low (FUTO is offline; user makes the trust decision when installing it) | Documented under "External components" in README |

---

## 5. Verification checklist (run on every release)

- [ ] `aapt dump permissions app-release.apk` shows only `VIBRATE`, `POST_NOTIFICATIONS` (and any new ones must be justified in the release notes + threat model).
- [ ] `:app:verifyNoInternetPermission` passes during CI.
- [ ] `PersonalDictionaryIsolationTest` passes during CI.
- [ ] `PersonalDictionaryEncryptionTest` passes during CI.
- [ ] APK signing fingerprint matches the value pinned in README (release-build only; debug builds use a per-developer keystore).
- [ ] No new `TODO()` runtime stubs introduced (lint check or grep).

---

## 6. Reporting a vulnerability

Open a GitHub issue or, for embargo-required disclosures, contact the maintainer
listed in `LICENSE` / repository profile. There is no separate security@ alias —
the project is small enough that public-issue triage is the operating mode.
