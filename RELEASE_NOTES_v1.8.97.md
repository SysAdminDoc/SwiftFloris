# Release v1.8.97 — fastlane scripts hardening

Date: 2026-05-17

Follow-up F3 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

Two maintainer-only fastlane scripts had reliability footguns:

### `fastlane/update-readme.sh`

The previous implementation interpolated a multi-line markdown block
directly into `sed -i "/BEGIN/,/END/c\\$obtainium_links"`. Three concrete
problems:

1. Markdown links contain `/`, which is sed's default address delimiter.
   Any `$obtainium_links` entry with an unescaped `/` would either break
   the `sed` invocation or corrupt the README.
2. The `c\` (change) command in sed expects each replacement line to end
   with a `\` continuation marker. The `echo -e` source escaping was
   inconsistent with that requirement.
3. `set -e` was absent. A failure inside the `for` loop (e.g. malformed
   JSON in a track manifest) silently produced an incomplete block and
   the script still tried to write it.

This release:

- Adds `set -euo pipefail`.
- Writes the replacement block to a temp file (`trap`-cleaned on exit),
  then hands it to a Python block-substitution call. Python sees the
  block as a regular string with no shell-escape surface, so a markdown
  line containing `/` or `&` cannot corrupt the program.
- Exits with `::error::` on missing prerequisites (Obtainium dir, README
  file, zero track manifests) so a `for file in dir/*.json; do` over an
  empty dir doesn't silently produce a broken README.

### `fastlane/generate-screenshots.sh`

The previous script ended with `cd ..; rm -r out` after a nested
sequence of `cd`s. If any intermediate step failed silently or changed
directory, the `rm -r out` would have removed the wrong tree. Two
specific risks closed:

- `cd staging/images || exit` at the top + `cd out || exit` later
  meant the cleanup depended on the script-runner's CWD being correct
  at start. Now resolves an absolute path to `staging/images` via
  `${(%):-%x}` (zsh's script-file-name parameter), independently of
  the runner's CWD.
- `rm -r out` replaced with `rm -rf -- "$OUT_DIR_ABS"` where
  `OUT_DIR_ABS` is the absolute path captured at start. Any later
  `cd` mutation can't redirect the cleanup target.

Strict mode (`set -euo pipefail`) added. `mkdir out` → `mkdir -p out`
so a re-run on a non-empty workspace doesn't abort under strict mode.

Quoting the dozens of internal `$SPLIT_IMAGE_*` / `$OUT_FILE` ImageMagick
references is left alone — the names are constants under maintainer
control and quoting them all would be a much larger diff for negligible
risk reduction. If a future PR adds a SPLIT_IMAGE name containing a
space, that PR should add the quotes locally.

## Files touched

- `fastlane/update-readme.sh`
- `fastlane/generate-screenshots.sh`
- `gradle.properties` — versionCode 1897 / versionName 1.8.97

## Verification

Both scripts are maintainer-only — run on the build host, never in CI.
Smoke test on the build host:

```bash
# update-readme.sh: re-run; diff README.md against pre-run state.
./fastlane/update-readme.sh
git diff README.md

# generate-screenshots.sh: pre-populate staging/images with the canonical
# screenshot fixtures, then:
./fastlane/generate-screenshots.sh
ls metadata/android*/en-US/images/phoneScreenshots/
```

In both cases the script should error-out cleanly on missing inputs
(previously, missing inputs produced silent partial results).
