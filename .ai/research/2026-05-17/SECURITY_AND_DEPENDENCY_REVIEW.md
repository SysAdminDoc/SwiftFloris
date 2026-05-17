# Security & Dependency Review — 2026-05-17

Companion to the existing `docs/SECURITY.md` (which documents the
OSV-Scanner + dependency-review pipeline). This review compares **every
pinned version** in `gradle/libs.versions.toml` and `gradle/tools.versions.toml`
against the latest stable as of 2026-05-17, plus license-compatibility,
CVE, and policy-cutoff items.

Source data: dep-research Agent run on 2026-05-17, corrected by the
same-day fifth pass in [FIFTH_PASS_FINDINGS.md](FIFTH_PASS_FINDINGS.md)
(see [SOURCE_REGISTER.md §2.7-2.8](SOURCE_REGISTER.md)).

**Continuation note:** v1.8.68 shipped the AndroidX Security Crypto
migration. `androidx.security:security-crypto:1.1.0-alpha06` is no longer
declared in `:app`; `com.google.crypto.tink:tink-android:1.21.0` is now
the runtime crypto dependency for local encrypted-preference wrapping.
v1.8.69 then shipped Bump-batch A: coroutines 1.11.0, KSP 2.3.8,
ZXing 3.5.4, and AboutLibraries stable 14.2.0. v1.8.71 shipped
Bump-batch B: Roborazzi 1.60.0 and Robolectric 4.16.1.

---

## 1. Version delta table

| Pin | Current pin | Latest stable (2026-05-17) | Delta | Action |
|---|---|---|---|---|
| **AGP** | 9.2.1 | **9.2.1 stable** (`9.3.0-alpha05` preview skipped) | Current on stable line | ✅ shipped v1.8.74 after R8 keepattributes audit |
| **Kotlin** | 2.3.21 | 2.3.21 (2.4.0-RC out) | Current | Keep |
| KSP | 2.3.8 | **2.3.8** | Current | ✅ shipped v1.8.69 |
| **androidx-activity** | **1.13.0** | **1.13.0 stable** | Current | Keep |
| **compose-bom** | 2026.05.00 | **2026.05.00** | Current | ✅ shipped v1.8.74 |
| androidx-core | 1.18.0 | 1.18.0 | Current | Keep |
| androidx-emoji2 | 1.6.0 | 1.6.0 | Current | Keep (Emoji 17 still gated on 1.7) |
| androidx-navigation | 2.9.7 | 2.9.7 (navigation3 1.1.0 also stable) | Current | Consider navigation3 evaluation |
| androidx-profileinstaller | 1.4.1 | 1.4.1 | Current | Keep |
| androidx-room | 2.8.4 | 2.8.4 (3.0.0-alpha exists) | Current | Keep (3.0 alpha only) |
| androidx-sqlite | 2.6.2 | 2.6.2 | Current | Keep |
| androidx-window | 1.5.1 | 1.5.1 | Current | Keep |
| coil | 3.4.0 | 3.4.0 | Current | Keep |
| **kotlinx-coroutines** | 1.11.0 | **1.11.0** | Current | ✅ shipped v1.8.69 |
| kotlinx-serialization-json | 1.11.0 | 1.11.0 | Current | Keep |
| material-kolor | 4.1.1 | 4.1.1 | Current | Keep |
| **mikepenz-aboutlibraries** | 14.2.0 | **14.2.0 stable** (`15.0.0-b01` beta exists) | Current | ✅ shipped v1.8.69 |
| jetpref | 0.3.0 | 0.3.x | Current | Keep |
| sqlcipher-android | 4.16.0 | 4.16.0 (released 2026-05-12) | Current | Keep — provider watch / OpenSSL proof-of-concept plan shipped in v1.8.80 (§3) |
| **zxing-core** | 3.5.4 | **3.5.4** | Current | ✅ shipped v1.8.69 |
| **roborazzi** | 1.60.0 | **1.60.0** | Current | ✅ shipped v1.8.71 |
| **robolectric** | 4.16.1 | **4.16.1** | Current | ✅ shipped v1.8.71 |
| kotest | 6.1.11 | 6.1.11 | Current | Keep |
| androidx-benchmark | 1.4.1 | 1.4.1 | Current | Keep |
| **Tink Android** | **1.21.0** | **1.21.0** | **Current** | **Keep** — replaced deprecated AndroidX Security Crypto in v1.8.68 |
| Build Tools | 36.0.0 | 36.0.0 | Current | Keep |
| NDK | 29.0.14206865 | 29.x | Current | Keep — 16 KB alignment automatic since AGP 8.5.1 + NDK r28 |
| JDK | 17 | 17 (21 LTS available) | Current | Keep until Compose tooling forces 21 |
| Gradle wrapper | 9.4.1 | 9.4.x | Current | Keep |

