# SwiftFloris Security

This document explains SwiftFloris's release-time security posture, the dependency-scanning policy, and how the
"checked on date X" status note for each release is produced.

## At a glance

- SwiftFloris does not request the `INTERNET` permission. The local release evidence command verifies this via
  `:app:verifyNoInternetPermission`.
- Local sensitive stores use Android platform keys: the SQLCipher personal-dictionary passphrase is wrapped with
  Tink `Aead` and AndroidKeystore-held AES-256-GCM keys.
- The Gradle dependency tree is scanned for known CVEs during local release evidence collection.
- Every GitHub Release body should carry an "OSV scan" appendix from `build/release-evidence/<timestamp>/` that
  records the scanner version, the date the scan ran, and either "0 known vulnerabilities" or the count + identifiers.

A clean OSV scan does **not** imply forever-immunity. CVEs are published continuously, and a clean check on release
day will not catch a vulnerability disclosed the day after. The dated stamp lets users reason about how stale the
security check is for the version they have installed.

## Supported scope

Security reports are in scope when they affect the SwiftFloris app, release artifacts, update channel, build
pipeline, bundled data, addon trust path, local storage, backup/restore flow, clipboard handling, IME field-sensitivity
handling, or any other path that can expose typed text, personal dictionaries, clipboard content, local files, signing
trust, or device-local settings.

Reports about upstream dependencies are still useful, but the primary fix usually belongs upstream. File those with
the affected Android, AndroidX, Compose, Kotlin, Room, SQLCipher, Tink, or Gradle project first, then
link the advisory in a SwiftFloris issue if SwiftFloris needs an emergency pin, workaround, or release note.

## Reporting channel

Use [GitHub Security Advisories](https://github.com/SysAdminDoc/SwiftFloris/security/advisories/new) for private
reports. If that form is unavailable, open a public GitHub issue with only a minimal summary and ask for private
follow-up before sharing exploit details, crash logs, personal dictionary data, clipboard content, private APKs, or
proof-of-concept files.

Include the affected SwiftFloris version, Android version, install channel, reproduction steps, impact, and whether
the issue requires a malicious app, a crafted local file, physical access, debug tooling, or normal keyboard use.

## Response expectations

Maintainers aim to acknowledge valid reports within seven days, confirm scope and severity before public disclosure,
and keep the reporter updated when a fix needs a release train or upstream dependency change. Confirmed vulnerabilities
receive release-note credit unless the reporter asks to stay anonymous.

## Threat model

SwiftFloris is designed as a no-network keyboard: typed text, suggestions, clipboard history, dictionaries, settings,
and addon audit records stay on the device unless the user explicitly exports or shares them. Optional integrations
are local handoffs to user-installed providers, and sensitive fields suppress risky surfaces such as external voice
handoff. This document focuses on the dependency and supply-chain side of that posture.

## Local key storage

As of v1.8.68, SwiftFloris no longer depends on AndroidX Security Crypto's deprecated
`EncryptedSharedPreferences` APIs. `TinkStringPreferenceCrypto` uses Tink Android `1.22.0`, creates
AndroidKeystore-held AES-256-GCM wrapping keys, and binds ciphertext to the `prefsFile:key` associated-data tuple.
Reads of existing ciphertext do not generate replacement Keystore keys; missing keys fail closed and writes are the
only path that create a new wrapper key.

The helper is used for the SQLCipher personal-dictionary passphrase
(`sqlcipher_passphrase_tink_v1`). The old parallel in-process clipboard-history
Tink store was removed in v1.8.121 after the seventh-pass audit confirmed it was
not on the live IME path; clipboard history is stored through the Room-backed
`ClipboardManager`/`ClipboardHistoryDatabase` path.

Legacy AndroidX encrypted-preference payloads are read only for one-shot migration when their keysets are still
recoverable. The normal runtime dependency is now `com.google.crypto.tink:tink-android`.

## SQLCipher crypto provider watch

SwiftFloris currently uses the stock SQLCipher Android Community AAR. As of
SQLCipher 4.16.0, Zetetic's Android Community provider matrix still lists
LibTomCrypt for that artifact, while OpenSSL is the supported build path for
commercial / enterprise non-FIPS packages and custom source builds. The
provider-removal risk is tracked, but there is no immediate dependency swap in
`:app`; migration experiments and rollback notes stay in maintainer-local
planning docs until they are ready for public review.

## Dependency scanning

### Local release scan

`scripts/release-evidence.ps1` first verifies public README/security/reproducible-build version pins against
`gradle/libs.versions.toml`, `gradle-wrapper.properties`, and `gradle.properties`, then runs OSV-Scanner locally.
The scan reads
`gradle/libs.versions.toml`, `gradle/tools.versions.toml`, every `build.gradle.kts`, `settings.gradle.kts`, and the
resolved Gradle metadata available in the checkout. It writes `osv-result.json`, command logs, and a plain-text
summary under `build/release-evidence/<timestamp>/`.

If the release-time scan finds a HIGH or CRITICAL advisory, `scripts/osv-release-gate.py` blocks the release. The
gate parses `osv-result.json`, classifies each finding by CVSS score or database severity, and exits non-zero for any
unoverridden HIGH/CRITICAL match. LOW and MEDIUM findings are summarized but non-blocking.

To override a blocking advisory (e.g. not reachable, awaiting upstream fix), add an entry to `.github/osv-overrides.json`
with the advisory `id`, `severity`, `rationale`, `owner`, and `expiry` (YYYY-MM-DD). Expired overrides are ignored and
produce a local warning. The override file is committed and auditable.

SwiftFloris currently publishes local release evidence, APK hashes, and OSV summaries. It does not claim automated
remote attestation or component-inventory generation for current releases.

## What "clean" means

A "0 known vulnerabilities" line in a release body means:

- **Yes:** As of `<release-date>`, the OSV database knew of no CVE affecting any package version on the
  `releaseRuntimeClasspath` of `:app` at the resolved versions in `gradle/libs.versions.toml`.
- **No:** SwiftFloris is forever free of vulnerabilities.
- **No:** SwiftFloris has been audited for unknown / zero-day issues.
- **No:** Every dependency author has done a clean security review of their code.

A non-empty list in a release body means at least one advisory matched. The advisory IDs are linked back to OSV.dev
so readers can check severity, exploit vector, and patch availability without re-running the scan.

## Reproducing the scan locally

```bash
# Install osv-scanner (see https://google.github.io/osv-scanner/)
go install github.com/google/osv-scanner/v2/cmd/osv-scanner@latest

# From the repository root
./gradlew :app:dependencies --configuration releaseRuntimeClasspath > gradle-deps.txt
osv-scanner scan source -r --format json --output-file build/release-evidence/manual-osv-result.json ./
python scripts/osv-release-gate.py build/release-evidence/manual-osv-result.json
```

The recursive scan covers the lockfiles, the Gradle files, the dependency-tree dump, and any vendored manifests.

## Public disclosure

Do not publish unpatched exploit details, private user data, or weaponized proof-of-concept files before maintainers
have had a chance to triage and patch. After a fix ships, SwiftFloris documents the affected version range, fix
version, and security-scan context in the GitHub Release notes.
