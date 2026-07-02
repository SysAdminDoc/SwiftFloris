#!/usr/bin/env python3
"""Self-test the targetSdk 37 shadow preflight helper with small fixtures."""

from __future__ import annotations

import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from textwrap import dedent


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "verify-targetsdk37-shadow.py"

spec = importlib.util.spec_from_file_location("verify_targetsdk37_shadow", SCRIPT)
shadow = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(shadow)


def write_fixture(root: Path, target: int = 36, compile_sdk: int = 37, manifest_target: int = 37) -> None:
    (root / "app" / "build" / "intermediates" / "merged_manifest" / "debug" / "processDebugMainManifest").mkdir(
        parents=True,
        exist_ok=True,
    )
    (root / "app" / "build" / "test-results" / "testDebugUnitTest").mkdir(parents=True, exist_ok=True)
    (root / "gradle.properties").write_text(
        dedent(
            f"""
            projectMinSdk=26
            projectTargetSdk={target}
            projectCompileSdk={compile_sdk}
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    shadow.shadow_manifest_path(root).write_text(
        dedent(
            f"""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="{manifest_target}" />
            </manifest>
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    testcases = "\n".join(
        f'  <testcase name="{name}" classname="Gradle Test Run :app:testDebugUnitTest" time="0.0"/>'
        for name in shadow.REQUIRED_REPLAY_TESTS
    )
    (root / "app" / "build" / "test-results" / "testDebugUnitTest" / "TEST-fixture.xml").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<testsuite tests="{len(shadow.REQUIRED_REPLAY_TESTS)}" failures="0" errors="0">\n'
        f"{testcases}\n"
        "</testsuite>\n",
        encoding="utf-8",
    )


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture)
        if shadow.project_sdks(fixture) != (36, 37):
            print("expected SDK parser to read release target and compile SDK")
            return 1
        command = shadow.gradle_command(fixture, 37)
        if "-PprojectTargetSdk=37" not in command or "--tests" in command:
            print(command)
            print("expected shadow command to use project override and avoid Kotest --tests filters")
            return 1
        errors = shadow.validate_outputs(fixture, release_target=36, shadow_target=37)
        if errors:
            print(errors)
            print("expected matching fixture outputs to pass")
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture, manifest_target=36)
        errors = shadow.validate_outputs(fixture, release_target=36, shadow_target=37)
        if not any("targetSdkVersion is 36" in error for error in errors):
            print(errors)
            print("expected stale manifest target to fail")
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture)
        report = fixture / "app" / "build" / "test-results" / "testDebugUnitTest" / "TEST-fixture.xml"
        report.write_text(
            report.read_text(encoding="utf-8").replace("AndroidAdaptiveImeWindowTest", "OtherTest"),
            encoding="utf-8",
        )
        errors = shadow.validate_outputs(fixture, release_target=36, shadow_target=37)
        if not any("AndroidAdaptiveImeWindowTest" in error for error in errors):
            print(errors)
            print("expected missing API 37 replay test to fail")
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture, compile_sdk=36)
        _, compile_sdk = shadow.project_sdks(fixture)
        if compile_sdk >= 37:
            print("expected compileSdk fixture below shadow target")
            return 1

    print("targetSdk shadow preflight self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