## 2. Critical issue: `androidx-security-crypto:1.1.0-alpha06` is deprecated API surface

Fifth-pass verification corrected the first-pass wording: stable
`androidx.security:security-crypto:1.1.0` exists. The material issue is
that AndroidX release notes deprecate the APIs in favor of platform APIs
and direct Android Keystore use, while SwiftFloris was still pinned to
older `1.1.0-alpha06`. `EncryptedSharedPreferences` also carries
key-rotation concerns for this use case.

SwiftFloris previously used this library in [app/build.gradle.kts](../../../app/build.gradle.kts)
inline: `implementation("androidx.security:security-crypto:1.1.0-alpha06")`.

The library was used to protect the SQLCipher passphrase via
`EncryptedSharedPreferences` + `MasterKey` (per ROADMAP §6 N7.4 and the
`PersonalDictionaryEncryptionTest` contract).

**Status:** ✅ shipped in v1.8.68. The replacement is
`TinkStringPreferenceCrypto`, backed by `com.google.crypto.tink:tink-android:1.21.0`
and direct AndroidKeystore AES-256-GCM keys. The implementation also migrated
legacy clipboard-history encrypted preferences because that path used the same
deprecated AndroidX Security Crypto dependency.

### Implemented migration (one-shot, single PR)

1. Replace `androidx-security-crypto` with **Google Tink**
   (`com.google.crypto.tink:tink-android`, Apache-2.0; target 1.21.0 as
   of the fifth pass).
   Use `Aead` (single-key) to wrap the SQLCipher passphrase; persist the
   wrapped passphrase in plain `SharedPreferences`; protect the wrapping
   key via `AndroidKeystoreV1` KMS client (built into Tink).
2. Preserve the existing on-disk passphrase format by detecting it and
   migrating once (similar to the existing plaintext-DB → encrypted-DB
   detection in v1.7.4).
3. Update `PersonalDictionaryEncryptionTest` to pin the new contract:
   wrap path, KMS reference, rotation strategy.

Effort: ~1 day. Risk: low if guarded by the existing test contract and
the post-migration verification path.

Alternative: drop in `dev.spght:encryptedprefs` (maintained fork by
Ed Holloway-George). Lower migration friction but ties SwiftFloris to a
single maintainer's continuity.

References:
- https://developer.android.com/jetpack/androidx/releases/security
- https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a
- https://github.com/tink-crypto/tink-java

## 3. SQLCipher 4.16.0 — provider migration watch

`sqlcipher-android:4.16.0` (released 2026-05-12 by Zetetic) is current. The
earlier provider-risk finding needs nuance after the same-day implementation
follow-up: SQLCipher issue `#564` did announce LibTomCrypt / NSS provider
deprecation, but SQLCipher 4.14.0 restored LibTomCrypt for Community Edition
Android builds after community feedback, and SQLCipher 4.16.0 still lists
Android Community non-FIPS packages as LibTomCrypt-based.

The `sqlcipher-android` README documents the supported source-build escape
hatch: provide ABI-specific OpenSSL `libcrypto.a` files and build with
`SQLCIPHER_CFLAGS` containing `-DSQLCIPHER_CRYPTO_OPENSSL` and
`-DSQLITE_HAS_CODEC`. Zetetic also documents that the modern
`sqlcipher-android` library has supported Android 16 KB page sizes since 4.6.1;
SwiftFloris is already on AGP 9.2.1 and NDK 29, matching Android's default
16 KB-compatible tooling guidance.

