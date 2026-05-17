# SwiftFloris v1.8.80

Released: 2026-05-17

## SQLCipher provider migration plan

This release closes the Tier-3 #36 planning slice for SQLCipher's future
crypto-provider risk.

### What changed

- Added `docs/SQLCIPHER_PROVIDER_MIGRATION.md`, a readiness plan for a possible
  future move from the stock SQLCipher Android Community AAR's LibTomCrypt
  provider to an OpenSSL-backed SQLCipher build.
- Updated `docs/SECURITY.md` with a SQLCipher provider watch section and a link
  to the new plan.
- Corrected the research security review: SQLCipher issue `#564` did announce
  LibTomCrypt / NSS deprecation, but Zetetic restored LibTomCrypt for Android
  Community builds in 4.14.0 and SQLCipher 4.16.0 still lists Android Community
  builds as LibTomCrypt-based.
- Captured concrete migration triggers, proof-of-concept steps, 16 KB page-size
  verification gates, `PersonalDictionaryEncryptionTest` expectations, and a
  rollback rule that keeps the current passphrase / Room schema untouched.

### Privacy and security

- No dependency or runtime provider changed in `:app`.
- No network permission, telemetry, account, or cloud path was added.
- The existing SQLCipher + Tink / AndroidKeystore encrypted-at-rest contract is
  unchanged.

### Verification

- `git diff --check`
- Manifest permission scan for banned network permissions
- Root JVM crash/replay tracked-file guard
- Gradle was not required for this docs-only planning slice; this VM still lacks
  `JAVA_HOME` / `java` for maintainer-host build verification.
