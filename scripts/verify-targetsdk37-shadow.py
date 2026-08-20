#!/usr/bin/env python3
"""Run the targetSdk 37 shadow preflight without changing release metadata."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "http://schemas.android.com/apk/res/android"
DEFAULT_SHADOW_TARGET = 37
REQUIRED_REPLAY_TESTS = (
    "EditorInputConnectionBatchTextAttributeTest",
    "ImeVisibilityConfigurationPolicyTest",
    "AndroidAdaptiveImeWindowTest",
    "AndroidAdaptiveManifestContractTest",
)
SHADOW_TASKS = (
    ":app:verifyNoInternetPermission",
    ":app:processDebugMainManifest",
    ":app:testDebugUnitTest",
    ":app:assembleDebug",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build/test SwiftFloris with -PprojectTargetSdk=37, then verify the "
            "merged manifest and API 37 replay tests without editing gradle.properties."
        ),
    )
    parser.add_argument(
        "--root",
        default=str(ROOT),
        help="Repository root. Defaults to the parent of this script directory.",
    )
    parser.add_argument(
        "--shadow-target",
        type=int,
        default=DEFAULT_SHADOW_TARGET,
        help="Temporary targetSdk to pass via -PprojectTargetSdk. Defaults to 37.",
    )
    parser.add_argument(
        "--skip-gradle",
        action="store_true",
        help="Only validate existing manifest/test outputs. Intended for script self-tests.",
    )
    return parser.parse_args()


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator:
            values[key.strip()] = value.strip()
    return values


def project_sdks(root: Path) -> tuple[int, int]:
    values = read_properties(root / "gradle.properties")
    try:
        release_target = int(values["projectTargetSdk"])
        compile_sdk = int(values["projectCompileSdk"])
    except KeyError as exc:
        raise ValueError(f"gradle.properties missing {exc.args[0]}") from exc
    except ValueError as exc:
        raise ValueError("gradle.properties SDK values must be integers") from exc
    return release_target, compile_sdk


def gradle_executable(root: Path) -> Path:
    return root / ("gradlew.bat" if os.name == "nt" else "gradlew")


def gradle_command(root: Path, shadow_target: int) -> list[str]:
    return [
        str(gradle_executable(root)),
        "--no-daemon",
        "--rerun-tasks",
        f"-PprojectTargetSdk={shadow_target}",
        *SHADOW_TASKS,
    ]


def run_gradle(root: Path, shadow_target: int) -> int:
    command = gradle_command(root, shadow_target)
    print("targetSdk shadow preflight Gradle command:", flush=True)
    print("  " + " ".join(command), flush=True)
    completed = subprocess.run(command, cwd=root, check=False)
    return int(completed.returncode)


def shadow_manifest_path(root: Path) -> Path:
    return root / "app" / "build" / "intermediates" / "merged_manifest" / "debug" / "processDebugMainManifest" / "AndroidManifest.xml"


def manifest_target_sdk(path: Path) -> int:
    if not path.exists():
        raise ValueError(f"merged debug manifest not found: {path}")
    tree = ET.parse(path)
    uses_sdk = tree.getroot().find("uses-sdk")
    if uses_sdk is None:
        raise ValueError(f"merged debug manifest has no uses-sdk element: {path}")
    raw = uses_sdk.attrib.get(f"{{{ANDROID_NS}}}targetSdkVersion")
    if raw is None:
        raise ValueError(f"merged debug manifest has no targetSdkVersion: {path}")
    return int(raw)


def reported_test_names(root: Path) -> set[str]:
    report_dir = root / "app" / "build" / "test-results" / "testDebugUnitTest"
    names: set[str] = set()
    for path in sorted(report_dir.glob("TEST-*.xml")):
        tree = ET.parse(path)
        for testcase in tree.getroot().iter("testcase"):
            for attr in ("name", "classname"):
                value = testcase.attrib.get(attr)
                if value:
                    names.add(value)
                    names.add(value.rsplit(".", 1)[-1])
    return names


def validate_outputs(root: Path, release_target: int, shadow_target: int) -> list[str]:
    errors: list[str] = []
    try:
        actual_target = manifest_target_sdk(shadow_manifest_path(root))
        if actual_target != shadow_target:
            errors.append(f"merged debug manifest targetSdkVersion is {actual_target}, expected {shadow_target}")
    except (ET.ParseError, ValueError) as exc:
        errors.append(str(exc))

    current_release_target, _ = project_sdks(root)
    if current_release_target != release_target:
        errors.append(
            f"gradle.properties projectTargetSdk changed from {release_target} to {current_release_target}; "
            "the shadow preflight must not mutate release metadata"
        )

    names = reported_test_names(root)
    missing = [name for name in REQUIRED_REPLAY_TESTS if name not in names]
    if missing:
        errors.append(f"API 37 replay tests missing from test report: {', '.join(missing)}")
    return errors


def main() -> int:
    args = parse_args()
    root = Path(args.root).resolve()
    try:
        release_target, compile_sdk = project_sdks(root)
    except ValueError as exc:
        print(f"::error::targetsdk37-shadow: {exc}", file=sys.stderr)
        return 1

    if compile_sdk < args.shadow_target:
        print(
            f"::error::targetsdk37-shadow: compileSdk {compile_sdk} is below shadow target {args.shadow_target}",
            file=sys.stderr,
        )
        return 1

    print(
        f"targetSdk shadow preflight: release target={release_target}, "
        f"compileSdk={compile_sdk}, shadow target={args.shadow_target}",
        flush=True,
    )

    if not args.skip_gradle:
        exit_code = run_gradle(root, args.shadow_target)
        if exit_code != 0:
            print(f"targetSdk shadow preflight: FAIL (Gradle exit={exit_code})", file=sys.stderr)
            return exit_code

    errors = validate_outputs(root, release_target, args.shadow_target)
    if errors:
        for error in errors:
            print(f"::error::targetsdk37-shadow: {error}", file=sys.stderr)
        print(f"targetSdk shadow preflight: FAIL ({len(errors)} error(s))", file=sys.stderr)
        return 1

    print(
        "targetSdk shadow preflight: OK "
        f"(manifest targetSdk={args.shadow_target}; "
        f"reported tests={', '.join(REQUIRED_REPLAY_TESTS)}; "
        f"gradle.properties remains projectTargetSdk={release_target})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
