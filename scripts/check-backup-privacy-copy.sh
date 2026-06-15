#!/usr/bin/env bash
# scripts/check-backup-privacy-copy.sh
#
# Guard against public prose drifting from the actual data_extraction_rules.xml.
# The rules exclude the personal dictionary, key material, clipboard history,
# learned n-grams, SwiftKey traces, and sync identity from BOTH cloud backup
# and device-to-device transfer. Any prose that says transfer is "kept" or
# "allowed" for these items is misleading.
#
# Exits 0 on pass, 1 on any match.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

errors=0

fail() {
  echo "::error::backup-privacy-copy: $*"
  errors=$((errors + 1))
}

# Prose files to scan (docs + README).
FILES=(
  README.md
  docs/PRIVACY_AND_AI.md
  docs/THREAT_MODEL.md
  docs/SECURITY.md
  docs/REPRODUCIBLE_BUILDS.md
)

# Patterns that indicate stale wording about device transfer being preserved.
PATTERNS=(
  'device.transfer kept'
  'device.transfer.* is allowed'
  'device.transfer.* is preserved'
  'device.transfer.* is included'
  'd2d.* transfer.* allowed'
  'd2d.* transfer.* kept'
)

for f in "${FILES[@]}"; do
  [ -f "$f" ] || continue
  for pat in "${PATTERNS[@]}"; do
    matches="$(grep -inP "$pat" "$f" || true)"
    if [ -n "$matches" ]; then
      while IFS= read -r line; do
        fail "${f}: stale backup/transfer wording: ${line}"
      done <<< "$matches"
    fi
  done
done

if [ "$errors" -gt 0 ]; then
  echo "backup-privacy-copy: FAIL ($errors stale-wording match(es) found)"
  echo "The data_extraction_rules.xml excludes the personal dictionary from"
  echo "BOTH cloud backup and device-to-device transfer. Update the prose to match."
  exit 1
fi

echo "backup-privacy-copy: OK (no stale transfer wording found)"
