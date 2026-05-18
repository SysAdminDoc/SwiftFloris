# Release v1.8.95 — verifyDataExtractionRules build gate

Date: 2026-05-17

Follow-up F12 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

[`app/build.gradle.kts`](app/build.gradle.kts) — new
`verifyDataExtractionRules` task, wired to `preBuild` on every variant
(matches the [`verifyNoInternetPermission`](app/build.gradle.kts)
pattern).

The task fails the build if
[`app/src/main/res/xml/data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)
(the Android 12+ rules file shipped in v1.8.85) is missing OR if it
drops any of the load-bearing excludes:

- **SQLCipher personal-dictionary DB + sidecars** —
  `floris_user_dictionary`, `floris_user_dictionary.db`,
  `floris_user_dictionary.db-journal`,
  `floris_user_dictionary.db-wal`,
  `floris_user_dictionary.db-shm`.
- **Tink-wrapped passphrase prefs** — `floris_user_dictionary_key.xml`.
- **Clipboard history dir** — `clipboard_history`.

It also fails if either of the two required rule sections is missing
(`<cloud-backup>`, `<device-transfer>`).

## Why this matters

Android Lint already validates the file against the data-extraction-rules
schema, but a schema-valid edit can still drop an exclude. Concrete
failure mode without this gate:

1. A contributor "cleans up" the rules file (removes an exclude that
   "looks redundant" or migrates the file to a different schema).
2. Lint passes; tests pass; the APK ships.
3. On a real Android 12+ device, D2D transfer carries the SQLCipher
   DB AND its undecryptable Tink-wrapped passphrase pref to a new
   device — leaking PII ciphertext and bricking the user dictionary
   on the new device.

The substring check pinned in this task catches both an outright
exclude deletion AND a path-typo (e.g. `floris_user_dictionary.dbb`
that lints clean but matches no real path).

## Files touched

- `app/build.gradle.kts`
- `gradle.properties` — versionCode 1895 / versionName 1.8.95

## Verification

```powershell
./gradlew.bat :app:verifyDataExtractionRules
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

`verifyDataExtractionRules` should pass at HEAD; deliberately deleting
one of the listed `<exclude>` lines from
`data_extraction_rules.xml` and re-running should produce the
"missing exclude identifiers:" error and exit non-zero before
`assembleDebug` starts.
