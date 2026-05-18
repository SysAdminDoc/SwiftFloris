# Release v1.8.88 — Recover, don't crash, on undecryptable legacy passphrase

Date: 2026-05-17

Follow-up #3 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt#L91-L120)
— the v1.8.68 Tink migration path had a hard `error(...)` call that fired
when:

- The Tink-wrapped passphrase pref was missing or unreadable, AND
- A legacy AndroidX Security Crypto keyset was present in the prefs, AND
- `readLegacyEncryptedString` returned null (meaning the legacy value
  pref was missing OR the legacy decrypt itself failed silently).

Trigger scenarios for the third condition: Android Keystore master key
rotated by the system, prefs restored from a different device via auto-
backup of an old install, the legacy keyset got corrupted, or the
SharedPreferences XML was edited / partially restored. Real-world impact
is small (the user-triggered BackupScreen does not include this prefs
file, and the new `data_extraction_rules.xml` shipped in v1.8.85 excludes
it from D2D / cloud transfer), but the failure mode was severe: IME
hard-crashes on startup, settings unreachable.

This release replaces the `error(...)` with a recovery path:

1. Log a warning at the `Log.w` level explaining the state.
2. Clear the two AndroidX legacy keyset pref keys
   (`__androidx_security_crypto_encrypted_prefs_key_keyset__` /
   `__androidx_security_crypto_encrypted_prefs_value_keyset__`) — they
   are now unreadable garbage.
3. Fall through to the fresh-passphrase generation path that follows.

The user loses access to any personal-dictionary words encrypted under
the now-unreadable key, but the IME starts cleanly with an empty
dictionary. Users with a user-triggered backup can re-import.

## Why this is the right tradeoff

The alternative — recovering the old encrypted DB — is impossible because
we no longer have the decryption key. The remaining options are:

- **Crash (previous behaviour).** Leaves the user with a non-functional
  IME and no recovery path inside the app. Worst outcome.
- **Disable the dictionary silently.** The next dictionary-write would
  fail; UX is opaque.
- **Regenerate (this release).** User sees an empty dictionary, but
  typing works. Diagnostic logged.

Regeneration is the only option that keeps the IME usable. The lost-data
risk is real but the previous behaviour also lost the data — it just
also crashed.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt`
- `gradle.properties` — versionCode 1888 / versionName 1.8.88

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction (requires an Android shell):
- `adb shell run-as dev.patrickgold.florisboard.debug` then manually
  edit `shared_prefs/floris_user_dictionary_key.xml` to inject a value
  for `__androidx_security_crypto_encrypted_prefs_key_keyset__` without
  the matching encrypted-prefs value pref. Restart the IME.
- Pre-fix: IME crashes during dictionary init with the `error(...)`
  message.
- Post-fix: IME starts; logcat shows the warning; user dictionary is
  empty. Type a few words, confirm they get learned (new passphrase
  works).
