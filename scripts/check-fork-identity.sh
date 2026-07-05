#!/usr/bin/env bash
# scripts/check-fork-identity.sh
#
# F-Droid pre-submission fork-identity audit. Verifies that SwiftFloris
# presents clear fork identity separate from upstream FlorisBoard in
# every user-visible surface.
#
# Checks:
#   1. applicationId is io.github.sysadmindoc.swiftfloris
#   2. app_name string resource is "SwiftFloris"
#   3. Fastlane title.txt does not contain "FlorisBoard"
#   4. Fastlane short_description.txt exists and is non-empty
#   5. No localized Fastlane title contains "FlorisBoard"
#   6. FLADDONS_STORE_URL is confirmed removed (was upstream dead code)
#   7. rootProject.name is flagged as cosmetic FlorisBoard reference
#   8. Obtainium and funding metadata do not point users to upstream
#
# Intentional upstream references (not flagged):
#   - AGP namespace dev.patrickgold.florisboard (preserves cherry-pick ergonomics)
#   - Import format documentation mentioning FlorisBoard CSV format
#   - Historical changelog entries about the fork journey
#   - Internal code comments in AndroidManifest.xml
#
# Exits 0 on pass, 1 on any hard failure.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

errors=0
warnings=0

fail() {
  echo "::error::fork-identity: $*"
  errors=$((errors + 1))
}

warn() {
  echo "::warning::fork-identity: $*"
  warnings=$((warnings + 1))
}

ok() {
  echo "fork-identity: OK — $*"
}

# --- 1. applicationId ---
app_id="$(grep -oP "applicationId\s*=\s*\"\K[^\"]*" app/build.gradle.kts | head -1)"
if [ "$app_id" = "io.github.sysadmindoc.swiftfloris" ]; then
  ok "applicationId = $app_id"
elif [ -z "$app_id" ]; then
  fail "could not parse applicationId from app/build.gradle.kts"
else
  fail "applicationId is '$app_id' — expected 'io.github.sysadmindoc.swiftfloris'"
fi

# --- 2. app_name string resource ---
app_name="$(grep -oP '<string name="app_name">\K[^<]*' app/src/main/res/values/strings.xml | head -1)"
if [ "$app_name" = "SwiftFloris" ]; then
  ok "app_name = $app_name"
elif [ -z "$app_name" ]; then
  fail "could not parse app_name from strings.xml"
else
  fail "app_name is '$app_name' — expected 'SwiftFloris'"
fi

# --- 3. Fastlane English title ---
title_file="fastlane/metadata/android/en-US/title.txt"
if [ -f "$title_file" ]; then
  title_content="$(cat "$title_file")"
  if echo "$title_content" | grep -qi "FlorisBoard"; then
    fail "$title_file contains 'FlorisBoard': $title_content"
  else
    ok "title.txt = $title_content"
  fi
else
  fail "missing $title_file"
fi

# --- 4. Fastlane short description ---
short_file="fastlane/metadata/android/en-US/short_description.txt"
if [ -f "$short_file" ] && [ -s "$short_file" ]; then
  ok "short_description.txt exists and is non-empty"
else
  fail "missing or empty $short_file"
fi

# --- 5. Localized titles must not contain "FlorisBoard" ---
if [ -d "fastlane/metadata" ]; then
  while IFS= read -r -d '' title_path; do
    content="$(cat "$title_path")"
    if echo "$content" | grep -qi "FlorisBoard"; then
      # Allow "FlorisBoard" only in the main android/ metadata
      # (changelogs referencing upstream are fine). Titles must be clean.
      relpath="${title_path#$ROOT_DIR/}"
      fail "localized title contains 'FlorisBoard': $relpath ($content)"
    fi
  done < <(find fastlane/metadata -name "title.txt" -print0 2>/dev/null)
fi

# --- 6. Addon store URL (removed) ---
if grep -q 'FLADDONS_STORE_URL' app/build.gradle.kts 2>/dev/null; then
  warn "FLADDONS_STORE_URL build config field still present — it was removed as dead code"
else
  ok "FLADDONS_STORE_URL removed (no upstream addon store reference)"
fi

# --- 7. rootProject.name (hard check for F-Droid reviewer clarity) ---
root_name="$(grep -oP 'rootProject\.name\s*=\s*"\K[^"]*' settings.gradle.kts | head -1)" || true
if [ -n "$root_name" ] && [ "$root_name" = "SwiftFloris" ]; then
  ok "rootProject.name = $root_name"
elif [ -n "$root_name" ]; then
  fail "rootProject.name = '$root_name' — expected 'SwiftFloris' (F-Droid reviewers and reproducibility logs reference this)"
fi

# --- 8. Localized app_name overrides must not say "FlorisBoard" ---
while IFS= read -r -d '' strings_file; do
  if grep -q '<string name="app_name">' "$strings_file"; then
    localized_name="$(grep -oP '<string name="app_name">\K[^<]*' "$strings_file" | head -1)"
    if echo "$localized_name" | grep -qi "FlorisBoard"; then
      relpath="${strings_file#$ROOT_DIR/}"
      fail "localized app_name contains 'FlorisBoard': $relpath ($localized_name)"
    fi
  fi
done < <(find app/src/main/res/values-* -name "strings.xml" -print0 2>/dev/null)

# --- 9. Obtainium manifests must not route users to upstream FlorisBoard ---
for obtainium_manifest in fastlane/obtainium/stable.json fastlane/obtainium/preview.json; do
  if [ -f "$obtainium_manifest" ]; then
    if grep -Eqi 'dev\.patrickgold\.florisboard|github\.com/florisboard/florisboard|"author"[[:space:]]*:[[:space:]]*"florisboard"|FlorisBoard (Stable|Preview)' "$obtainium_manifest"; then
      fail "$obtainium_manifest contains upstream FlorisBoard Obtainium identity"
    else
      ok "$obtainium_manifest uses SwiftFloris identity"
    fi
  fi
done

# --- 10. Funding metadata must not route users to upstream maintainers ---
funding_file=".github/FUNDING.yml"
if [ -f "$funding_file" ]; then
  if grep -Eqi 'patrickgold|paypal\.me/devpatrickgold|florisboard' "$funding_file"; then
    fail "$funding_file routes sponsorship to upstream FlorisBoard identities"
  else
    ok "$funding_file does not contain upstream funding identities"
  fi
else
  ok "no GitHub funding metadata present"
fi

# --- Summary ---
if [ "$errors" -gt 0 ]; then
  echo ""
  echo "fork-identity: FAIL ($errors error(s), $warnings advisory warning(s))"
  exit 1
fi

echo ""
echo "fork-identity: PASS ($warnings advisory warning(s))"
echo "  applicationId:  $app_id"
echo "  app_name:       $app_name"
echo "  Intentional upstream references (namespace, import docs, changelogs) are whitelisted."
