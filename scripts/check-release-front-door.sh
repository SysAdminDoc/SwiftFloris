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
#   4. Exact local tag, origin tag, and GitHub Release exist for
#      projectVersionName once public surfaces claim that version
#
# Publication checks are hard failures by default because public install
# trust breaks when README/F-Droid metadata claim a version that GitHub
# Releases does not serve. Pass --allow-unpublished only while preparing
# a local unpublished version before README/F-Droid surfaces claim it.
#
# Local surfaces (gradle.properties, fastlane, README.md) are always
# hard failures regardless of --strict.
#
# Exits 0 on pass, 1 on any rule violation.

set -euo pipefail

allow_unpublished=false
for arg in "$@"; do
  case "$arg" in
    --allow-unpublished) allow_unpublished=true ;;
    --strict) ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

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

public_surfaces_claim_release=false

mark_public_claim() {
  local value="$1"
  if [ -n "$value" ] && [ "$value" = "$version_name" ]; then
    public_surfaces_claim_release=true
  fi
}

surface_mismatch() {
  local surface="$1"
  local actual="$2"
  if $allow_unpublished; then
    warn "${surface} reports v${actual} but gradle.properties declares v${version_name} (allowed only for unpublished local prep)"
  else
    fail "${surface} reports v${actual} but gradle.properties declares v${version_name}"
  fi
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

  mark_public_claim "$badge_version"
  mark_public_claim "$status_version"
  mark_public_claim "$releases_version"

  if [ -z "$readme_version" ]; then
    fail "could not find a version string in README.md (checked badge, status line, recent releases)"
  elif [ "$readme_version" != "$version_name" ]; then
    surface_mismatch "README.md" "$readme_version"
  else
    echo "README.md version: OK (v${readme_version})"
  fi

  # Cross-check: all three sources should agree if present
  for src_name in badge status releases; do
    eval "src_val=\${${src_name}_version}"
    if [ -n "$src_val" ] && [ "$src_val" != "$version_name" ]; then
      surface_mismatch "README.md ${src_name} version" "$src_val"
    fi
  done
else
  fail "README.md not found"
fi

# --- 4. Public metadata and publication proof ---
fdroid_yaml="fdroid/io.github.sysadmindoc.swiftfloris.yml"
if [ -f "$fdroid_yaml" ]; then
  fdroid_current="$(grep -oP 'CurrentVersion:\s*"\K[0-9]+\.[0-9]+\.[0-9]+' "$fdroid_yaml" | head -1)" || true
  fdroid_commit="$(grep -oP 'commit:\s*v\K[0-9]+\.[0-9]+\.[0-9]+' "$fdroid_yaml" | head -1)" || true
  mark_public_claim "$fdroid_current"
  mark_public_claim "$fdroid_commit"
  for fdroid_source in current commit; do
    eval "fdroid_value=\${fdroid_${fdroid_source}}"
    if [ -n "$fdroid_value" ] && [ "$fdroid_value" != "$version_name" ]; then
      surface_mismatch "${fdroid_yaml} ${fdroid_source}" "$fdroid_value"
    fi
  done
fi

publication_required=true
if $allow_unpublished; then
  if $public_surfaces_claim_release; then
    fail "--allow-unpublished cannot pass because README.md or F-Droid metadata already claims v${version_name}"
  else
    publication_required=false
    warn "publication proof relaxed by --allow-unpublished; do not publish README/F-Droid claims until tags and GitHub Release exist"
  fi
fi

publication_problem() {
  if $publication_required; then
    fail "$*"
  else
    warn "$*"
  fi
}

expected_tag="v${version_name}"

if git rev-parse -q --verify "refs/tags/${expected_tag}" >/dev/null; then
  echo "local tag: OK (${expected_tag})"
else
  publication_problem "missing local tag ${expected_tag}; create the tag or revert the public version bump"
fi

origin_url="$(git remote get-url origin 2>/dev/null || true)"
if [ -z "$origin_url" ]; then
  publication_problem "origin remote missing; cannot verify remote tag ${expected_tag}"
else
  if git ls-remote --exit-code --tags --refs origin "refs/tags/${expected_tag}" >/dev/null 2>&1; then
    echo "origin tag: OK (${expected_tag})"
  else
    publication_problem "missing origin tag ${expected_tag}; push the tag or revert the public version bump"
  fi
fi

gh_bin="${GH_BIN:-gh}"
if command -v "$gh_bin" >/dev/null 2>&1; then
  release_tag="$("$gh_bin" release view "$expected_tag" --json tagName --jq '.tagName' 2>/dev/null)" || true
  if [ "$release_tag" = "$expected_tag" ]; then
    echo "GitHub Release: OK (${release_tag})"
  else
    publication_problem "GitHub Release ${expected_tag} not found; publish the release or revert the public version bump"
  fi
else
  publication_problem "${gh_bin} CLI not available; cannot verify GitHub Release ${expected_tag}"
fi

# --- 5. Developer verification guidance freshness ---
# After Q3 2026, the developer-verification README section must have been
# reviewed. The "(reassess Q3 2026)" tag triggers a warning if the current
# date is past 2026-09-01 and the tag is still present — it means the
# guidance was not updated for the enforcement window.
if grep -q "reassess Q3 2026" README.md 2>/dev/null; then
  current_date="$(date -u +%Y%m%d 2>/dev/null || echo 20260101)"
  if [ "$current_date" -ge "20260901" ]; then
    fail "README.md developer-verification section still says 'reassess Q3 2026' but we are past that date — update the guidance"
  else
    echo "developer-verification: OK (reassessment date not yet reached)"
  fi
fi

# --- 6. Trust-critical localization coverage ---
# Resource coverage is a local, deterministic gate.  It distinguishes the
# reviewed English UI from partial translation/fallback and keeps existing
# translated-resource floors from regressing.  The checker emits only keys,
# counts, and source locations; it never reads user data.
if command -v python >/dev/null 2>&1; then
  if python scripts/check-locale-coverage.py --check; then
    echo "locale coverage: OK"
  else
    fail "trust-critical locale coverage gate failed"
  fi
else
  fail "python is required for the trust-critical locale coverage gate"
fi

# --- Summary ---
if [ "$errors" -gt 0 ]; then
  echo "release-front-door: FAIL ($errors error(s), $warnings warning(s))"
  exit 1
fi

echo "release-front-door: OK (v${version_name}, code ${version_code}, $warnings warning(s))"
