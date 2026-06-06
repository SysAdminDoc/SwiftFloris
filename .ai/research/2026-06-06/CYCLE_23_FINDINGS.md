# Cycle 23 Findings - 2026-06-06

## Cycle

`sync-key-lifecycle-recheck-2026-06-06`

## Scope

Resumed after Cycle 22. This pass traced the Sync pairing payload generator,
sealed-box cryptography APIs, Sync preferences, existing secret-storage helpers,
backup/data-extraction rules, and official Android key-storage/backup guidance.

## Files and sources reviewed

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairingPayloadGenerator.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/SealedBoxCrypto.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/prefs/SyncPrefs.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/FlorisPreferenceModelImpl.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/sync/SealedBoxCryptoTest.kt`
- Android Keystore:
  https://developer.android.com/privacy-and-security/keystore
- Android Auto Backup:
  https://developer.android.com/identity/data/autobackup

## Findings

- `SyncSettingsScreen.kt:218-234` persists only cluster id and device id before
  generating the QR payload. It does not pass a caller-owned keypair or persist
  any private-key material after generation.
- `PairingPayloadGenerator.kt:27-43` defaults `keyPair` to
  `SealedBoxCrypto.generateKeyPair()` and serializes only the public half into
  `pubkeyHex`.
- `SealedBoxCrypto.kt:86-97` documents that the caller owns the private half for
  long-lived recipient keys. `SealedBoxCrypto.kt:156-170` requires the recipient
  `KeyPair` to decrypt a sealed envelope.
- `SyncPrefs.kt:78-93` stores channel/cluster/device/paired-device/manual-export
  state only. There is no sync identity key preference or secret owner.
- A production-code grep for `generateKeyPair`, `KeyPair`, `PrivateKey`, and
  `pubkeyHex` found no main-code storage owner beyond `PairingPayloadGenerator`
  and `SealedBoxCrypto`.
- `FlorisUserDictionaryEncryption.kt:65-133` and
  `TinkStringPreferenceCrypto.kt` already provide a local precedent for wrapping
  app secrets with a Tink AEAD protected by an AndroidKeystore alias.
- `AndroidManifest.xml:60-64` enables backup and points at the project's backup
  rules. Those rules include `jetpref_datastore`; a future plaintext sync secret
  placed in normal preferences would inherit backup scope unless a separate
  excluded secret store is used.

## External-source effect

- Android Keystore docs describe storing cryptographic keys so key material is
  non-exportable and protected from extraction from the app process/device.
- Android Auto Backup docs document include/exclude rules and call out
  device-specific generated identifiers as typical backup exclusions. That
  supports excluding sync identity secrets while keeping non-secret channel
  metadata portable.

## Roadmap effect

Added R23-1 to `ROADMAP.md`: persist and backup-scope the Sync long-term X25519
identity before transport activation.

## Acceptance shape

- First QR generation creates or loads one stable local X25519 identity.
- Public key/fingerprint remains stable across repeated QR generations and
  process restarts until explicit Sync identity reset.
- Private-key material is wrapped with a sync-specific AndroidKeystore/Tink
  alias or an explicitly documented AndroidKeystore-backed X25519 path.
- The private key is never stored in plaintext JetPref.
- Backup/data-extraction rules exclude the sync secret store.
- Missing or tampered key material fails closed with reset/re-pair guidance.
- Tests prove envelopes sealed to the QR public key decrypt after key reload.

## Duplicate avoidance

- R22-1 remains the user confirmation/fingerprint review before saving remote
  public keys.
- R23-1 is the local identity lifecycle required so this device can decrypt
  envelopes addressed to the public key it advertises.
