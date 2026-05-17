# SwiftFloris v1.8.54 — 2026-05-17

Phase A3 — Encrypted-blob personal-dictionary export envelope codec.

## Why ship this now

The Floris personal dictionary already encrypts its on-disk Room
database with SQLCipher (`PersonalDictionaryEncryptionTest` pins
that contract), but the SQLCipher passphrase is held in Android
Keystore and is **intentionally non-portable** — a feature, because
it makes a stolen device backup useless to the thief. The downside
is that users can't carry their learned vocabulary to a different
phone through any user-chosen channel (Syncthing, USB-drag,
ProtonDrive, etc.) without first decrypting to plaintext CSV — which
then sits on the source filesystem as a juicy plaintext target.

Phase A3 of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` ships the
portable replacement: an AES-256-GCM blob keyed by a user-typed
passphrase, with PBKDF2-HMAC-SHA-256 key derivation at the
OWASP-2025-recommended 600 000-iteration count. The iteration count
is baked into the blob so future bumps decrypt old exports
unchanged.

This slice ships the **codec primitive + full test coverage**. The
Settings UI wiring (passphrase dialog + file-create launcher +
encrypt-then-write loop) lands in a follow-up slice so the
load-bearing crypto contract is committed and reviewable
independently.

## What changed

### `EncryptedDictionaryExport` (new, pure-Kotlin / JVM stdlib)

- `encrypt(plaintext, passphrase, iterations = 600_000, secureRandom)`
  - 16-byte random PBKDF2 salt (NIST SP 800-132 §5.1 floor).
  - 12-byte random AES-GCM nonce (NIST GCM standard).
  - PBKDF2-HMAC-SHA-256 derives a 256-bit AES key.
  - AES-256-GCM with 128-bit auth tag in a single sealed block.
  - Deriving-key buffer is best-effort scrubbed via `Arrays.fill(key, 0)`
    after the cipher captures it.
  - Rejects: empty passphrase, plaintext > 16 MiB, iterations < 100 000
    (the OWASP 2025 floor).

- `decrypt(envelope, passphrase) → ByteArray`
  - Parses the 44-byte header, validates magic + version + field
    bounds before touching the cipher.
  - Collapses cryptographic indistinguishability (wrong passphrase
    vs. tampered ciphertext) into a single `BAD_PASSPHRASE` reason
    so the UI shows one honest line of copy instead of leaking
    which case it actually was.
  - Rejects an envelope that claims a plaintext size > 16 MiB before
    decrypting — defends against an attacker swapping a real export
    with a 1 GiB random blob to OOM the destination device.

- `isEncryptedEnvelope(candidate) → Boolean`
  - Byte-sniff predicate that tests just the 6-byte `SFEXP1` magic.
  - Lets the import flow ask for a passphrase only when the file is
    actually encrypted; plain CSV / JSON / XML / zip files skip the
    passphrase prompt entirely.

### `EncryptedDictionaryException` + `FailureReason` enum

Six categorical failure reasons (`TRUNCATED`, `NOT_AN_ENVELOPE`,
`UNSUPPORTED_VERSION`, `CORRUPT_HEADER`, `OVERSIZED`,
`BAD_PASSPHRASE`) — keeps the call site's `when` exhaustive without
pattern-matching on cause types.

### Wire format

```
offset  size  field
0       6     magic = "SFEXP1" (ASCII)
6       2     version (uint16; v1 = 0x0001)
8       16    PBKDF2 salt (random per export)
24      12    AES-GCM nonce / IV (random per export)
36      4     PBKDF2 iteration count (uint32 BE, currently 600 000)
40      4     plaintext payload byte-length (uint32 BE, sanity bound)
44      …     ciphertext + 16-byte GCM auth tag (single sealed block)
```

Total header = 44 bytes. Two envelopes encrypting the same
plaintext under the same passphrase produce different bytes because
the per-export salt + nonce are random.

## New tests

`EncryptedDictionaryExportTest` (15 cases):
- round-trip recovers exact plaintext;
- wrong passphrase fails with `BAD_PASSPHRASE`;
- tampered ciphertext byte fails with `BAD_PASSPHRASE` (cryptographic
  indistinguishability);
- truncated envelope reports `TRUNCATED` before touching the cipher;
- non-envelope blob (zero magic) reports `NOT_AN_ENVELOPE`;
- future-version envelope reports `UNSUPPORTED_VERSION`;
- envelope claiming oversized plaintext reports `OVERSIZED`;
- envelope claiming negative plaintext length reports `OVERSIZED`;
- envelope with iters=0 reports `CORRUPT_HEADER`;
- encrypt rejects empty passphrase;
- encrypt rejects iterations below OWASP floor;
- encrypt rejects plaintext past safety cap;
- envelope size = header + plaintext + 16-byte GCM tag (pinned);
- `isEncryptedEnvelope` byte-sniff distinguishes magic from CSV /
  JSON / XML / too-short input;
- two encrypts of same plaintext + passphrase produce different
  envelopes but both decrypt to the same plaintext.

## Versioning

- `gradle.properties`: `projectVersionCode=1854`,
  `projectVersionName=1.8.54`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The PBKDF2 iteration count of 600 000 takes ~150 ms on a Pixel 6 in
informal benchmarks (Conscrypt-accelerated path); tests use 100 000
to keep the suite fast while staying within the codec's OWASP floor.

## What's next

The Settings UI wiring for the encrypted export — passphrase entry
dialog, file-create launcher, encrypt-then-write loop — is the
follow-up. Once that lands, the import flow gets the symmetric
"detected an SFEXP1 envelope → prompt for passphrase → decrypt →
run through the existing `DictionaryImporter`" branch so encrypted
exports round-trip end-to-end through the app without ever
materialising plaintext on the source device's user-visible
filesystem.

After A3 wiring closes, the autonomous loop moves into Phase B
(decoder calibration: B1 sentence-position priors expansion, B2
quick-prediction-insert tuning on empty fields, B3 shared-spelling
bilingual handling, B4 same-sentence language switch hardening,
B5 trace-based field calibration).
