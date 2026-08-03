#!/usr/bin/env python3
"""Fail when public trust claims drift from live SwiftFloris capability data."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REGISTRY_PATH = Path("app/src/main/config/trust-capabilities.json")
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_NAME = f"{{{ANDROID_NS}}}name"
ANDROID_PROTECTION_LEVEL = f"{{{ANDROID_NS}}}protectionLevel"
TOOLS_NS = "http://schemas.android.com/tools"
TOOLS_NODE = f"{{{TOOLS_NS}}}node"
NETWORK_PERMISSIONS = {
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.INTERNET",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Derive high-risk trust facts from the manifest, AIDL, Gradle "
            "catalogs, storage code, capability defaults, and public copy."
        ),
    )
    parser.add_argument("--root", default=str(ROOT), help="Repository root.")
    parser.add_argument(
        "--skip-docs",
        action="store_true",
        help="Validate the machine contract only; intended for focused diagnostics.",
    )
    return parser.parse_args()


def read_text(root: Path, relative_path: str | Path) -> str:
    path = root / relative_path
    try:
        return path.read_text(encoding="utf-8-sig")
    except FileNotFoundError as exc:
        raise ValueError(f"missing required file: {Path(relative_path).as_posix()}") from exc


def read_json(root: Path, relative_path: str | Path) -> Any:
    try:
        return json.loads(read_text(root, relative_path))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{Path(relative_path).as_posix()}: invalid JSON: {exc}") from exc


def parse_properties(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator:
            values[key.strip()] = value.strip()
    return values


def parse_version_catalog(text: str) -> dict[str, str]:
    versions: dict[str, str] = {}
    in_versions = False
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            in_versions = line == "[versions]"
            continue
        if not in_versions:
            continue
        match = re.fullmatch(r'([A-Za-z0-9_.-]+)\s*=\s*"([^"]+)"', line)
        if match:
            versions[match.group(1)] = match.group(2)
    return versions


def strip_comments(text: str) -> str:
    without_blocks = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", without_blocks)


def parse_manifest(root: Path) -> tuple[list[str], list[str]]:
    path = root / "app/src/main/AndroidManifest.xml"
    try:
        manifest = ET.parse(path).getroot()
    except FileNotFoundError as exc:
        raise ValueError("missing required file: app/src/main/AndroidManifest.xml") from exc
    except ET.ParseError as exc:
        raise ValueError(f"app/src/main/AndroidManifest.xml: invalid XML: {exc}") from exc

    uses_permissions = sorted(
        element.attrib[ANDROID_NAME]
        for element in manifest.findall("uses-permission")
        if ANDROID_NAME in element.attrib
        and element.attrib.get(TOOLS_NODE) != "remove"
    )
    signature_permissions = sorted(
        element.attrib[ANDROID_NAME]
        for element in manifest.findall("permission")
        if ANDROID_NAME in element.attrib
        and "signature" in element.attrib.get(ANDROID_PROTECTION_LEVEL, "").split("|")
    )
    return uses_permissions, signature_permissions


def parse_aidl_methods(text: str) -> list[str]:
    code = strip_comments(text)
    return sorted(
        match.group(1)
        for match in re.finditer(
            r"\b(?:boolean|byte|char|double|float|int|long|String|String\[\]|void)\s+"
            r"([A-Za-z_][A-Za-z0-9_]*)\s*\(",
            code,
        )
    )


def source_section(text: str, start: str, end: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        return ""
    end_index = text.find(end, start_index + len(start))
    return text[start_index:] if end_index < 0 else text[start_index:end_index]


def derive_optional_capabilities(root: Path) -> dict[str, str]:
    voice_selection = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/"
        "VoiceRecognitionEngineSelection.kt",
    )
    ime_service = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt",
    )
    application = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt",
    )
    correction_prefs = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/app/prefs/CorrectionPrefs.kt",
    )
    smart_compose = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/"
        "SmartComposeProvider.kt",
    )
    heuristic_compose = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/"
        "HeuristicSmartComposeProvider.kt",
    )
    rewrite = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/"
        "RewriteProvider.kt",
    )
    translator = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/translate/InlineTranslator.kt",
    )
    handwriting = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/handwriting/StrokeRecognizer.kt",
    )
    mcp_lifecycle = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/McpServiceLifecycle.kt",
    )

    facts: dict[str, str] = {}
    facts["externalVoiceImeHandoff"] = (
        "shipped"
        if "switchInputMethod(method.id, subtype)" in ime_service
        and 'mode != "voice"' in ime_service
        else "absent"
    )
    facts["localVoiceRecognizer"] = (
        "preview_only"
        if re.search(
            r"object\s+VoiceLocalRecognizerRuntime\s*\{\s*const\s+val\s+AVAILABLE\s*=\s*false",
            voice_selection,
        )
        else "shipped"
    )
    heuristic_pref = source_section(
        correction_prefs,
        "val heuristicSmartCompose",
        "\n}",
    )
    facts["heuristicSmartCompose"] = (
        "shipped_opt_in"
        if "HeuristicSmartComposeProvider(this)" in application
        and "default = false" in heuristic_pref
        and "class HeuristicSmartComposeProvider" in heuristic_compose
        else "absent"
    )
    facts["modelSmartCompose"] = (
        "contract_only"
        if "SmartComposeResult.NoSuggestion" in smart_compose
        and "override val activeModel: LiteRtModelDescriptor? = null" in heuristic_compose
        else "shipped"
    )
    facts["rewriteModel"] = (
        "contract_only"
        if "private var current: RewriteProvider = NoOpRewriteProvider" in rewrite
        and "RewriteResult.Unavailable" in rewrite
        else "shipped"
    )
    facts["translationRuntime"] = (
        "contract_only"
        if "private var current: InlineTranslator = InlineTranslator.Default" in translator
        and "TranslationResult.Unavailable" in translator
        else "shipped"
    )
    facts["handwritingRecognizer"] = (
        "contract_only"
        if "private var current: StrokeRecognizer = StrokeRecognizer.Default" in handwriting
        and "StrokeRecognitionResult.NoRecognition" in handwriting
        else "shipped"
    )
    facts["mcpBinderBridge"] = (
        "shipped"
        if "McpClientRegistry.setActive(AndroidMcpClient(binderLookup))" in mcp_lifecycle
        else "contract_only"
    )
    return facts


def derive_mcp_network_policy(root: Path) -> bool:
    discoverer = strip_comments(
        read_text(
            root,
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/McpAndroidDiscoverer.kt",
        )
    )
    trust_core = strip_comments(
        read_text(
            root,
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/McpDaemonDiscoverer.kt",
        )
    )
    policy = strip_comments(
        read_text(
            root,
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/"
            "NoNetworkPermissionPolicy.kt",
        )
    )
    return (
        "GET_PERMISSIONS" in discoverer
        and "readRequestedPermissions(pm, packageName)" in discoverer
        and "NoNetworkPermissionPolicy.firstDenied(snapshot.requestedPermissions)" in discoverer
        and "NoNetworkPermissionPolicy.firstDenied(cand.requestedPermissions)" in trust_core
        and all(permission in policy for permission in NETWORK_PERMISSIONS)
    )


def validate_registry(root: Path, registry: dict[str, Any]) -> list[str]:
    errors: list[str] = []

    def expect(label: str, actual: Any, expected: Any) -> None:
        if actual != expected:
            errors.append(f"{REGISTRY_PATH.as_posix()}: {label} is {actual!r}; live value is {expected!r}")

    gradle = parse_properties(read_text(root, "gradle.properties"))
    tools = parse_version_catalog(read_text(root, "gradle/tools.versions.toml"))
    libraries = parse_version_catalog(read_text(root, "gradle/libs.versions.toml"))
    for key in ("projectMinSdk", "projectTargetSdk", "projectCompileSdk"):
        if key not in gradle:
            errors.append(f"gradle.properties: missing {key}")
    if "buildTools" not in tools:
        errors.append("gradle/tools.versions.toml: missing buildTools")
    if "sqlcipher-android" not in libraries:
        errors.append("gradle/libs.versions.toml: missing sqlcipher-android")
    if errors:
        return errors

    expect("schemaVersion", registry.get("schemaVersion"), 1)
    sdk = registry.get("sdk", {})
    expect("sdk.min", sdk.get("min"), int(gradle["projectMinSdk"]))
    expect("sdk.target", sdk.get("target"), int(gradle["projectTargetSdk"]))
    expect("sdk.compile", sdk.get("compile"), int(gradle["projectCompileSdk"]))
    expect("sdk.buildTools", sdk.get("buildTools"), tools["buildTools"])

    uses_permissions, signature_permissions = parse_manifest(root)
    base_app = registry.get("baseApp", {})
    expect("baseApp.usesPermissions", base_app.get("usesPermissions"), uses_permissions)
    expect(
        "baseApp.declaredSignaturePermissions",
        base_app.get("declaredSignaturePermissions"),
        signature_permissions,
    )
    expect(
        "baseApp.networkPermission",
        base_app.get("networkPermission"),
        bool(set(uses_permissions) & NETWORK_PERMISSIONS),
    )

    clipboard_source = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/"
        "ClipboardDatabase.kt",
    )
    clipboard_history = source_section(
        clipboard_source,
        "abstract class ClipboardHistoryDatabase",
        "@Serializable",
    )
    clipboard_files = source_section(
        clipboard_source,
        "abstract class ClipboardFilesDatabase",
        "\n}",
    )
    clipboard_file_storage = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/"
        "ClipboardFileStorage.kt",
    )
    dictionary_source = read_text(
        root,
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt",
    )
    storage = registry.get("storage", {})
    clipboard_history_fact = (
        "plaintext_room"
        if "Room" in clipboard_history
        and ".databaseBuilder(" in clipboard_history
        and ".openHelperFactory(" not in clipboard_history
        else "encrypted_or_unknown"
    )
    clipboard_media_fact = (
        "plaintext_app_private_files"
        if "noBackupFilesDir" in clipboard_file_storage
        and ".openHelperFactory(" not in clipboard_files
        else "encrypted_or_unknown"
    )
    dictionary_fact = (
        "sqlcipher_room"
        if ".openHelperFactory(factory)" in dictionary_source
        else "plaintext_or_unknown"
    )
    expect("storage.clipboardHistory", storage.get("clipboardHistory"), clipboard_history_fact)
    expect("storage.clipboardMedia", storage.get("clipboardMedia"), clipboard_media_fact)
    expect("storage.personalDictionary", storage.get("personalDictionary"), dictionary_fact)
    expect(
        "storage.sqlcipherVersion",
        storage.get("sqlcipherVersion"),
        libraries["sqlcipher-android"],
    )

    aidl = read_text(
        root,
        "app/src/main/aidl/dev/patrickgold/florisboard/ime/mcp/IMcpDaemon.aidl",
    )
    mcp = registry.get("mcp", {})
    expect("mcp.appRole", mcp.get("appRole"), "aidl_client")
    expect("mcp.transport", mcp.get("transport"), "local_android_binder")
    expect("mcp.aidlMethods", mcp.get("aidlMethods"), parse_aidl_methods(aidl))
    expect(
        "mcp.daemonNetworkPermissionsRejected",
        mcp.get("daemonNetworkPermissionsRejected"),
        derive_mcp_network_policy(root),
    )

    expect(
        "optionalCapabilities",
        registry.get("optionalCapabilities"),
        derive_optional_capabilities(root),
    )

    automated_contracts = []
    accessibility_contract_files = {
        "semantic_labels": (
            "app/src/test/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/"
            "KeyboardKeyAccessibilityTest.kt"
        ),
        "theme_contrast": (
            "app/src/test/kotlin/dev/patrickgold/florisboard/ime/theme/ThemeContrastTest.kt"
        ),
        "touch_targets": (
            "app/src/test/kotlin/dev/patrickgold/florisboard/ime/window/TouchTargetWcagTest.kt"
        ),
        "visual_regression": (
            "app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/"
            "PendingSettingsScreensScreenshotTest.kt"
        ),
    }
    for name, relative_path in accessibility_contract_files.items():
        if (root / relative_path).is_file():
            automated_contracts.append(name)
    accessibility = registry.get("accessibility", {})
    expect(
        "accessibility.automatedContracts",
        accessibility.get("automatedContracts"),
        sorted(automated_contracts),
    )
    expect(
        "accessibility.manualAssistiveTechnologyReleaseGate",
        accessibility.get("manualAssistiveTechnologyReleaseGate"),
        False,
    )
    return errors


def validate_public_copy(root: Path, registry: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    docs = {
        "README.md": read_text(root, "README.md"),
        "CONTRIBUTING.md": read_text(root, "CONTRIBUTING.md"),
        "docs/THREAT_MODEL.md": read_text(root, "docs/THREAT_MODEL.md"),
        "docs/PRIVACY_AND_AI.md": read_text(root, "docs/PRIVACY_AND_AI.md"),
    }
    accessibility_path = root / "docs/ACCESSIBILITY.md"
    if accessibility_path.is_file():
        docs["docs/ACCESSIBILITY.md"] = accessibility_path.read_text(encoding="utf-8-sig")
    normalized_docs = {
        path: re.sub(r"\s+", " ", text)
        for path, text in docs.items()
    }

    def require(path: str, needle: str, label: str) -> None:
        normalized_needle = re.sub(r"\s+", " ", needle)
        if normalized_needle not in normalized_docs[path]:
            errors.append(f"{path}: missing {label}: {needle!r}")

    def forbid(path: str, needle: str, label: str) -> None:
        normalized_needle = re.sub(r"\s+", " ", needle)
        if normalized_needle in normalized_docs[path]:
            errors.append(f"{path}: stale {label}: {needle!r}")

    sdk = registry["sdk"]
    sqlcipher_version = registry["storage"]["sqlcipherVersion"]
    require(
        "CONTRIBUTING.md",
        f"compile SDK {sdk['compile']} and Build Tools {sdk['buildTools']}",
        "derived SDK prerequisite",
    )
    forbid(
        "CONTRIBUTING.md",
        "compile SDK 36 and Build Tools",
        "compile-SDK prerequisite",
    )

    threat = "docs/THREAT_MODEL.md"
    clipboard_history = registry["storage"]["clipboardHistory"]
    if clipboard_history == "encrypted_or_unknown":
        require(
            threat,
            "Clipboard history is SQLCipher-encrypted at rest when the local SQLCipher provider is available",
            "encrypted clipboard disclosure",
        )
        forbid(
            threat,
            "Clipboard history is currently a plaintext Room database",
            "plaintext clipboard disclosure",
        )
    else:
        require(
            threat,
            "Clipboard history is currently a plaintext Room database",
            "plaintext clipboard disclosure",
        )
    require(
        threat,
        f"(`net.zetetic:sqlcipher-android` {sqlcipher_version})",
        "derived SQLCipher version",
    )
    require(
        threat,
        "client-side `IMcpDaemon.aidl` Binder contract",
        "MCP AIDL client disclosure",
    )
    for permission in registry["baseApp"]["usesPermissions"]:
        require(threat, f"`{permission}`", f"manifest permission {permission}")
    forbid(
        threat,
        "Clipboard items are AES-256-GCM encrypted at rest",
        "obsolete clipboard encryption claim",
    )
    forbid(
        threat,
        "IME does not expose AIDL services beyond the platform `InputMethodService`",
        "AIDL absence claim",
    )
    forbid(threat, "4.16.0", "SQLCipher version")
    forbid(
        threat,
        "shows only `VIBRATE`, `POST_NOTIFICATIONS`",
        "incomplete permission checklist",
    )

    privacy = "docs/PRIVACY_AND_AI.md"
    require(
        privacy,
        "No Bergamot runtime addon currently ships",
        "translation runtime status",
    )
    require(
        privacy,
        "The optional model-backed Smart Compose runtime does not currently ship",
        "model Smart Compose runtime status",
    )
    require(
        privacy,
        "No handwriting recognizer addon currently ships",
        "handwriting runtime status",
    )
    if registry["mcp"]["daemonNetworkPermissionsRejected"]:
        forbid(
            privacy,
            "SwiftFloris does not currently inspect or reject a daemon's network permissions",
            "stale MCP daemon trust boundary",
        )
    else:
        require(
            privacy,
            "SwiftFloris does not currently inspect or reject a daemon's network permissions",
            "MCP daemon trust boundary",
        )
    forbid(
        privacy,
        "The actual translator is the **Bergamot WASM runtime** delivered",
        "delivered translation runtime claim",
    )
    forbid(
        privacy,
        "The actual completion engine is **Gemma 3 270M Q4",
        "delivered model runtime claim",
    )
    forbid(
        privacy,
        "Recognizer engine is delivered as a separately-installed",
        "delivered handwriting runtime claim",
    )
    if not registry["mcp"]["daemonNetworkPermissionsRejected"]:
        forbid(
            privacy,
            "they cannot themselves declare `INTERNET` and remain enrollable",
            "MCP network-permission rejection claim",
        )

    readme = "README.md"
    require(
        readme,
        "app/src/main/config/trust-capabilities.json",
        "machine-readable trust registry link",
    )
    require(
        readme,
        "MCP daemon packages are a separate trust boundary",
        "MCP daemon trust disclosure",
    )
    if registry["mcp"]["daemonNetworkPermissionsRejected"]:
        require(
            readme,
            "packages requesting network permissions are rejected before trust or binding",
            "MCP daemon network-permission gate",
        )
    require(
        readme,
        "None of the Bergamot, LiteRT-LM, handwriting, or local voice recognizer runtimes currently ships",
        "optional runtime status",
    )
    for permission in registry["baseApp"]["usesPermissions"]:
        require(readme, f"`{permission}`", f"manifest permission {permission}")
    forbid(
        readme,
        "Native runtimes for optional capabilities (LiteRT-LM, Bergamot, librime, ML",
        "optional runtimes shipped claim",
    )

    accessibility = docs.get("docs/ACCESSIBILITY.md")
    if accessibility is not None:
        require(
            "docs/ACCESSIBILITY.md",
            "Manual assistive-technology checks are not part of the automated release gate",
            "manual verification boundary",
        )
        forbid(
            "docs/ACCESSIBILITY.md",
            "**Switch Access verified.**",
            "Switch Access verification claim",
        )
        forbid(
            "docs/ACCESSIBILITY.md",
            "Touch input must route correctly while magnified. Verified",
            "current magnification verification claim",
        )
    return errors


def main() -> int:
    args = parse_args()
    root = Path(args.root).resolve()
    try:
        registry = read_json(root, REGISTRY_PATH)
        if not isinstance(registry, dict):
            raise ValueError(f"{REGISTRY_PATH.as_posix()}: root must be a JSON object")
        errors = validate_registry(root, registry)
        if not args.skip_docs:
            errors.extend(validate_public_copy(root, registry))
    except (OSError, ValueError, TypeError, KeyError) as exc:
        print(f"trust capability gate: FAIL\n- {exc}")
        return 1

    if errors:
        print("trust capability gate: FAIL")
        for error in errors:
            print(f"- {error}")
        return 1
    print("trust capability gate: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
