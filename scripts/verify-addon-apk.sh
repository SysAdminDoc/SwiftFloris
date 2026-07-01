#!/usr/bin/env bash
# verify-addon-apk.sh — validate a SwiftFloris addon APK against the universal
# enrolment contract.
#
# ROADMAP matrix #10 — every addon APK (dictionary-pack, theme-pack,
# layout-pack, popup-mapping-pack, language-pack, plus any future native-bearing
# addon) must pass this gate at addon-CI time. The IME's AddonEnumerator runs
# the equivalent checks at runtime and silently skips packs that fail; the
# CI-time gate prevents a doomed APK from ever reaching a user.
#
# See docs/addons/apk-validation.md for the contract this script implements.
#
# Usage:
#   ./scripts/verify-addon-apk.sh path/to/your-addon.apk
#
# Exit codes:
#   0 — all checks pass
#   1 — one or more checks failed (line printed for each)
#   2 — usage error / required tool missing
#
# Required tools:
#   - zipalign  (Android SDK build-tools r33+)
#   - aapt2     (Android SDK build-tools)
#   - apksigner (Android SDK build-tools)
#   - stat, grep, awk (POSIX)
#
# Environment:
#   ANDROID_HOME / ANDROID_SDK_ROOT — Android SDK root. Optional; defaults to
#                  $HOME/Library/Android/sdk (macOS) or
#                  $HOME/Android/Sdk (Linux). Windows-style C:\... paths are
#                  accepted when the script runs under Git Bash. If neither
#                  exists the script falls back to PATH-resolved binaries.

set -eo pipefail

# `set -u` would error on unset positional parameters before the usage check
# fires, so it's deliberately not in the strict-mode line. The case-by-case
# `${VAR:-default}` style below covers the references that need it.

readonly MAX_BUNDLE_BYTES=67108864  # 64 MiB — AddonContract.ADDON_MAX_BUNDLE_BYTES
readonly BANNED_PERMISSIONS=(
    "android.permission.INTERNET"
    "android.permission.ACCESS_NETWORK_STATE"
    "android.permission.ACCESS_WIFI_STATE"
    "android.permission.CHANGE_NETWORK_STATE"
    "android.permission.CHANGE_WIFI_STATE"
)
readonly CONTRACT_BASE="io.github.sysadmindoc.swiftfloris"
readonly REQUIRED_META_KEYS=(
    "${CONTRACT_BASE}.addon.type"
    "${CONTRACT_BASE}.addon.version"
    "${CONTRACT_BASE}.addon.license"
    "${CONTRACT_BASE}.addon.descriptor"
)
readonly REGISTER_ACTION_PREFIX="${CONTRACT_BASE}.action.REGISTER_"

usage() {
    echo "usage: $(basename "$0") path/to/addon.apk" >&2
    exit 2
}

require_tool() {
    local tool="$1"
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "FAIL  required tool not found: $tool" >&2
        return 2
    fi
    return 0
}

normalize_sdk_home() {
    local sdk_home="$1"
    if [ -n "$sdk_home" ] && [ ! -d "$sdk_home" ] && [[ "$sdk_home" =~ ^[A-Za-z]:\\ ]]; then
        sdk_home="${sdk_home//\\//}"
    fi
    printf '%s\n' "$sdk_home"
}

resolve_build_tools_bin() {
    local tool="$1"
    local default_home=""
    if [ -d "${HOME}/Library/Android/sdk" ]; then
        default_home="${HOME}/Library/Android/sdk"
    elif [ -d "${HOME}/Android/Sdk" ]; then
        default_home="${HOME}/Android/Sdk"
    fi
    local sdk_home
    sdk_home="$(normalize_sdk_home "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$default_home}}")"
    if [ -n "$sdk_home" ] && [ -d "$sdk_home/build-tools" ]; then
        local latest
        latest=$(ls -1 "$sdk_home/build-tools" 2>/dev/null | sort -V | tail -1)
        local candidate
        for candidate in "$sdk_home/build-tools/$latest/$tool" "$sdk_home/build-tools/$latest/$tool.exe" "$sdk_home/build-tools/$latest/$tool.bat"; do
            if [ -x "$candidate" ] || [ -f "$candidate" ]; then
                echo "$candidate"
                return 0
            fi
        done
    fi
    command -v "$tool" 2>/dev/null
}

check_alignment() {
    local apk="$1" zipalign="$2"
    if "$zipalign" -c -P 16 -v 4 "$apk" >/dev/null 2>&1; then
        echo "PASS  16 KB native-library alignment"
        return 0
    else
        echo "FAIL  16 KB native-library alignment — rebuild with NDK r28+ (zipalign -P 16 -v 4)"
        return 1
    fi
}

check_bundle_size() {
    local apk="$1"
    local size
    if command -v stat >/dev/null 2>&1; then
        # Try GNU stat first, then BSD stat.
        size=$(stat -c '%s' "$apk" 2>/dev/null || stat -f '%z' "$apk" 2>/dev/null)
    fi
    if [ -z "$size" ]; then
        echo "FAIL  bundle-size check could not stat $apk"
        return 1
    fi
    if [ "$size" -le "$MAX_BUNDLE_BYTES" ]; then
        echo "PASS  bundle size $size bytes (<= $MAX_BUNDLE_BYTES)"
        return 0
    else
        echo "FAIL  bundle size $size bytes exceeds AddonContract.ADDON_MAX_BUNDLE_BYTES ($MAX_BUNDLE_BYTES)"
        return 1
    fi
}

