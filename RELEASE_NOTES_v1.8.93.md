# Release v1.8.93 — release.yml keystore-decode hygiene

Date: 2026-05-17

Follow-up F2 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

[`.github/workflows/release.yml`](.github/workflows/release.yml) — the
keystore-decode step previously had three forensic-leak / silent-failure
risks:

1. **`echo "$VAR" | base64 -d`** adds a trailing newline before the pipe,
   so any base64 payload whose encoder did not terminate with `\n` would
   have a stray `0x0a` appended pre-decode and the decoded bytes would
   be one byte off. The resulting keystore would fail to open, but the
   error message would not point at the encoding mistake. Replaced with
   `printf '%s' "$VAR" | base64 -d` so the secret is passed through
   verbatim.
2. **No `umask 077` or `chmod 600`** on the decoded keystore — on a
   shared runner image the file was world-readable until consumed.
   `umask 077` before the redirect plus `chmod 600` after closes the
   read window for any other process on the runner image.
3. **No magic-byte validation** — a malformed secret could produce a
   non-empty file that gets handed to `jarsigner` / AGP signing, and
   the failure mode is opaque. New check: read first 4 bytes, accept
   JKS (`FE ED FE ED`) or PKCS#12 (`30 82 …` DER SEQUENCE), fail-fast
   with a pointing error otherwise.

Same workflow: the `gh release create` step previously interpolated
`${{ inputs.version }}`, `${{ inputs.draft }}`, `${{ github.ref_name }}`,
`${{ steps.locate-apk.outputs.apk-path }}`, and
`${{ steps.sha.outputs.manifest-path }}` directly into the `run:` shell
command. The values are maintainer-controlled today, but the pattern is
the same script-injection footgun the
[`validate-strings-no-translations.yml` hardening (v1.8.85)](RELEASE_NOTES_v1.8.85.md)
closed. All five values now pass through `env:` and the command line
uses `"$VAR"`. `set -euo pipefail` added.

Bash arrays (`draft_arg=()`) replace the previous unquoted
`$draft_arg` expansion so a `false` setting can't accidentally pass an
empty-string argument that becomes a positional placeholder.

## Files touched

- `.github/workflows/release.yml`
- `gradle.properties` — versionCode 1893 / versionName 1.8.93

## Verification

No `:app` source / lint / test impact — workflow-only.

Manual reproduction the maintainer can run on the build host:

```bash
# Smoke test the magic-byte gate with a deliberately-broken secret.
SIGNING_KEYSTORE_BASE64="$(printf 'not-a-keystore' | base64)"
# Run the Decode keystore step locally and confirm it errors out with
# the "does not start with a JKS or PKCS#12 magic prefix" message.
```

The release workflow itself is `workflow_dispatch`-only, so the next
real exercise of this code path is the v1.8.93 GitHub Release the
maintainer triggers from their build host.
