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
#   ANDROID_HOME — Android SDK root. Optional; defaults to
#                  $HOME/Library/Android/sdk (macOS) or
#                  $HOME/Android/Sdk (Linux). If neither exists the
#                  script falls back to PATH-resolved binaries.

set -u

readonly MAX_BUNDLE_BYTES=67108864  # 64 MiB — AddonContract.ADDON_MAX_BUNDLE_BYTES
readonly BANNED_PERMISSIONS=(
    "android.permission.INTERNET"
    "android.permission.ACCESS_NETWORK_STATE"
    "android.permission.ACCESS_WIFI_STATE"
    "android.permission.CHANGE_NETWORK_STATE"
    "android.permission.CHANGE_WIFI_STATE"
)
readonly REQUIRED_META_KEYS=(
    "dev.patrickgold.florisboard.addon.type"
    "dev.patrickgold.florisboard.addon.version"
    "dev.patrickgold.florisboard.addon.license"
    "dev.patrickgold.florisboard.addon.descriptor"
)
readonly REGISTER_ACTION_PREFIX="dev.patrickgold.florisboard.action.REGISTER_"

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

resolve_build_tools_bin() {
    local tool="$1"
    local default_home=""
    if [ -d "${HOME}/Library/Android/sdk" ]; then
        default_home="${HOME}/Library/Android/sdk"
    elif [ -d "${HOME}/Android/Sdk" ]; then
        default_home="${HOME}/Android/Sdk"
    fi
    local sdk_home="${ANDROID_HOME:-$default_home}"
    if [ -n "$sdk_home" ] && [ -d "$sdk_home/build-tools" ]; then
        local latest
        latest=$(ls -1 "$sdk_home/build-tools" 2>/dev/null | sort -V | tail -1)
        if [ -n "$latest" ] && [ -x "$sdk_home/build-tools/$latest/$tool" ]; then
            echo "$sdk_home/build-tools/$latest/$tool"
            return 0
        fi
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
    local perms
    perms=$("$aapt2" dump permissions "$apk" 2>/dev/null || true)
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
    local manifest
    manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk" 2>/dev/null || true)
    if [ -z "$manifest" ]; then
        echo "FAIL  could not parse AndroidManifest.xml via aapt2"
        return 1
    fi
    if ! echo "$manifest" | grep -qE "action.*${REGISTER_ACTION_PREFIX}"; then
        echo "FAIL  no REGISTER_ADDON intent action found on any receiver"
        return 1
    fi
    echo "PASS  REGISTER_ADDON receiver present"

    local missing=0
    for key in "${REQUIRED_META_KEYS[@]}"; do
        if ! echo "$manifest" | grep -qE "name=\"${key}\"|name=\"$key\""; then
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
    local signer_output
    signer_output=$("$apksigner" verify --print-certs "$apk" 2>/dev/null || true)
    if echo "$signer_output" | grep -qE "Signer #1 certificate SHA-256 digest"; then
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
