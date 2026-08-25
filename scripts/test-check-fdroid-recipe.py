#!/usr/bin/env python3
"""Self-test for the F-Droid recipe gate.

Each fixture reintroduces exactly one of the defects the gate exists to catch,
so a rule that stops working stops passing here too.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from shutil import copy2
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-fdroid-recipe.py"
RECIPE_REL = Path("fdroid/io.github.sysadmindoc.swiftfloris.yml")
BUILD_REL = Path("app/build.gradle.kts")


def build_fixture(root: Path) -> None:
    (root / "scripts").mkdir(parents=True, exist_ok=True)
    copy2(CHECKER, root / "scripts" / CHECKER.name)
    (root / RECIPE_REL.parent).mkdir(parents=True, exist_ok=True)
    (root / BUILD_REL.parent).mkdir(parents=True, exist_ok=True)
    copy2(ROOT / RECIPE_REL, root / RECIPE_REL)
    copy2(ROOT / BUILD_REL, root / BUILD_REL)


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(root / "scripts" / CHECKER.name), str(root)],
        cwd=root,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def expect_pass(root: Path, what: str) -> int:
    result = run_checker(root)
    if result.returncode != 0:
        print(result.stdout)
        print(f"expected {what} to pass")
        return 1
    return 0


def expect_fail(root: Path, needle: str, what: str) -> int:
    result = run_checker(root)
    if result.returncode != 1 or needle not in result.stdout:
        print(result.stdout)
        print(f"expected {what} to fail with {needle!r}")
        return 1
    return 0


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        build_fixture(fixture)
        if expect_pass(fixture, "the checked-in recipe"):
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        build_fixture(fixture)
        recipe = fixture / RECIPE_REL
        recipe.write_text(
            recipe.read_text(encoding="utf-8").replace(
                "output: app/build/outputs/apk/release/app-release-unsigned.apk",
                "output: app/build/outputs/apk/release/app-release.apk",
            ),
            encoding="utf-8",
        )
        if expect_fail(fixture, "app-release-unsigned.apk", "a recipe naming the signed artifact"):
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        build_fixture(fixture)
        build = fixture / BUILD_REL
        build.write_text(
            build.read_text(encoding="utf-8").replace(
                '            signingConfig = signingConfigs.findByName("release")\n        }',
                '            signingConfig = signingConfigs.findByName("release") '
                '?: signingConfigs.getByName("debug")\n        }',
                1,
            ),
            encoding="utf-8",
        )
        if expect_fail(fixture, "falls back to another signing key", "a release build that debug-signs"):
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        build_fixture(fixture)
        recipe = fixture / RECIPE_REL
        recipe.write_text(
            recipe.read_text(encoding="utf-8").replace(
                "AllowedAPKSigningKeys: []",
                "AntiFeatures:\n  KnownVuln:\n    en-US: None known.\n\nAllowedAPKSigningKeys: []",
            ),
            encoding="utf-8",
        )
        if expect_fail(fixture, "describing it as absent", "a hollow anti-feature"):
            return 1

    print("fdroid recipe checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
