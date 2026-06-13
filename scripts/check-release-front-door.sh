#!/usr/bin/env bash
# scripts/check-release-front-door.sh
#
# Release-front-door drift gate. Verifies that the public-facing release
# surfaces agree with gradle.properties. When they drift, install trust
# breaks: Obtainium users see a stale version, F-Droid reproducibility
# evidence references a tag that doesn't exist, and the README claims a
# release that GitHub Releases doesn't serve.
#
# Checks:
#   1. gradle.properties declares projectVersionName and projectVersionCode
#   2. Fastlane changelog for projectVersionCode exists and is non-empty
#   3. README.md "Current release" line matches projectVersionName
#   4. (CI-only, with gh available) latest GitHub Release tag matches
#      projectVersionName, or the release workflow has not run yet (first
#      release is allowed to be missing)
#
# Modes:
#   --strict   All checks must pass (use in release.yml)
#   (default)  README + fastlane checks; GitHub Release check is advisory
#
# Exits 0 on pass, 1 on any rule violation.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

STRICT=false
if [ "${1:-}" = "--strict" ]; then
  STRICT=true
fi

errors=0
warnings=0

fail() {
  echo "::error::release-front-door: $*"
  errors=$((errors + 1))
}

warn() {
  echo "::warning::release-front-door: $*"
  warnings=$((warnings + 1))
}

# --- 1. Read gradle.properties ---
version_name="$(awk -F= '/^projectVersionName=/ { gsub(/[ \r\n]/,"",$2); print $2 }' gradle.properties)"
version_code="$(awk -F= '/^projectVersionCode=/ { gsub(/[ \r\n]/,"",$2); print $2 }' gradle.properties)"

[ -n "$version_name" ] || fail "could not read projectVersionName from gradle.properties"
[ -n "$version_code" ] || fail "could not read projectVersionCode from gradle.properties"

if [ "$errors" -gt 0 ]; then
  echo "release-front-door: FAIL ($errors error(s))"
  exit 1
fi

echo "gradle.properties: v${version_name} (code ${version_code})"

# --- 2. Fastlane changelog ---
changelog="fastlane/metadata/android/en-US/changelogs/${version_code}.txt"
if [ ! -f "$changelog" ]; then
  fail "missing fastlane changelog for versionCode ${version_code}: ${changelog}"
elif [ ! -s "$changelog" ]; then
  fail "fastlane changelog ${changelog} is empty"
else
  echo "fastlane changelog: OK (${changelog})"
fi

# --- 3. README.md current-release text ---
# The README contains a line like:
#   - **v1.9.41** (2026-06-12) — ...
# in the "Recent releases" section. The first such entry is the current release.
# Also check the Status line: Current release: **v1.9.41**
# And the version badge: version-v1.9.41-blue
readme_version=""
if [ -f README.md ]; then
  # Check the shields.io badge first (most machine-parseable)
  badge_version="$(grep -oP 'version-v\K[0-9]+\.[0-9]+\.[0-9]+' README.md | head -1)" || true
  # Check the Status line
  status_version="$(grep -oP 'Current release: \*\*v\K[0-9]+\.[0-9]+\.[0-9]+' README.md | head -1)" || true
  # Check the Recent releases first entry
  releases_version="$(grep -oP '^\- \*\*v\K[0-9]+\.[0-9]+\.[0-9]+' README.md | head -1)" || true

  if [ -n "$badge_version" ]; then
    readme_version="$badge_version"
  elif [ -n "$status_version" ]; then
    readme_version="$status_version"
  elif [ -n "$releases_version" ]; then
    readme_version="$releases_version"
  fi

  if [ -z "$readme_version" ]; then
    fail "could not find a version string in README.md (checked badge, status line, recent releases)"
  elif [ "$readme_version" != "$version_name" ]; then
    fail "README.md reports v${readme_version} but gradle.properties declares v${version_name}"
  else
    echo "README.md version: OK (v${readme_version})"
  fi

  # Cross-check: all three sources should agree if present
  for src_name in badge status releases; do
    eval "src_val=\${${src_name}_version}"
    if [ -n "$src_val" ] && [ "$src_val" != "$version_name" ]; then
      fail "README.md ${src_name} version (v${src_val}) disagrees with gradle.properties (v${version_name})"
    fi
  done
else
  fail "README.md not found"
fi

# --- 4. GitHub Release tag (advisory in default mode, required in --strict) ---
if command -v gh >/dev/null 2>&1; then
  latest_release="$(gh release list --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null)" || true
  expected_tag="v${version_name}"

  if [ -z "$latest_release" ]; then
    msg="no GitHub Releases found — expected ${expected_tag}"
    if [ "$STRICT" = true ]; then
      fail "$msg"
    else
      warn "$msg (advisory — run with --strict in release.yml)"
    fi
  elif [ "$latest_release" != "$expected_tag" ]; then
    msg="latest GitHub Release is ${latest_release} but gradle.properties declares ${expected_tag}"
    if [ "$STRICT" = true ]; then
      fail "$msg"
    else
      warn "$msg (advisory — the release workflow will create it)"
    fi
  else
    echo "GitHub Release: OK (${latest_release})"
  fi
else
  echo "GitHub Release: SKIPPED (gh CLI not available)"
fi

# --- Summary ---
if [ "$errors" -gt 0 ]; then
  echo "release-front-door: FAIL ($errors error(s), $warnings warning(s))"
  exit 1
fi

echo "release-front-door: OK (v${version_name}, code ${version_code}, $warnings warning(s))"
