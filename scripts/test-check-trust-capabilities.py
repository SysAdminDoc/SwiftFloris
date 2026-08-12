#!/usr/bin/env python3
"""Fixture tests for the semantic trust-capability release gate."""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
from collections.abc import Callable
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-trust-capabilities.py"
FIXTURE_FILES = (
    "app/src/main/config/trust-capabilities.json",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/aidl/dev/patrickgold/florisboard/ime/mcp/IMcpDaemon.aidl",
    "app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/app/prefs/CorrectionPrefs.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/handwriting/StrokeRecognizer.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/McpAndroidDiscoverer.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/McpDaemonDiscoverer.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/McpServiceLifecycle.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/NoNetworkPermissionPolicy.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/HeuristicSmartComposeProvider.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/RewriteProvider.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/SmartComposeProvider.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/translate/InlineTranslator.kt",
    "app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceRecognitionEngineSelection.kt",
    "app/src/test/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/KeyboardKeyAccessibilityTest.kt",
    "app/src/test/kotlin/dev/patrickgold/florisboard/ime/theme/ThemeContrastTest.kt",
    "app/src/test/kotlin/dev/patrickgold/florisboard/ime/window/TouchTargetWcagTest.kt",
    "app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/PendingSettingsScreensScreenshotTest.kt",
    "gradle/libs.versions.toml",
    "gradle/tools.versions.toml",
    "gradle.properties",
    "README.md",
    "CONTRIBUTING.md",
    "docs/THREAT_MODEL.md",
    "docs/PRIVACY_AND_AI.md",
)


def copy_fixture(root: Path) -> None:
    for relative in FIXTURE_FILES:
        source = ROOT / relative
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    accessibility = ROOT / "docs/ACCESSIBILITY.md"
    if accessibility.is_file():
        target = root / "docs/ACCESSIBILITY.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(accessibility, target)


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(CHECKER), "--root", str(root)],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def expect_failure(
    root: Path,
    label: str,
    mutate: Callable[[Path], object],
    needle: str,
) -> str | None:
    copy_fixture(root)
    mutate(root)
    result = run_checker(root)
    if result.returncode != 1 or needle not in result.stdout:
        return (
            f"{label}: expected failure containing {needle!r}; "
            f"exit={result.returncode}\n{result.stdout}"
        )
    return None


def replace(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8-sig")
    if old not in text:
        raise AssertionError(f"fixture mutation source not found in {path}: {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def change_registry_compile_sdk(root: Path) -> None:
    path = root / "app/src/main/config/trust-capabilities.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    data["sdk"]["compile"] = 36
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def remove_enrollment_permission_from_registry(root: Path) -> None:
    path = root / "app/src/main/config/trust-capabilities.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    data["enrollment"]["allowedPermissions"].remove("android.permission.VIBRATE")
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        copy_fixture(fixture)
        passing = run_checker(fixture)
        if passing.returncode != 0:
            print(passing.stdout)
            print("matching live capability data should pass")
            return 1

        cases = (
            (
                "registry SDK drift",
                change_registry_compile_sdk,
                "sdk.compile",
            ),
            (
                "manifest SEND_SMS permission drift",
                lambda root: replace(
                    root / "app/src/main/AndroidManifest.xml",
                    "<application",
                    '<uses-permission android:name="android.permission.SEND_SMS"/>\n\n    <application',
                ),
                "baseApp.usesPermissions",
            ),
            (
                "AIDL surface drift",
                lambda root: replace(
                    root
                    / "app/src/main/aidl/dev/patrickgold/florisboard/ime/mcp/IMcpDaemon.aidl",
                    "String[] listToolNames();",
                    "String[] describeTools();",
                ),
                "mcp.aidlMethods",
            ),
            (
                "dependency catalog drift",
                lambda root: replace(
                    root / "gradle/libs.versions.toml",
                    'sqlcipher-android = "4.17.0"',
                    'sqlcipher-android = "4.18.0"',
                ),
                "storage.sqlcipherVersion",
            ),
            (
                "runtime capability drift",
                lambda root: replace(
                    root
                    / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/"
                    "VoiceRecognitionEngineSelection.kt",
                    "const val AVAILABLE = false",
                    "const val AVAILABLE = true",
                ),
                "optionalCapabilities",
            ),
            (
                "MCP daemon network policy drift",
                lambda root: replace(
                    root
                    / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/"
                    "NoNetworkPermissionPolicy.kt",
                    '"android.permission.INTERNET",',
                    '"android.permission.NETWORK_PERMISSION_REMOVED",',
                ),
                "mcp.daemonNetworkPermissionsRejected",
            ),
            (
                "enrollment permission registry drift",
                remove_enrollment_permission_from_registry,
                "enrollment.allowedPermissions",
            ),
            (
                "runtime enrollment allowlist drift",
                lambda root: replace(
                    root
                    / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/"
                    "NoNetworkPermissionPolicy.kt",
                    '"android.permission.VIBRATE",',
                    '"android.permission.READ_MEDIA_IMAGES",',
                ),
                "enrollment.allowedPermissions",
            ),
            (
                "enrollment allowlist downgraded to a denylist",
                lambda root: replace(
                    root
                    / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/"
                    "NoNetworkPermissionPolicy.kt",
                    "val AllowedPermissions",
                    "val UnusedPermissions",
                ),
                "mcp.daemonNetworkPermissionsRejected",
            ),
            (
                "enrollment allowlist widened to an exfiltration-capable permission",
                lambda root: replace(
                    root
                    / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/"
                    "NoNetworkPermissionPolicy.kt",
                    '"android.permission.VIBRATE",',
                    '"android.permission.SEND_SMS",',
                ),
                "mcp.daemonNetworkPermissionsRejected",
            ),
            (
                "clipboard documentation regression",
                lambda root: (
                    root / "docs/THREAT_MODEL.md"
                ).write_text(
                    (root / "docs/THREAT_MODEL.md").read_text(encoding="utf-8")
                    + "\nClipboard items are AES-256-GCM encrypted at rest\n",
                    encoding="utf-8",
                ),
                "clipboard encryption claim",
            ),
        )

        for label, mutate, needle in cases:
            failure = expect_failure(fixture, label, mutate, needle)
            if failure is not None:
                print(failure)
                return 1

        accessibility = fixture / "docs/ACCESSIBILITY.md"
        if accessibility.is_file():
            failure = expect_failure(
                fixture,
                "accessibility verification regression",
                lambda root: (
                    root / "docs/ACCESSIBILITY.md"
                ).write_text(
                    (root / "docs/ACCESSIBILITY.md").read_text(encoding="utf-8")
                    + "\n**Switch Access verified.**\n",
                    encoding="utf-8",
                ),
                "Switch Access verification claim",
            )
            if failure is not None:
                print(failure)
                return 1

    print("trust capability gate self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
