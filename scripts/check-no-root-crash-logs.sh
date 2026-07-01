#!/usr/bin/env bash
set -euo pipefail

# Guard against leaving local JVM crash logs or process replay logs at the
# repository root. They are useful while debugging, but they make release
# evidence noisy and must live under .ai/local-crash-logs/<date>/ or be deleted.

ROOT="$(git rev-parse --show-toplevel)"

tracked_matches="$(
  git -C "$ROOT" ls-files -- \
    ':(top)hs_err_pid*.log' \
    ':(top)replay_pid*.log'
)"

root_matches="$(
  cd "$ROOT"
  find . -maxdepth 1 -type f \( -name 'hs_err_pid*.log' -o -name 'replay_pid*.log' \) -print \
    | sed 's#^\./##' \
    | sort
)"

status=0
if [[ -n "$tracked_matches" ]]; then
  echo "::error::Root JVM crash/replay logs are tracked. Move them to .ai/local-crash-logs/<date>/ or delete local copies before committing."
  printf '%s\n' "$tracked_matches"
  status=1
fi

if [[ -n "$root_matches" ]]; then
  echo "::error::Root JVM crash/replay logs are present in the repository root. Move useful logs to .ai/local-crash-logs/<date>/ or delete them before collecting release evidence."
  printf '%s\n' "$root_matches"
  status=1
fi

if [[ "$status" -ne 0 ]]; then
  exit "$status"
fi

echo "No root JVM crash/replay logs are tracked or present."
