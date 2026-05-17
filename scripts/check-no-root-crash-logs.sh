#!/usr/bin/env bash
set -euo pipefail

# Guard against accidentally committing local JVM crash logs or process replay
# logs at the repository root. They are useful while debugging, but they add
# noisy megabyte-scale files to fresh clones and do not belong in source.

ROOT="$(git rev-parse --show-toplevel)"

matches="$(
  git -C "$ROOT" ls-files -- \
    ':(top)hs_err_pid*.log' \
    ':(top)replay_pid*.log'
)"

if [[ -n "$matches" ]]; then
  echo "::error::Root JVM crash/replay logs are committed. Move them to .ai/local-crash-logs/<date>/ or delete local copies before committing."
  printf '%s\n' "$matches"
  exit 1
fi

echo "No root JVM crash/replay logs are committed."
