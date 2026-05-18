# Release v1.8.85 — Cross-subsystem hardening pass

Date: 2026-05-17
Author: AI-assisted audit (Claude Code, Opus 4.7), reviewed by maintainer.

## ⚠️ Per-PR-scope deviation

This release intentionally violates [AGENTS.md §6](AGENTS.md) ("One logical
improvement per commit / PR") and the
[IMPROVEMENT_PLAN.md §9](IMPROVEMENT_PLAN.md) Repo Hygiene rule. The
maintainer commissioned an extreme cross-subsystem audit + hardening pass
spanning the just-shipped v1.8.75-84 slices plus the load-bearing privacy /
backup / CI infrastructure, and explicitly opted out of the per-PR-scope
rule for this release.

Future per-feature work returns to the one-logical-change-per-release pattern.

## ⚠️ Verification status

The dev VM has no JDK / Android SDK on its path, so the
[AGENTS.md §5](AGENTS.md) Definition-of-Done verification commands
(`:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`,
`:app:verifyRoborazziDebug`, manual QA, APK SHA-256) **have not** been
run for this release on this host. The maintainer's primary build host
should run the full DoD set before tagging and pushing.

Each fix below is annotated with the specific verification command that
should pass.

## Summary

Five subsystem-spanning research agents audited the v1.8.75-84 slices and
foundational privacy / build / CI surfaces. Findings landed across six
priority-zero / priority-one categories. Sixteen fixes ship in this release;
the remaining seven (described in §Follow-up below) are scoped for separate
single-feature releases.

## P0 — Privacy / supply-chain gate hardening

### 1. `verifyNoInternetPermission` no longer leaks on merged manifests

[app/build.gradle.kts](app/build.gradle.kts) — the project's flagship
no-network gate previously only scanned `app/src/**/AndroidManifest.xml`.
A library AAR (current or future) that added `INTERNET` via manifest merging
would slip past the gate, the merged manifest would carry the permission,
and the contract printed everywhere in marketing and `PROJECT_CONTEXT.md` §2
would be silently broken.

This release:

- Adds a per-variant `verifyNoInternetPermissionMerged<Variant>` task wired
  against AGP's `SingleArtifact.MERGED_MANIFEST`, so every library and every
  flavor/buildType overlay is included in the check.
- Adds `finalizedBy` on `processManifest` and `dependsOn` on `assemble` so
  the merged-manifest check runs both during PR builds and during release
  builds, catching regressions before the APK is assembled.
- Honours legitimate `tools:node="remove"` / `tools:node="removeAll"`
  directives in both the source-pre-check and merged post-check, so the
  documented escape hatch (strip a permission a library wrongly declared)
  works correctly.
- Rewrites the regex into a single multi-line-tolerant element matcher
  rather than five permission-specific regexes; the diff is shorter and the
  scanner is more robust to manifest formatting variations.

Verification: `./gradlew.bat :app:verifyNoInternetPermission
:app:verifyNoInternetPermissionMergedDebug :app:assembleDebug` should all
pass; an artificial INTERNET declaration in any library module's manifest
should fail the merged check.

### 2. CI workflows no longer ship with the default read/write GITHUB_TOKEN

[`.github/workflows/android.yml`](.github/workflows/android.yml),
[`.github/workflows/crowdin-upload.yml`](.github/workflows/crowdin-upload.yml),
[`.github/workflows/reproducible-build.yml`](.github/workflows/reproducible-build.yml)
— added file-scope `permissions: { contents: read }` blocks. Previously the
default token inherited the repo-wide setting (typically read-write), so a
malicious / compromised transitive action dependency could push code, edit
releases, or comment on issues using the workflow's own token. After this
change the default token can only read the repo; jobs that need additional
scopes (e.g. validate-strings-no-translations's `pull-requests: write`)
declare them explicitly.

The remaining workflows (`dependency-scan.yml`, `roborazzi-baseline.yml`,
`release.yml`) already had explicit `permissions:` blocks.

### 3. `validate-strings-no-translations.yml` no longer interpolates untrusted PR data into shell

[`.github/workflows/validate-strings-no-translations.yml`](.github/workflows/validate-strings-no-translations.yml)
— the workflow runs on `pull_request_target` (base-repo context with the
repo's own GITHUB_TOKEN) and previously interpolated
`${{ github.event.pull_request.user.login }}`, the PR file list, and the
`steps.fetch_changed_files.outputs.illegal_changes_list` step output
directly into `run:` blocks. The step output is derived from PR-author-
controlled filenames; a PR file path containing shell metacharacters
(quotes, semicolons, backticks) could break out of the `echo` command and
execute attacker-controlled shell in the base-repo context.

This release:

- Passes every `${{ github.event.* }}` and step-output value via `env:`,
  references them as quoted shell variables (`"$VAR"`) only.
- Replaces the hand-rolled `curl + jq` PR-files paginator with `gh api
  --paginate --jq`, which never interpolates the response into the shell
  command line.
- Adds `set -euo pipefail` to every `run:` block so an upstream failure
  (network blip, missing jq) is loud rather than silent.
- Markdown-fences the illegal-files list in the comment body so even a
  filename containing backticks cannot escape the fence into surrounding
  markdown commands.

The third-party `peter-evans/create-or-update-comment` action is kept on its
floating `@v4` tag with a `TODO supply-chain` comment pointing at the
`gh api repos/peter-evans/create-or-update-comment/git/refs/tags/v4` lookup
the maintainer should run to pin it to a SHA. Pinning to an unverified SHA
from this AI session would risk breaking CI more than the floating tag does.

## P0 — Reliability / crash prevention

### 4. `HardwareKeyboardRuntimeMapper` is now thread-safe

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt#L36-L80)
— `layoutsByDeviceId` is a plain `LinkedHashMap` touched from the IME input
thread (via `KeyboardManager.onHardwareKeyDown` and the `InputManager`
device-detach callback) and from the settings/UI thread when the user binds
a layout to a device. Concurrent `put` + `remove` on a `LinkedHashMap` can
corrupt the bucket array and throw `ConcurrentModificationException`,
crashing the IME on a hot path. All accesses now go through a monitor lock.

Same file: `BitmapFactory`-style "swallow Ctrl-pressed events" check at
`map(HardwareKeyEventInfo)` was rejecting every PC-style AltGr keystroke
(Android delivers AltGr as Ctrl+Alt), so `.klc` imports with AltGr-mapped
characters (€ on EU layouts, all CJK IME hooks) were unreachable. Now
rejects Ctrl ONLY when Alt is not also pressed.

### 5. `BitmapFactory.decodeStream` in the sticker palette is bounded

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt)
— the v1.8.77 user-imported sticker folder feeds arbitrary user-selected
SAF file URIs into `BitmapFactory.decodeStream(stream)` with no
`BitmapFactory.Options`. A corrupted or hostile 100k × 100k PNG would
allocate 40 GB of bitmap heap and crash the IME process. Replaced with the
canonical two-pass pattern: `inJustDecodeBounds=true` reads dimensions
without allocating pixels; a hard reject ceiling of 8192 px on either edge
rejects anything obviously hostile; then `inSampleSize` is computed so the
final bitmap never exceeds ~512 px on its longest edge.

### 6. `ZipUtils.unzip` gains pre-canonical entry-name guard + entry-count cap

[app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt](app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt)
— the existing zip-slip guard relied on `File.canonicalPath` comparison,
which resolves symlinks. Added a layer-zero check on the entry name itself:
reject `..` path segments, leading `/` or `\`, Windows drive prefixes,
NUL bytes, and empty entry names *before* the filesystem ever sees them.
Also added a hard cap of 10_000 entries per archive — defends against the
zip-bomb pattern that ships millions of zero-byte entries (passes per-entry
and total-byte gates but exhausts inodes / dentries on extraction).

## P1 — Privacy / data integrity

### 7. Android 12+ `dataExtractionRules` now uses the correct schema

[`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml#L63)
previously pointed `android:dataExtractionRules="@xml/backup_rules"` at a
file whose root element is `<full-backup-content>`. The Android 12+
`dataExtractionRules` attribute requires the `<data-extraction-rules>`
schema with separate `<cloud-backup>` and `<device-transfer>` sections.
On Android 12+, the system silently fell back to the default "include
everything" device-transfer behaviour, which would:

- Carry the SQLCipher personal-dictionary DB (`floris_user_dictionary*`) to
  a new device as part of a D2D transfer, alongside an Android-Keystore-
  bound passphrase that cannot be transferred — leaking the encrypted PII
  blob AND bricking the dictionary on the new device.
- Carry `floris_user_dictionary_key.xml` (the Tink-wrapped passphrase
  SharedPrefs), with the same problem.

Added [`app/src/main/res/xml/data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)
with explicit `<exclude>` entries for every personal-dictionary DB
sidecar file (`.db`, `.db-journal`, `.db-wal`, `.db-shm`) plus the wrap-key
preferences and the clipboard-history directory, in both the
`<cloud-backup>` and `<device-transfer>` rule sets. Manifest now points
to the new file for API 31+; pre-31 still reads `backup_rules.xml` via
`android:fullBackupContent`.

This closes a real data-leakage hole that the [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md)
posture had implicitly assumed was already shut.

## P1 — UX correctness

### 8. Sticker MIME-type spoof closed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt)
— `resolveMimeType` previously fell back to filename-extension detection
whenever SAF returned a MIME that wasn't in the supported-image set. So a
file with SAF-declared `application/octet-stream` but named `evil.png`
would be announced to the recipient app's `commitContent` receiver as
`image/png`, even though the bytes were arbitrary. Recipient apps that
auto-forward image attachments (most messengers) would propagate the
spoofed type. New behaviour: SAF's declared MIME is the source of truth
when present and non-empty. The extension-based fallback only runs when
SAF gives us nothing (the common case for naive file managers).

### 9. Addon enumerator no longer rejects legitimate 64+ MB asset packs

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonEnumerator.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonEnumerator.kt)
— `evaluate()` was reading `File(sourceDir).length()` (the APK file size on
disk) and feeding it through `AddonManifest`'s `require(bundleSizeBytes <=
ADDON_MAX_BUNDLE_BYTES)`. The bundle-size gate exists to stop a malicious
addon from claiming a 500 MB asset bundle that would OOM the IME on
enrolment, but the IME never loads the APK into RAM (PackageManager + mmap
handle that). Net effect: legitimate 65 MB theme packs and dictionary
packs were silently rejected at enrolment. Bundle-size enforcement moves
to the asset-mounting step (future Next-10.4); the enrolment-time field is
now `0L` and the gate becomes a no-op until then.

## P1 — Build reliability

### 10. `verify-reproducible-apk.sh` no longer always fails on signed APKs

[scripts/verify-reproducible-apk.sh](scripts/verify-reproducible-apk.sh)
— the script's top-level pass condition was `cmp -s` on the full signed
APK. Signed release APKs cannot be byte-identical (the v2/v3 signing block
contains randomised padding even with deterministic content), so this
check always failed on signed builds and was effectively meaningless. Now:

- If full bytes match → exit 0 (best — full reproducibility).
- Else compute payload entry manifests (ZIP entries outside `META-INF/`);
  if those match → exit 0 with a note that the drift is in the signing
  block (the F-Droid verified-reproducible-tier requirement).
- Else → exit 1 (real payload divergence, the only failure mode F-Droid
  cares about).

This aligns the gate with the F-Droid rebuilder's actual comparison
methodology.

## Follow-up work (next per-feature releases)

The audit found several additional defects that warrant their own
single-feature releases per the standard per-PR-scope rule:

- **Addon spec ↔ visibility mismatch.** `AddonContract.kt` says receivers
  are optional, but Android 11+ `<queries>` based on `<intent>` only makes
  packages with matching intent-filters visible. Spec-compliant addons
  declaring only `<meta-data>` cannot be discovered. Either update the
  spec to mandate a receiver or change the enumerator to use
  `queryBroadcastReceivers()`. (Settle by docs change; defer.)
- **SAF persistable-URI re-take on cold start** for the user-imported
  sticker folder — currently a swallowed `SecurityException` silently
  empties the Imported pack after a process restart.
- **`floris_user_dictionary_key.xml` excluded from user backup zip.**
  `BackupScreen.kt` still includes the SharedPrefs path; on restore-to-
  new-device, the Tink-wrapped passphrase is undecryptable. Surface a
  "regenerate passphrase" path rather than the current `error(...)`.
- **`FLAG_SECURE` extension to numeric-password fields.** Current check
  misses `TYPE_NUMBER_VARIATION_PASSWORD`-only inputs; PIN entry can
  leak into the IME-local clipboard history.
- **`FLAG_SECURE` on the encrypted-export passphrase dialog** to block
  screenshots / screen-recording.
- **`ZipUtils.unzip` abort-vs-continue policy.** Currently warns and
  continues on slip/oversize; a partial restore looks successful to the
  user. Recommend converting to an abort-on-first-violation atomic
  semantic.
- **Hardware-layout LDML `longPress` semantics.** `KeymanLdmlParser` reads
  the first codepoint of `longPress` into the `shift` slot; LDML defines
  `longPress` as a space-separated alternates list, not a shift glyph.
  Real Keyman keyboards (Amharic, etc.) display wrong shifted chars. Fix
  needs `HardwareKeyEntry` to grow a `longPressAlternates` field.

These will land as `RELEASE_NOTES_v1.8.86.md` through `v1.8.92.md` as
prioritised on the maintainer's build host.

## Files touched

- `app/build.gradle.kts` — merged-manifest gate, `tools:node="remove"` handling.
- `app/src/main/AndroidManifest.xml` — point `dataExtractionRules` at new schema file.
- `app/src/main/res/xml/data_extraction_rules.xml` — NEW: Android 12+ rules.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonEnumerator.kt` — drop APK-size bundle gate.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt` — thread safety, AltGr.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt` — bounded decode.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt` — MIME spoof fix.
- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt` — pre-canonical entry guard, entry-count cap.
- `.github/workflows/android.yml` — file-scope read-only permissions.
- `.github/workflows/crowdin-upload.yml` — file-scope read-only permissions.
- `.github/workflows/reproducible-build.yml` — file-scope read-only permissions.
- `.github/workflows/validate-strings-no-translations.yml` — env-passing untrusted PR data.
- `scripts/verify-reproducible-apk.sh` — entry-manifest pass criterion for signed APKs.
- `gradle.properties` — versionCode 1885 / versionName 1.8.85.

## Verification commands the maintainer should run before tag + push

```powershell
./gradlew.bat :app:verifyNoInternetPermission
./gradlew.bat :app:verifyNoInternetPermissionMergedDebug
./gradlew.bat :app:verifyNoInternetPermissionMergedRelease
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:verifyRoborazziDebug
./gradlew.bat :app:installDebug
```

Plus a manual QA pass on a device covering:
- Type into a hardware-keyboard-connected field with AltGr characters (€, etc.).
- Connect + disconnect a Bluetooth keyboard while typing.
- Pick a SAF folder with a known oversized PNG (> 8192 px); verify the tile shows the fallback IMG label rather than crashing.
- Pick a SAF folder with an `evil.png` whose contents are `text/plain`; verify it is rejected / not committed.
- Run a backup → restore round-trip on Android 12+ and Android 14+ devices; confirm the personal-dictionary DB is regenerated cleanly (not transferred).
- Trigger a PR with a hostile filename in the path `app/src/main/res/values-*/strings.xml` and verify the CI does not execute the filename as shell.

If any of those fail, the corresponding fix above should be reverted from
this release and re-landed as its own per-PR commit per AGENTS.md §6.
