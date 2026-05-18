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
