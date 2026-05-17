# SQLCipher Provider Migration Plan

Last reviewed: 2026-05-17

SwiftFloris currently uses `net.zetetic:sqlcipher-android:4.16.0` for the
encrypted personal dictionary. This document is the readiness plan for the
possible future move from the stock Android Community AAR's LibTomCrypt provider
to an OpenSSL-backed SQLCipher build.

## Current State

- The app depends on `sqlcipher-android = "4.16.0"` in
  `gradle/libs.versions.toml`.
- `FlorisUserDictionaryEncryption` loads `libsqlcipher` and opens Room through
  `SupportOpenHelperFactory`.
- The SQLCipher passphrase is wrapped locally with Tink Android and an
  AndroidKeystore-held AES-256-GCM key.
- No network, telemetry, account, or cloud backup path is involved.

## Upstream Evidence

- SQLCipher issue `#564` announced a plan to deprecate and remove the bundled
  LibTomCrypt and NSS providers in a future release, while recommending OpenSSL
  or CommonCrypto as the default supported alternatives for source builds.
- SQLCipher 4.14.0 later restored LibTomCrypt for Community Edition Android
  builds and made it the Android Community provider again after community
  feedback.
- SQLCipher 4.16.0, released 2026-05-12, still lists Android Community
  non-FIPS builds as LibTomCrypt-based, while commercial / enterprise
  non-FIPS packages use OpenSSL 3.5.6 LTS on other platforms and Enterprise
  FIPS is based on the SQLCipher Cryptographic Module.
- The `sqlcipher-android` README documents that the default Android provider is
  LibTomCrypt and that an OpenSSL-linked build is possible by supplying
  ABI-specific `libcrypto.a` files and `SQLCIPHER_CFLAGS` including
  `-DSQLCIPHER_CRYPTO_OPENSSL` and `-DSQLITE_HAS_CODEC`.
- Zetetic documents that the modern `sqlcipher-android` library has supported
  Android 16 KB page sizes since 4.6.1.
- Android's 16 KB page-size guidance requires native libraries and SDKs to be
  rebuilt / verified for 16 KB page devices; AGP 8.5.1+ and NDK r28+ are the
  default-compatible build path. SwiftFloris is already on AGP 9.2.1 and NDK 29.

## Decision

Do **not** replace the Maven Central Community AAR in `:app` today.

The provider-removal risk is real enough to track, but the immediate trigger is
not active: the current SQLCipher Android Community release line still ships
LibTomCrypt, and the dependency is current. A custom OpenSSL provider build
should be evaluated before any upstream removal, provider-specific CVE, or
distribution/compliance requirement forces it.

## Migration Triggers

Start the OpenSSL-backed proof of concept when any one of these happens:

- Zetetic announces a dated Android Community provider removal.
- A HIGH / CRITICAL OSV, NVD, or upstream advisory affects the LibTomCrypt
  provider used by `sqlcipher-android`.
- The Maven Central AAR stops supporting Android 16 KB page sizes.
- F-Droid, a downstream distributor, or a security review requires OpenSSL /
  FIPS-provider documentation instead of the Community AAR.
- SwiftFloris adds another native SQLCipher-dependent component that justifies
  owning a custom AAR build pipeline.

## Proof-of-Concept Plan

1. Fork or vendor a build-only copy of `sqlcipher-android` outside `:app`.
2. Build OpenSSL per Android ABI and place the ABI-specific `libcrypto.a`
   libraries and headers where `sqlcipher-android` expects them.
3. Build the AAR with:

   ```bash
   SQLCIPHER_CFLAGS="-DSQLCIPHER_CRYPTO_OPENSSL -DSQLITE_HAS_CODEC" ./gradlew assembleRelease
   ```

4. Keep the proof-of-concept AAR outside the production dependency graph until
   the verification matrix below passes.
5. Compare the custom AAR against the Maven Central AAR for ABI coverage,
   native library size, license notices, symbol stripping, 16 KB alignment, and
   OSV/dependency-review visibility.

## Required Verification Before Switching

- Existing encrypted personal dictionaries open without rekeying.
- Plaintext-to-encrypted migration still detects the SQLite header and stages /
  restores exactly as before.
- New encrypted dictionaries are not readable with the wrong passphrase.
- `PersonalDictionaryEncryptionTest` passes unchanged or gains explicit provider
  assertions.
- `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and
  `:app:verifyRoborazziDebug` pass on a Java / Android SDK host.
- `zipalign -c -P 16 -v 4 <apk>` passes for every release APK.
- A 16 KB page-size emulator or device reports `adb shell getconf PAGE_SIZE`
  as `16384` and can open, write, restart, and reopen the encrypted dictionary.
- Release notes and `NOTICE` / `LICENSES/` record the OpenSSL build inputs.

## Rollback Plan

The rollback target is the stock `net.zetetic:sqlcipher-android` AAR. Do not
change the SQLCipher passphrase wrapping format or Room schema as part of the
provider proof of concept. If the provider swap regresses device behavior,
restore the Maven Central dependency and leave the encrypted database files
unchanged.

## Non-Goals

- Do not adopt a commercial / enterprise SQLCipher package unless the maintainer
  explicitly accepts the licensing and redistribution terms.
- Do not switch to BoringSSL unless upstream SQLCipher Android documents a
  supported BoringSSL build path or a local proof of concept demonstrates the
  same compatibility and maintenance story as OpenSSL.
- Do not relax the no-network, no-telemetry, or encrypted-at-rest invariants to
  satisfy a provider migration.