**Status:** ✅ planning slice shipped in v1.8.80. The production dependency
stays on the Maven Central Community AAR for now. The migration triggers,
OpenSSL proof-of-concept steps, verification matrix, and rollback plan live in
[docs/SQLCIPHER_PROVIDER_MIGRATION.md](../../../docs/SQLCIPHER_PROVIDER_MIGRATION.md).

References:
- https://github.com/sqlcipher/sqlcipher/issues/564
- https://www.zetetic.net/blog/2026/03/17/sqlcipher-4.14.0-release/
- https://www.zetetic.net/blog/2026/05/12/sqlcipher-4.16.0-release/
- https://github.com/sqlcipher/sqlcipher-android
- https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/
- https://developer.android.com/guide/practices/page-sizes

## 4. License compatibility verification

ROADMAP §1 hard rule: GPL / AGPL / LGPL / FUTO Source-First cannot be
linked into `:app`. They may ship as a clearly-isolated addon under their
own license.

Re-verification against the ROADMAP's claimed targets:

| Library | License | `:app` compatible? | ROADMAP position |
|---|---|---|---|
| **KenLM** (referenced in §7 Next-3.1) | **LGPL** | **❌ NO** | **MUST move to addon or reject** |
| Bergamot translator | MPL-2.0 | ✅ Yes (MPL Secondary License) | §8 L2 addon |
| librime (main repo) | BSD-3-Clause | ✅ Yes | §8 L3 addon (kept as addon for runtime size, not license) |
| librime-legacy | GPL | ❌ NO | Reject |
| Varnam (libvarnam) | MPL | ✅ Yes | §8 L5 addon |
| varnam-fcitx5 | GPL | ❌ NO | Reject |
| ML Kit Digital Ink SDK | ML Kit Terms (not OSS) | ⚠️ App-Store-distribution dependent — F-Droid friction | §7 Next-4.2 already isolates in `addons/handwriting-mlkit/` |
| CleverKeys glide model | GPL-3.0 | ❌ NO direct linking | Architectural reference only |
| SCOWL | BSD-like | ✅ Yes (currently shipped) | §1 attribution |
| LDNOOBW profanity list | CC-BY-4.0 | ✅ Yes (currently shipped) | §1 attribution |

### KenLM is the load-bearing finding

ROADMAP §7 Next-3.1 (lines 511 ff.) plans a header reader + JNI bring-up
against KenLM. KenLM is LGPL-2.1+. Per §1 of the same roadmap, **LGPL
cannot ship in `:app`**.

This means either:

- **(a)** KenLM stays as an external addon APK (like Bergamot, librime),
  which the in-`:app` `KenLmBinaryHeader` + `KenLmBinaryReader.readHeader`
  facade already supports. The in-tree parser parses only the binary
  header format, which is not the LGPL'd library — that's fine.
- **(b)** Switch to SentencePiece (Apache-2.0) for in-`:app` n-gram-like
  scoring. Less proven against KenLM-flavored corpora but license-clean.

**Resolution:** keep the in-`:app` header-only parser; pin the JNI step
to an addon. This appears to already be the implicit design (the v1.8.x
KenLM work is parser-only), but the ROADMAP text should be updated to
make the license boundary explicit. Captured in
[ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md).

## 5. CVE / advisory status

No direct CVEs against any pinned library were identified for 2025-01-01
through 2026-05-17.

Adjacent advisories noted:

- **SQLite CVE-2025-29087** — does **not** affect SQLCipher (Zetetic
  forum post confirms). No action.
- **Android Security Bulletins through April 2026** — OS-level patches,
  no direct library impact. No action.
- **zxing-core 3.5.4** — small email-validation hardening; non-blocking
  bump.
- **androidx-security-crypto** deprecation is a hardening issue, not a
  CVE. See §2.

## 6. F-Droid Reproducible Builds verified-tier

F-Droid publishes a per-app "Reproducibility Status" (✔️/💔) since 2025-05.
~21 % of main-repo apps are dev-signed reproducible. NLnet is funding a
2025-2026 rebuild-farm overhaul.