check_permissions() {
    local apk="$1" aapt2="$2"
    local perms aapt2_status
    # Run aapt2 in a subshell so its non-zero exit (e.g. tool corruption,
    # malformed APK) is captured into $aapt2_status rather than aborted by
    # `set -e`. We then explicitly distinguish "tool failed" from "tool
    # succeeded but produced no output" from "tool succeeded and matched
    # nothing" — the previous `|| true` collapsed all three into a silent
    # PASS.
    set +e
    perms=$("$aapt2" dump permissions "$apk" 2>/dev/null)
    aapt2_status=$?
    set -e
    if [ "$aapt2_status" -ne 0 ]; then
        echo "FAIL  aapt2 dump permissions exited $aapt2_status — cannot validate banned permissions"
        return 1
    fi
    if [ -z "$perms" ]; then
        # An APK with zero declared permissions returns empty output. That's
        # actually the PASS case (no banned-permission strings to match),
        # but we report it explicitly so the maintainer can sanity-check
        # against expectation.
        echo "PASS  no permissions declared in APK"
        return 0
    fi
    local found=0
    for perm in "${BANNED_PERMISSIONS[@]}"; do
        if echo "$perms" | grep -qE "uses-permission: name='${perm}'|name='${perm}'"; then
            echo "FAIL  banned permission declared: $perm"
            found=1
        fi
    done
    if [ "$found" -eq 0 ]; then
        echo "PASS  no banned network permissions declared"
        return 0
    fi
    return 1
}

check_register_receiver_and_metadata() {
    local apk="$1" aapt2="$2"
    local manifest aapt2_status
    set +e
    manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk" 2>/dev/null)
    aapt2_status=$?
    set -e
    if [ "$aapt2_status" -ne 0 ]; then
        echo "FAIL  aapt2 dump xmltree exited $aapt2_status — cannot parse AndroidManifest.xml"
        return 1
    fi
    if [ -z "$manifest" ]; then
        # Distinguished from "tool failed": aapt2 succeeded but emitted no
        # output. That's an APK without an AndroidManifest, which is
        # impossible for a valid Android package — call it a failure so a
        # broken `aapt2` build (no output, exit 0) can't silently pass.
        echo "FAIL  AndroidManifest.xml produced no output from aapt2 — APK is malformed or aapt2 is broken"
        return 1
    fi
    if ! echo "$manifest" | grep -Fq "$REGISTER_ACTION_PREFIX"; then
        echo "FAIL  no REGISTER_ADDON intent action found on any receiver"
        return 1
    fi
    echo "PASS  REGISTER_ADDON receiver present"

    local missing=0
    for key in "${REQUIRED_META_KEYS[@]}"; do
        if ! echo "$manifest" | grep -Fq "$key"; then
            echo "FAIL  required meta-data key missing: $key"
            missing=1
        fi
    done
    if [ "$missing" -eq 0 ]; then
        echo "PASS  required meta-data keys (type / version / license / descriptor) present"
    fi
    return $missing
}

check_signing_certificate() {
    local apk="$1" apksigner="$2"
    local signer_output apksigner_status
    set +e
    signer_output=$("$apksigner" verify --print-certs "$apk" 2>/dev/null)
    apksigner_status=$?
    set -e
    # apksigner returns non-zero when the APK is unsigned or signed with an
    # unrecognised scheme. Distinguish that from "tool failed to invoke at
    # all" (status > 1 typically) so a missing apksigner binary doesn't
    # masquerade as an unsigned APK.
    if [ "$apksigner_status" -gt 2 ]; then
        echo "FAIL  apksigner verify exited $apksigner_status — tool error rather than verification failure"
        return 1
    fi
    if echo "$signer_output" | grep -qE "(Signer #[0-9]+|V[0-9]+ Signer):? certificate SHA-256 digest"; then
        echo "PASS  signing certificate present (apksigner --print-certs)"
        return 0
    fi
    echo "FAIL  no v2 / v3 signing certificate found — sign the APK with apksigner before publishing"
    return 1
}

main() {
    if [ "$#" -ne 1 ]; then
        usage
    fi
    local apk="$1"
    if [ ! -f "$apk" ]; then
        echo "FAIL  APK not found: $apk" >&2
        exit 2
    fi

    local zipalign aapt2 apksigner
    zipalign=$(resolve_build_tools_bin zipalign)
    aapt2=$(resolve_build_tools_bin aapt2)
    apksigner=$(resolve_build_tools_bin apksigner)

    if [ -z "$zipalign" ] || [ -z "$aapt2" ] || [ -z "$apksigner" ]; then
        echo "FAIL  required Android SDK tool not found (zipalign / aapt2 / apksigner)" >&2
        echo "      set ANDROID_HOME or put the build-tools on PATH" >&2
        exit 2
    fi

    local rc=0
    echo "Validating $apk against the SwiftFloris addon-APK contract"
    echo "  zipalign:  $zipalign"
    echo "  aapt2:     $aapt2"
    echo "  apksigner: $apksigner"
    echo

    check_alignment "$apk" "$zipalign" || rc=1
    check_bundle_size "$apk" || rc=1
    check_permissions "$apk" "$aapt2" || rc=1
    check_register_receiver_and_metadata "$apk" "$aapt2" || rc=1
    check_signing_certificate "$apk" "$apksigner" || rc=1

    echo
    if [ "$rc" -eq 0 ]; then
        echo "All checks passed."
    else
        echo "One or more checks failed. See docs/addons/apk-validation.md for the contract."
    fi
    exit $rc
}

main "$@"
