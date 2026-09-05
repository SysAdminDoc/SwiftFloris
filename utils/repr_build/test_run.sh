#!/bin/bash
#
# Self-test for utils/repr_build/run.sh.
#
# The reproducible build runs inside a container that is removed as soon as the
# assembly returns. Until 2026-09-05 the outputs were copied out only when the
# assembly succeeded, so a failed build destroyed the one copy of its own logs,
# and the function returned the exit status of `docker rm` rather than the
# assembly's, which made a failed reproducible build look like a pass.
#
# These cases run `docker_run_assemble` against a stubbed `docker`, so they need
# neither Docker nor a build.

# Deliberately no `set -u`: run.sh reads optional signing variables such as
# FLSEC_SIGNING_ENABLED without defaults, and is invoked without nounset in
# normal use. Turning it on here would fail the sourced script rather than test
# the behaviour under examination.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_SH="$SCRIPT_DIR/run.sh"
failures=0

fail() {
  echo "FAIL: $*"
  failures=$(( failures + 1 ))
}

# Builds a throwaway workspace with a fake `docker` on PATH.
#   $1 exit status the stubbed assemble should return
#   $2 whether the stubbed container has an out/ directory (yes|no)
make_workspace() {
  local assemble_status="$1"
  local has_output="$2"
  local workspace
  workspace="$(mktemp -d)"
  mkdir -p "$workspace/bin"

  cat > "$workspace/bin/docker" <<STUB
#!/bin/bash
case "\$1" in
  run)
    # The real script passes --name <container> before the image.
    echo "stub: docker run" >> "$workspace/calls.log"
    exit $assemble_status
    ;;
  cp)
    echo "stub: docker cp" >> "$workspace/calls.log"
    if [ "$has_output" = "yes" ]; then
      dest="\$3"
      mkdir -p "\$dest/out"
      echo "build log from a run that exited $assemble_status" > "\$dest/out/build.log"
      exit 0
    fi
    exit 1
    ;;
  rm)
    echo "stub: docker rm" >> "$workspace/calls.log"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
STUB
  chmod +x "$workspace/bin/docker"

  cat > "$workspace/bin/uuidgen" <<'STUB'
#!/bin/bash
echo "testcontainer"
STUB
  chmod +x "$workspace/bin/uuidgen"

  echo "$workspace"
}

run_assemble() {
  local workspace="$1"
  (
    PATH="$workspace/bin:$PATH"
    # shellcheck source=/dev/null
    source "$RUN_SH"
    cd "$workspace" || exit 99
    docker_run_assemble "release" "$workspace/final_out"
  )
}

# --- a failed assembly still yields its evidence, and still reports failure ---
workspace="$(make_workspace 7 yes)"
run_assemble "$workspace"
status=$?
if [ "$status" -ne 7 ]; then
  fail "a failed assembly must propagate its own exit code, got $status"
fi
if [ ! -f "$workspace/final_out/build.log" ]; then
  fail "a failed assembly must still copy its output out of the container"
fi
if [ "$(grep -c 'docker rm' "$workspace/calls.log")" -ne 1 ]; then
  fail "the container must be removed exactly once"
fi
rm -rf "$workspace"

# --- a successful assembly is unchanged ---
workspace="$(make_workspace 0 yes)"
run_assemble "$workspace"
status=$?
if [ "$status" -ne 0 ]; then
  fail "a successful assembly must return 0, got $status"
fi
if [ ! -f "$workspace/final_out/build.log" ]; then
  fail "a successful assembly must copy its output"
fi
rm -rf "$workspace"

# --- a container that died before producing out/ must not abort the cleanup ---
workspace="$(make_workspace 1 no)"
output="$(run_assemble "$workspace" 2>&1)"
status=$?
if [ "$status" -ne 1 ]; then
  fail "an assembly with no output must still propagate its exit code, got $status"
fi
case "$output" in
  *"no out/ directory to copy"*) ;;
  *) fail "a missing out/ directory must be reported, got: $output" ;;
esac
if [ "$(grep -c 'docker rm' "$workspace/calls.log")" -ne 1 ]; then
  fail "the container must be removed even when there is nothing to copy"
fi
rm -rf "$workspace"

if [ "$failures" -ne 0 ]; then
  echo "reproducible-build run.sh self-test: FAIL ($failures)"
  exit 1
fi
echo "reproducible-build run.sh self-test: PASS"
