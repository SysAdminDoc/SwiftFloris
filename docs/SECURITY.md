# SwiftFloris Security

This document explains SwiftFloris's release-time security posture, the dependency-scanning policy, and how the
"checked on date X" status note for each release is produced.

## At a glance

- SwiftFloris does not request the `INTERNET` permission. CI verifies this on every build via
  `:app:verifyNoInternetPermission`.
- Local sensitive stores use Android platform keys: the SQLCipher personal-dictionary passphrase is wrapped with
  Tink `Aead` and AndroidKeystore-held AES-256-GCM keys.
- The Gradle dependency tree is scanned for known CVEs on a weekly cron, on every change to a version-bearing build
  file, and again at release time.
- Every GitHub Release body carries an "OSV scan" appendix that records the scanner version, the date the scan ran,
  and either "0 known vulnerabilities" or the count + identifiers.

A clean OSV scan does **not** imply forever-immunity. CVEs are published continuously, and a clean check on release
day will not catch a vulnerability disclosed the day after. The dated stamp lets users reason about how stale the
security check is for the version they have installed.

## Supported scope

Security reports are in scope when they affect the SwiftFloris app, release artifacts, update channel, build
pipeline, bundled data, addon trust path, local storage, backup/restore flow, clipboard handling, IME field-sensitivity
handling, or any other path that can expose typed text, personal dictionaries, clipboard content, local files, signing
trust, or device-local settings.

Reports about upstream dependencies are still useful, but the primary fix usually belongs upstream. File those with
the affected Android, AndroidX, Compose, Kotlin, Room, SQLCipher, Tink, Gradle, or GitHub Actions project first, then
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
`EncryptedSharedPreferences` APIs. `TinkStringPreferenceCrypto` uses Tink Android `1.21.0`, creates
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

### Weekly + on-change scan (`.github/workflows/dependency-scan.yml`)

The dependency-scan workflow runs three independent checks:

1. **GitHub Dependency Review** — runs on pull requests. Fails the workflow on `severity = high`.
2. **OSV-Scanner** — runs on push to `main` / `master`, on weekly cron (Sundays 06:00 UTC), and on every change to a
   version-bearing build file. Reads `gradle/libs.versions.toml`, `gradle/tools.versions.toml`, every `build.gradle.kts`,
   `settings.gradle.kts`, and the `:app:releaseRuntimeClasspath` dependency tree.
3. **Gradle dependency tree upload** — uploads the full `:app:dependencies` output as a workflow artifact so any
   reviewer can re-run the scan offline against the exact transitive closure that shipped.

The scan job's failure threshold is **any HIGH or CRITICAL CVE in a runtime-classpath dependency**. MEDIUM and below
are reported but non-blocking, so we can publish releases with known-low-severity issues if the upstream fix is not
yet available.

### Weekly version-review PRs (`.github/dependabot.yml`)

Dependabot version updates run weekly for Gradle and GitHub Actions manifests.
The Gradle update scope includes `gradle/libs.versions.toml`, custom catalog
imports from `settings.gradle.kts`, and module build files; GitHub Actions
updates cover workflow action references under `.github/workflows/`. These PRs
are review prompts, not auto-merge rules: maintainers review runtime impact,
license compatibility, changelogs, and security advisories before accepting
any bump.

### Release-time scan (`.github/workflows/release.yml`)

The release workflow runs an additional OSV scan as part of the release pipeline. The scan summary (count, scanner
version, scan date, and any flagged advisory IDs) is captured into `RELEASE_OSV_SUMMARY.md` in the workflow runner
and appended to the GitHub Release body. The result is reproducible — anyone can re-run `osv-scanner --recursive ./`
locally against the source tree at the matching tag and expect the same advisory set, modulo CVEs disclosed after the
release date.

If the release-time scan finds a HIGH or CRITICAL vulnerability the workflow does **not** silently swallow it; the
step is `continue-on-error: true` so the release proceeds, but the failure is recorded in the release body and
visible at a glance.

### Provenance attestation (`.github/workflows/release.yml`)

Every release APK receives a SLSA Build Level 2 provenance attestation via
`actions/attest-build-provenance`. The attestation is stored in the GitHub
attestation store and can be verified by anyone:

```bash
gh attestation verify SwiftFloris-*.apk --repo SysAdminDoc/SwiftFloris
```

This proves the APK was built by the release workflow from a specific commit on
the `SysAdminDoc/SwiftFloris` repository, not uploaded manually or built on an
uncontrolled machine. The OIDC identity token binds the attestation to the GitHub
Actions runner that produced the artifact.

### SBOM (`.github/workflows/release.yml`)

Each release includes an SPDX JSON SBOM (`swiftfloris-sbom.spdx.json`) generated
by `anchore/sbom-action`. The SBOM catalogues every first-party and third-party
component in the source tree at build time. It is attached to the GitHub Release
alongside the APK and SHA256SUMS manifest.

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
go install github.com/google/osv-scanner/cmd/osv-scanner@v2.0.2

# From the repository root
./gradlew :app:dependencies --configuration releaseRuntimeClasspath > gradle-deps.txt
osv-scanner --recursive --skip-git ./
```

The recursive scan covers the lockfiles, the Gradle files, the dependency-tree dump, and any vendored manifests.

## Public disclosure

Do not publish unpatched exploit details, private user data, or weaponized proof-of-concept files before maintainers
have had a chance to triage and patch. After a fix ships, SwiftFloris documents the affected version range, fix
version, and security-scan context in the GitHub Release notes.
