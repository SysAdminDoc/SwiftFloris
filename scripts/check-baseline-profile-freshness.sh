#!/usr/bin/env bash
set -euo pipefail

profile_path="${BASELINE_PROFILE_PATH:-app/src/main/baseline-prof.txt}"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "::error::check-baseline-profile-freshness.sh must run inside a git worktree." >&2
  exit 1
fi

if ! git ls-files --error-unmatch "$profile_path" >/dev/null 2>&1; then
  echo "::error::Tracked baseline profile is missing: $profile_path" >&2
  exit 1
fi

if [ ! -s "$profile_path" ]; then
  echo "::error::Baseline profile exists but is empty: $profile_path" >&2
  exit 1
fi

base_ref="${BASELINE_PROFILE_BASE_REF:-}"
if [ -z "$base_ref" ]; then
  current_version="$(grep -E '^projectVersionName=' gradle.properties | cut -d= -f2)"
  current_tag="v$current_version"
  base_ref="$(git tag --merged HEAD --list 'v[0-9]*' --sort=-v:refname | grep -v -x "$current_tag" | head -n 1 || true)"
fi

if [ -z "$base_ref" ]; then
  base_ref="$(git rev-list --max-parents=0 HEAD | tail -n 1)"
fi

if ! git rev-parse --verify "$base_ref^{commit}" >/dev/null 2>&1; then
  echo "::error::Baseline profile base ref does not resolve to a commit: $base_ref" >&2
  exit 1
fi

changed_paths="$(git diff --name-only "$base_ref..HEAD")"
if [ -z "$changed_paths" ]; then
  echo "No changes since $base_ref; baseline profile freshness check passed."
  exit 0
fi

release_relevant_paths="$(printf '%s\n' "$changed_paths" | grep -E \
  '^(app/src/main/(AndroidManifest\.xml|kotlin/|java/|res/)|app/build\.gradle\.kts|benchmark/|build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties|gradle/libs\.versions\.toml)' || true)"
profile_changed="$(printf '%s\n' "$changed_paths" | grep -x "$profile_path" || true)"

if [ -z "$release_relevant_paths" ]; then
  echo "No app or benchmark runtime changes since $base_ref; baseline profile freshness check passed."
  exit 0
fi

if [ -n "$profile_changed" ]; then
  echo "Baseline profile was refreshed since $base_ref."
  exit 0
fi

{
  echo "::error::App or benchmark runtime files changed since $base_ref, but $profile_path was not refreshed."
  echo "Regenerate before releasing, then commit the profile artifact:"
  echo "  ./gradlew :app:assembleBenchmark :benchmark:connectedBenchmarkAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.patrickgold.florisboard.benchmark.BaselineProfileGenerator'"
  echo "Changed release-relevant paths:"
  printf '%s\n' "$release_relevant_paths" | sed 's/^/  - /'
} >&2
exit 1
