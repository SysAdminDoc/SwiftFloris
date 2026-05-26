#!/usr/bin/env bash
# scripts/check-fastlane-metadata.sh
#
# F-Droid metadata gate. The F-Droid build server reads from
# fastlane/metadata/android/en-US/ for the listing's title, short
# description, full description, and per-versionCode changelogs. If those
# files drift from current reality, the listing shows stale or wrong content
# (worst case: still says "FlorisBoard"). This script catches that.
#
# Rules:
#   1. title.txt exists, ≤ 50 chars (F-Droid cap)
#   2. short_description.txt exists, ≤ 80 chars (F-Droid cap)
#   3. full_description.txt exists and is non-empty
#   4. For the current gradle.properties projectVersionCode N, a file
#      fastlane/metadata/android/en-US/changelogs/<N>.txt exists, is non-empty,
#      and is ≤ 500 chars (F-Droid cap)
#   5. title.txt may not contain "FlorisBoard" (this fork is SwiftFloris)
#
# Exits 0 on pass, 1 on any rule violation.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

META_DIR="fastlane/metadata/android/en-US"
TITLE="$META_DIR/title.txt"
SHORT="$META_DIR/short_description.txt"
FULL="$META_DIR/full_description.txt"
CHANGELOG_DIR="$META_DIR/changelogs"

fail() {
  echo "::error::fastlane metadata gate: $*"
  exit 1
}

[ -f "$TITLE" ] || fail "missing $TITLE"
[ -f "$SHORT" ] || fail "missing $SHORT"
[ -f "$FULL" ] || fail "missing $FULL"

# Count chars (not bytes — UTF-8 may inflate); use `wc -m`. Strip trailing
# newline before counting so the file's last \n doesn't push us over the cap.
title_chars="$(printf '%s' "$(cat "$TITLE")" | wc -m | tr -d ' ')"
short_chars="$(printf '%s' "$(cat "$SHORT")" | wc -m | tr -d ' ')"

[ "$title_chars" -le 50 ] || fail "$TITLE is $title_chars chars; F-Droid cap is 50"
[ "$title_chars" -gt 0 ]  || fail "$TITLE is empty"
[ "$short_chars" -le 80 ] || fail "$SHORT is $short_chars chars; F-Droid cap is 80"
[ "$short_chars" -gt 0 ]  || fail "$SHORT is empty"
[ -s "$FULL" ]            || fail "$FULL is empty"

# Rule 5: title must not be (or contain) "FlorisBoard" — this is the
# SwiftFloris fork. Upstream FlorisBoard ships under its own listing.
if grep -i -q 'FlorisBoard' "$TITLE"; then
  fail "$TITLE contains 'FlorisBoard' — SwiftFloris is the project name. F-Droid would publish the wrong listing title."
fi

# Pull projectVersionCode from gradle.properties.
version_code="$(awk -F= '/^projectVersionCode=/ { gsub(/[ \r\n]/,"",$2); print $2 }' gradle.properties)"
[ -n "$version_code" ] || fail "could not read projectVersionCode from gradle.properties"

changelog_file="$CHANGELOG_DIR/${version_code}.txt"
[ -f "$changelog_file" ] || fail "missing changelog for versionCode $version_code: $changelog_file
  → Create it by extracting the matching '## vX.Y.Z' section from CHANGELOG.md,
    trimmed to ≤ 500 chars. Without it, the F-Droid listing 'What's New' field
    falls back to whatever previous changelog file is highest, which is stale."

cl_chars="$(printf '%s' "$(cat "$changelog_file")" | wc -m | tr -d ' ')"
[ "$cl_chars" -le 500 ] || fail "$changelog_file is $cl_chars chars; F-Droid cap is 500. Trim it."
[ "$cl_chars" -gt 0 ]   || fail "$changelog_file is empty"

echo "fastlane metadata gate: OK (versionCode $version_code, title=$title_chars chars, short=$short_chars chars, changelog=$cl_chars chars)"