**SwiftFloris status (per docs/REPRODUCIBLE_BUILDS.md):** toolchain pins
are in place; F-Droid `fdroiddata` PR has not been submitted. Per
ROADMAP §6 N6.3 the metadata submission is the open external step.

**Recommended additional CI step:** local "build twice, compare APK
checksums" job in `android.yml` so SwiftFloris can detect non-determinism
**before** F-Droid's rebuilder does. Effort: low. Source:
https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html

## 7. EU AI Act Article 50 — 2 Aug 2026 cutoff

EU AI Act Article 50 transparency duties apply from **2 August 2026** (~10
weeks from this research date). Any AI-assisted feature that interacts
directly with users must inform the user at first interaction.

For SwiftFloris this affects:

- Next-word prediction strip (heuristic ranker today; neural ranker
  post-L1)
- Glide-typing classifier (`StatisticalGlideTypingClassifier`)
- On-device translation (L2 addon)
- Voice (FUTO handoff or Vosk fallback)
- Smart-compose ghost text (post-L1)
- Tone / rewrite router (post-L1)

**Recommended response:** add a first-run "AI features in this keyboard"
explainer surface that:

- Lists each AI/ML surface in the IME.
- States plainly: "All processing happens on this device. No data leaves
  the device. No vendor accounts."
- Links to `docs/THREAT_MODEL.md` and `PROJECT_CONTEXT.md`.
- Is shown once on first launch; can be re-opened from Settings → About.

Effort: low. Surface lives next to the existing first-run setup flow
(`app/setup/`). Captured as a new item in
[ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md).

## 8. Apple App Store guideline 5.1.2(i) — third-party AI personal data

Apple's November 2025 App Store guidelines clamp down on apps sharing
personal data with third-party AI services. Not directly applicable to
SwiftFloris (Android-only, no Apple touchpoint) but **informs the framing
of the README "no vendor cloud, no account" promise** for users coming
from cross-platform apps.

No action required; cited for context.

## 9. Recommended dependency bumps (PR-sized batches)

### Bump-batch A (low risk, shipped v1.8.69)

1. ✅ **kotlinx-coroutines** 1.10.2 → **1.11.0**
2. ✅ **KSP** 2.3.5 → **2.3.8**
3. ✅ **zxing-core** 3.5.3 → **3.5.4**
4. ✅ **aboutlibraries** 14.0.1 → **14.2.0** (stable; skipped `15.0.0-b01` beta)

**Acceptance criteria:** `:app:testDebugUnitTest`, `:app:lintDebug`,
`:app:assembleDebug` green; 16KB alignment check still passes.

### Bump-batch B (visual-regression infrastructure, shipped v1.8.71)

1. ✅ **roborazzi** 1.55.0 → **1.60.0**
2. ✅ **robolectric** 4.14.1 → **4.16.1**

**Acceptance criteria:** existing Roborazzi snapshot tests still pass;
`:app:recordRoborazziDebug` runs cleanly; baseline-PNG capture can resume.
Local Gradle verification is blocked on this VM by missing Java, so the
maintainer build host must run the visual/test suite before publishing.

### Bump-batch C (build toolchain)

1. ✅ **AGP** 9.0.0 → **9.2.1** shipped v1.8.74
2. ✅ **Compose BOM** 2026.03.01 → **2026.05.00** shipped v1.8.74

**Acceptance criteria:** Bump-batch B must land first. ProGuard /
`proguard-rules.pro` audit for R8 keep-attributes / annotation stripping
behavior before the AGP 9.2 merge.

### Bump-batch D (security migration)

1. ✅ **v1.8.68 shipped:** **androidx-security-crypto** →
   **Google Tink + direct AndroidKeystore wrapping** (replace, do not bump)

**Acceptance criteria:** `PersonalDictionaryEncryptionTest` rewritten to
the new contract; migration path detects + rewraps the old shape;
existing installs upgrade cleanly.

## 10. Other recommended hardening

- **Reproducible-build verification job** in CI (build twice, compare).
- **Local EU AI Act transparency surface** (see §7).
- **R8 rules audit** before AGP 9.2.x bump.
- **OpenSSL/BoringSSL SQLCipher provider plan** (see §3).
- **Local lint baseline refresh** (count is from 2026-05-05; drifted).
