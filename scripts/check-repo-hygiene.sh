#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

generated_paths="$(git ls-files | grep -E '(^|/)(\.gradle|\.kotlin|build|out|release|captures)/|^app/build/|^benchmark/build/|^lib/.*/build/' || true)"
if [ -n "$generated_paths" ]; then
  echo "::error::Generated build/report output is tracked. Remove these paths from git and keep them as workflow artifacts only:"
  echo "$generated_paths"
  exit 1
fi

deleted_markdown="$(git status --porcelain=v1 | awk '$1 ~ /D/ && $2 ~ /\.md$/ { print $2 }')"
if [ -n "$deleted_markdown" ]; then
  echo "::error::Deleted Markdown files are present in the working tree. Confirm they are intentional before staging/pushing:"
  echo "$deleted_markdown"
  exit 1
fi

# Root-level only: git ls-files emits paths relative to repo root, so anything
# without a `/` is a top-level entry. Filter to that set, then apply rules.
root_files="$(git ls-files | grep -v '/' || true)"

# Root-level binary / config artefacts that must never be tracked.
# Release APKs / AABs live as GitHub Releases artefacts, not in the tree.
# Keystores never enter the tree. local.properties is per-machine SDK config.
# *.backup* / *.bak files belong under docs/archive/ if kept at all.
root_artefacts="$(printf '%s\n' "$root_files" | grep -E '\.(apk|aab|jks|keystore)$|^local\.properties$|\.(backup|backup-[^/]+|bak)$' || true)"
if [ -n "$root_artefacts" ]; then
  echo "::error::Forbidden root-level artefacts are tracked. APKs/AABs ship via GitHub Releases; keystores stay out of git; *.backup* belongs under docs/archive/:"
  echo "$root_artefacts"
  exit 1
fi

# Large root-level PNGs are a brand-asset smell — move them under
# fastlane/metadata/android/en-US/images/ or app/src/main/res/.
root_pngs="$(printf '%s\n' "$root_files" | grep -E '\.png$' || true)"
if [ -n "$root_pngs" ]; then
  while IFS= read -r png; do
    [ -z "$png" ] && continue
    size="$(wc -c < "$png" 2>/dev/null || echo 0)"
    if [ "$size" -gt 204800 ]; then
      echo "::error::Root-level PNG '$png' is $size bytes (>200 KB). Move large branding assets to fastlane/metadata/android/en-US/images/ or app/src/main/res/."
      exit 1
    fi
  done <<<"$root_pngs"
fi
