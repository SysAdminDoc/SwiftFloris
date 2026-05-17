# SwiftFloris v1.8.68 — 2026-05-17

N7.6 — Tink / AndroidKeystore migration for encrypted local stores.

## Why ship this now

The roadmap's fifth-pass research found that AndroidX Security Crypto did ship a
stable `1.1.0`, but its preference APIs are deprecated. SwiftFloris was still
pinned to `androidx.security:security-crypto:1.1.0-alpha06` for encrypted local
preference storage, so the app needed to move to the long-term Tink /
AndroidKeystore path before more release hardening lands.

## What changed

### Shared Tink preference wrapper

Added `TinkStringPreferenceCrypto`, a small shared helper that:

1. Wraps bytes or strings with Tink `Aead`.
2. Stores the wrapping key in AndroidKeystore via
   `AndroidKeystore.generateNewAes256GcmKey`.
3. Binds ciphertext to `prefsFile:key` associated data.
4. Refuses to create a replacement Keystore key while reading existing
   ciphertext; writes are the only path that create a missing wrapper key.
5. Commits ciphertext synchronously so encryption metadata is durable before the
   caller proceeds.
6. Reads legacy AndroidX `EncryptedSharedPreferences` string payloads through
   Tink's `AndroidKeysetManager` only for one-shot migration.

### Personal dictionary key migration

The SQLCipher personal-dictionary passphrase now stores under
`sqlcipher_passphrase_tink_v1`, wrapped by the shared Tink helper. Existing
`sqlcipher_passphrase_v1` AndroidX encrypted-preference payloads are migrated
once if their legacy keysets are still readable.

If legacy keysets exist but the passphrase cannot be recovered, SwiftFloris
fails closed instead of silently generating a new passphrase that would orphan an
existing encrypted dictionary.

### Clipboard history migration

The legacy in-process clipboard-history store also moved off AndroidX Security
Crypto. `ClipboardHistoryManager` now wraps `clipboard_history_tink_v1` with
Tink / AndroidKeystore and attempts a one-shot migration from the old
`clipboard_history` encrypted-preference payload. Keystore failures still fall
back to non-persistent in-memory history so the IME can start.

### Dependency update

Removed:

- `androidx.security:security-crypto:1.1.0-alpha06`

Added:

- `com.google.crypto.tink:tink-android:1.21.0`

## Versioning

- `gradle.properties`: `projectVersionCode=1868`,
  `projectVersionName=1.8.68`.

## Verification

Local checks performed on this Windows VM:

```powershell
git diff --check
rg -n "androidx\\.security\\.crypto|security-crypto" app/src/main app/build.gradle.kts gradle/libs.versions.toml
rg -n "TinkStringPreferenceCrypto|tink-android|AndroidKeystore|AndroidKeysetManager" app/src/main app/src/test app/build.gradle.kts gradle/libs.versions.toml
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

Gradle verification is still blocked on this VM because Java is not configured:
`JAVA_HOME is not set and no 'java' command could be found in your PATH`.
Run before merge on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryEncryptionTest
.\gradlew.bat :app:assembleRelease
```
