#!/usr/bin/env bash
# Maintainer-only — regenerates the obtainium-link block in README.md from
# the JSON track manifests under fastlane/obtainium/. Run on the maintainer
# build host; the result is committed manually.

set -euo pipefail

FASTLANE_DIR=$(dirname "$(realpath "$0")")
OBTAINIUM_DIR="$FASTLANE_DIR/obtainium"
README_FILE="$FASTLANE_DIR/../README.md"

if [ ! -d "$OBTAINIUM_DIR" ]; then
  echo "::error::Obtainium track dir missing: $OBTAINIUM_DIR" >&2
  exit 1
fi
if [ ! -f "$README_FILE" ]; then
  echo "::error::README.md missing at: $README_FILE" >&2
  exit 1
fi

obtainium_section="obtainium_links"
echo "obtainium"

# Build the replacement content in a temp file so we can hand it to a
# Python block-substitution call rather than smuggling it through the
# `sed c\` program text. The previous implementation interpolated a
# multi-line string with `\n` escapes directly into `sed -i ...c\\$var`,
# which fell apart on entries containing `/`, `&`, or literal newlines
# from `echo -e` style decoding.
TMP_BLOCK=$(mktemp)
trap 'rm -f "$TMP_BLOCK"' EXIT
{
  printf '<!-- BEGIN SECTION: %s -->\n' "$obtainium_section"
  printf '<!-- auto-generated link templates, do NOT edit by hand -->\n'
  printf '<!-- see fastlane/%s -->\n' "$(basename "$0")"
  shopt -s nullglob
  found_any=0
  for file in "$OBTAINIUM_DIR"/*.json; do
    found_any=1
    track_name=$(basename "$file" .json)
    # `jq -c .` compacts the JSON; `jq -sRr @uri` percent-encodes the
    # compact JSON so the resulting `obtainium://` URL is round-trip safe.
    track_urlenc_json=$(jq -c . "$file" | jq -sRr @uri)
    echo "  collect info for track '$track_name'" >&2
    markdown_link="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/$track_urlenc_json"
    printf '[obtainium_%s]: %s\n' "$track_name" "$markdown_link"
  done
  if [ "$found_any" -eq 0 ]; then
    echo "::error::No JSON track manifests found in $OBTAINIUM_DIR" >&2
    exit 1
  fi
  printf '<!-- END SECTION: %s -->\n' "$obtainium_section"
} > "$TMP_BLOCK"

echo "update README.md"
if grep -q "<!-- BEGIN SECTION: $obtainium_section -->" "$README_FILE"; then
  echo "  update existing section"
  # Use Python rather than sed for the block replacement: Python sees the
  # replacement content as a regular string with no shell-escape surface,
  # so a markdown line containing `/` or `&` cannot corrupt the program.
  python3 - "$README_FILE" "$TMP_BLOCK" "$obtainium_section" <<'PY'
import sys
from pathlib import Path

readme_path, block_path, section = sys.argv[1], sys.argv[2], sys.argv[3]
readme = Path(readme_path).read_text()
block = Path(block_path).read_text()
begin = f"<!-- BEGIN SECTION: {section} -->"
end = f"<!-- END SECTION: {section} -->"
begin_index = readme.find(begin)
end_index = readme.find(end)
if begin_index < 0 or end_index < 0 or end_index < begin_index:
    print(f"::error::Could not find {begin} … {end} block in README", file=sys.stderr)
    sys.exit(1)
# Replace the whole block including the end marker.
replacement = block.rstrip("\n") + "\n"
new_readme = readme[:begin_index] + replacement + readme[end_index + len(end):]
Path(readme_path).write_text(new_readme)
PY
else
  echo "  add new section"
  {
    printf '\n'
    cat "$TMP_BLOCK"
  } >> "$README_FILE"
fi
