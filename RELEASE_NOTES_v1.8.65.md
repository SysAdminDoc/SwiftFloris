# SwiftFloris v1.8.65 — 2026-05-17

Phase A3 — encrypted personal-dictionary export/import wiring.

## Why ship this now

v1.8.54 added the portable `SFEXP1` envelope codec, but Settings still only
exposed plaintext personal-dictionary export. This release closes the local
OneDrive-replacement loop from `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`: users
can create a passphrase-encrypted dictionary file, carry it through Syncthing,
USB, or another user-chosen channel, and import it on another SwiftFloris
device without writing a plaintext export to user-visible storage.

## What changed

### Encrypted export action

Settings → Personal dictionary now has **Export encrypted** beside the existing
plaintext export. The flow asks for a passphrase + confirmation, opens Android's
create-document picker with `my-personal-dictionary.sfexp`, then writes the
AES-256-GCM/PBKDF2 `EncryptedDictionaryExport` envelope directly to the selected
URI.

The plaintext combined-list payload is built in memory, encrypted, and scrubbed
before the output stream is opened. No temporary plaintext file is created.

### Encrypted import detection

The import path now sniffs the selected file for the `SFEXP1` magic before
routing to the normal parser. Encrypted files get a passphrase dialog, bounded
read, decrypt, and then feed the decrypted bytes through `DictionaryImporter` +
`PersonalDictionaryImportBatch`, so the same "Added / Updated / Skipped" summary
and rollback affordance applies to encrypted imports.

Wrong passphrase and tampered ciphertext still collapse to the same user-facing
message, matching the cryptographic contract from v1.8.54.

### Shared SwiftFloris combined-list parser

`UserDictionaryCombinedListCodec` now owns the legacy semicolon key-value export
format. Plain export, encrypted export, legacy import, and the modular importer
all share the same parser/encoder. `DictionaryImporter` gained a
`DictionaryImportFormat.FLORIS` route for SwiftFloris/legacy Floris combined
lists.

## Tests

Added / updated pure unit coverage for:

- SwiftFloris combined-list parse/detect/import routing;
- headerless combined-list streams;
- values containing `=`;
- encrypted combined-list decrypt → importer round-trip.

## Versioning

- `gradle.properties`: `projectVersionCode=1865`,
  `projectVersionName=1.8.65`.

## Verification

Local non-Java checks:

```powershell
git diff --check
rg -n "settings__udm__encrypted|settings__udm__import_summary__format__floris" app/src/main -g "*.kt" -g "*.xml"
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

The no-network permission scan returned no matches. This VM still has no JDK /
Android SDK on the path; Gradle fails with `JAVA_HOME is not set and no 'java'
command could be found in your PATH`. Run before merge on the main Android build
host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.DictionaryImporterTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.EncryptedDictionaryExportTest
```

## What's next

The local-code SwiftKey-parity queue is now mostly gated by external inputs:
B5 needs captured local `swiftkey_trace.jsonl` fixture rows, and Phase E needs
real addon runtimes for Gemma / Bergamot / Rime. A1 still has marketing-side
work outside this local repository (Reddit thread + 2026-05-30 pinned release).
