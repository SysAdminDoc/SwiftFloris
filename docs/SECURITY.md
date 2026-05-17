# SwiftFloris Security

This document explains SwiftFloris's release-time security posture, the dependency-scanning policy, and how the
"checked on date X" status note for each release is produced.

## At a glance

- SwiftFloris does not request the `INTERNET` permission. CI verifies this on every build via
  `:app:verifyNoInternetPermission`.
- Local sensitive stores use Android platform keys: clipboard history and the SQLCipher personal-dictionary
  passphrase are wrapped with Tink `Aead` and AndroidKeystore-held AES-256-GCM keys.
- The Gradle dependency tree is scanned for known CVEs on a weekly cron, on every change to a version-bearing build
  file, and again at release time.
- Every GitHub Release body carries an "OSV scan" appendix that records the scanner version, the date the scan ran,
  and either "0 known vulnerabilities" or the count + identifiers.

A clean OSV scan does **not** imply forever-immunity. CVEs are published continuously, and a clean check on release
day will not catch a vulnerability disclosed the day after. The dated stamp lets users reason about how stale the
security check is for the version they have installed.

## Threat model

See [`docs/THREAT_MODEL.md`](THREAT_MODEL.md) for the broader threat model (data-at-rest, addon isolation, MCP
binding scope, password-field handling). This document is narrower: it covers only the dependency / supply-chain side
of the security posture.

## Local key storage

As of v1.8.68, SwiftFloris no longer depends on AndroidX Security Crypto's deprecated
`EncryptedSharedPreferences` APIs. `TinkStringPreferenceCrypto` uses Tink Android `1.21.0`, creates
AndroidKeystore-held AES-256-GCM wrapping keys, and binds ciphertext to the `prefsFile:key` associated-data tuple.
Reads of existing ciphertext do not generate replacement Keystore keys; missing keys fail closed and writes are the
only path that create a new wrapper key.

The helper is used for:

- The SQLCipher personal-dictionary passphrase (`sqlcipher_passphrase_tink_v1`).
- The legacy in-process clipboard-history store (`clipboard_history_tink_v1`).

Legacy AndroidX encrypted-preference payloads are read only for one-shot migration when their keysets are still
recoverable. The normal runtime dependency is now `com.google.crypto.tink:tink-android`.

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

### Release-time scan (`.github/workflows/release.yml`)

The release workflow runs an additional OSV scan as part of the release pipeline. The scan summary (count, scanner
version, scan date, and any flagged advisory IDs) is captured into `RELEASE_OSV_SUMMARY.md` in the workflow runner
and appended to the GitHub Release body. The result is reproducible — anyone can re-run `osv-scanner --recursive ./`
locally against the source tree at the matching tag and expect the same advisory set, modulo CVEs disclosed after the
release date.

If the release-time scan finds a HIGH or CRITICAL vulnerability the workflow does **not** silently swallow it; the
step is `continue-on-error: true` so the release proceeds, but the failure is recorded in the release body and
visible at a glance.

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

## Reporting a vulnerability

If you find a vulnerability in SwiftFloris itself (not in an upstream dependency), please open a private security
advisory through GitHub's "Security" tab on the repository, or email the maintainers directly. Do not file a public
issue for unpatched vulnerabilities. The maintainers will respond within a week and publish a fix and credit you in
the release notes unless you ask to stay anonymous.

For vulnerabilities in upstream dependencies (Android SDK, AndroidX, Compose, Kotlin, Room, SQLCipher, etc.), please
file with the upstream project. The dependency-scan workflow will surface their fix automatically on the next run.
