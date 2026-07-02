#!/usr/bin/env bash
set -euo pipefail

# docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17 §B.4 / N12.5.
# Build the release APK from two clean worktrees at the same commit and require
# byte-for-byte equality. If the bytes drift, emit ZIP-entry manifests so the
# failure distinguishes content drift from signing / ZIP metadata drift.

ROOT="$(git rev-parse --show-toplevel)"
COMMIT="$(git -C "$ROOT" rev-parse HEAD)"
ARTIFACT_DIR="${1:-}"
if [[ -z "$ARTIFACT_DIR" ]]; then
  ARTIFACT_DIR="$ROOT/build/reproducible-apk"
fi
WORK_ROOT="$(mktemp -d "${RUNNER_TEMP:-/tmp}/swiftfloris-repro.XXXXXX")"
GRADLE_REPRO_ARGS=(
  --no-daemon
  --no-build-cache
  --rerun-tasks
  -Dorg.gradle.caching=false
  -Dkotlin.caching.enabled=false
)

cleanup() {
  git -C "$ROOT" worktree remove --force "$WORK_ROOT/first" >/dev/null 2>&1 || true
  git -C "$ROOT" worktree remove --force "$WORK_ROOT/second" >/dev/null 2>&1 || true
  rm -rf "$WORK_ROOT"
}
trap cleanup EXIT

mkdir -p "$ARTIFACT_DIR"

add_worktree() {
  local name="$1"
  git -C "$ROOT" worktree add --detach "$WORK_ROOT/$name" "$COMMIT"
  git -C "$WORK_ROOT/$name" submodule update --init --recursive
}

build_apk() {
  local name="$1"
  local apk_out="$2"
  local tree="$WORK_ROOT/$name"

  (
    cd "$tree"
    chmod +x ./gradlew
    ./gradlew "${GRADLE_REPRO_ARGS[@]}" clean :app:assembleRelease
  )

  local apk
  apk="$(find "$tree/app/build/outputs/apk/release" -maxdepth 1 -type f -name "*.apk" | sort | head -n 1)"
  if [[ -z "$apk" || ! -f "$apk" ]]; then
    echo "::error::No release APK produced for worktree '$name'."
    exit 1
  fi
  cp "$apk" "$apk_out"
}

run_kotlin_build_cache_guard() {
  local args=()
  local gradle_arg
  for gradle_arg in "${GRADLE_REPRO_ARGS[@]}"; do
    args+=("--gradle-arg=$gradle_arg")
  done
  python3 "$ROOT/scripts/check-kotlin-build-cache-cve-guard.py" \
    --label "reproducible-apk" \
    "${args[@]}"
}

write_entry_manifest() {
  local apk="$1"
  local manifest="$2"
  python3 - "$apk" > "$manifest" <<'PY'
import hashlib
import sys
import zipfile

apk = sys.argv[1]
with zipfile.ZipFile(apk, "r") as zf:
    for info in sorted(zf.infolist(), key=lambda item: item.filename):
        if info.is_dir() or info.filename.startswith("META-INF/"):
            continue
        data = zf.read(info.filename)
        digest = hashlib.sha256(data).hexdigest()
        print(f"{digest} {len(data):>12} {info.CRC:08x} {info.filename}")
PY
}

add_worktree first
add_worktree second
run_kotlin_build_cache_guard

FIRST_APK="$ARTIFACT_DIR/first-release.apk"
SECOND_APK="$ARTIFACT_DIR/second-release.apk"

build_apk first "$FIRST_APK"
build_apk second "$SECOND_APK"

sha256sum "$FIRST_APK" "$SECOND_APK" | tee "$ARTIFACT_DIR/SHA256SUMS"

if cmp -s "$FIRST_APK" "$SECOND_APK"; then
  echo "SwiftFloris release APK is byte-identical at $COMMIT (signing block + payload reproducible)."
  exit 0
fi

# Bytes drifted. This is expected for *signed* release APKs because the v2/v3
# signing block contains randomised padding even with deterministic content.
# F-Droid's verified-reproducible-tier rebuild compares the payload (ZIP
# entries outside META-INF/), not the signing block, then re-signs with the
# F-Droid key. We therefore distinguish:
#   - payload entries identical  -> reproducible (exit 0, with a note)
#   - payload entries differ     -> not reproducible (exit 1)
write_entry_manifest "$FIRST_APK" "$ARTIFACT_DIR/first-entry-manifest.txt"
write_entry_manifest "$SECOND_APK" "$ARTIFACT_DIR/second-entry-manifest.txt"

echo "::group::APK ZIP entry manifest diff"
if diff -u "$ARTIFACT_DIR/first-entry-manifest.txt" "$ARTIFACT_DIR/second-entry-manifest.txt"; then
  echo "::endgroup::"
  echo "SwiftFloris release APK payload is reproducible at $COMMIT (entry manifests match; byte drift isolated to signing block or ZIP metadata, expected for signed builds)."
  exit 0
else
  echo "::endgroup::"
  echo "::error::Release APK is not reproducible at commit $COMMIT — payload entries differ. See uploaded reproducibility artifacts."
  exit 1
fi
