# Release v1.8.94 — verify-addon-apk.sh distinguishes "no output" from "no match"

Date: 2026-05-17

Follow-up F4 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

[`scripts/verify-addon-apk.sh`](scripts/verify-addon-apk.sh) — three
checks (`check_permissions`, `check_register_receiver_and_metadata`,
`check_signing_certificate`) previously ran their respective Android
SDK tools with `|| true` to swallow any non-zero exit, then made the
PASS / FAIL decision based on whether `grep` matched anything in the
captured output. The collapse means:

- A corrupted `aapt2` binary that prints nothing and exits 1 silently
  PASSes the permissions and receiver checks (empty output → no
  banned-permission match → "PASS no banned network permissions
  declared"; empty manifest → no REGISTER action → "FAIL no
  REGISTER_ADDON intent action" but the cause is misreported as a
  contract violation).
- A missing `apksigner` binary at runtime silently looks like an
  unsigned APK.

This release switches each check to a three-state decision:

1. **Tool failed to invoke / exited non-zero** → FAIL with a message
   that names the tool and the exit code. The maintainer immediately
   knows it's a tooling problem, not an APK contract violation.
2. **Tool succeeded but produced no output** → distinguished per check:
   for permissions, empty output is genuine "no permissions declared"
   (PASS); for the manifest dump, empty output is malformed APK (FAIL).
3. **Tool succeeded with output** → original grep-based PASS / FAIL.

`set -u` was previously the only strict-mode flag. Replaced with
`set -eo pipefail` so a pipeline failure (e.g. `head | tail` segment)
aborts the script before it can falsely PASS the next check. `set -u`
itself is deliberately omitted because it would error on the unset
positional parameter `$1` before the usage check fires; the case-by-
case `${VAR:-default}` style covers the references that need it.

The script is consumed by the addon-CI gate documented in
[`docs/addons/apk-validation.md`](docs/addons/apk-validation.md), so
the contract surface is unchanged — only the failure-mode reporting
gets sharper.

## Files touched

- `scripts/verify-addon-apk.sh`
- `gradle.properties` — versionCode 1894 / versionName 1.8.94

## Verification

No `:app` source / lint / test impact.

Manual smoke on the build host:

```bash
# Move aapt2 out of the way; expect each check to FAIL with a clear
# "aapt2 dump … exited N" message rather than masquerade as an APK
# contract violation.
PATH="" ./scripts/verify-addon-apk.sh /path/to/some-addon.apk

# Run against a legitimate addon APK; expect PASS on all five checks
# unchanged from v1.8.93 baseline.
./scripts/verify-addon-apk.sh /path/to/known-good-addon.apk
```
