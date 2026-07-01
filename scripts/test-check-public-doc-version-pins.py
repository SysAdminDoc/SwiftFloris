#!/usr/bin/env python3
"""Self-test the public-doc and F-Droid metadata version pin checker with small fixtures."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from textwrap import dedent


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-public-doc-version-pins.py"


def write_fixture(root: Path) -> None:
    (root / "gradle" / "wrapper").mkdir(parents=True, exist_ok=True)
    (root / "docs").mkdir(exist_ok=True)
    (root / "fdroid").mkdir(exist_ok=True)
    (root / "gradle" / "libs.versions.toml").write_text(
        dedent(
            """
            [versions]
            android-gradle-plugin = "9.2.1"
            androidx-compose-bom = "2026.06.00"
            androidx-room = "2.8.4"
            kotlin = "2.4.0"
            ksp = "2.3.9"
            mikepenz-aboutlibraries = "15.0.3"
            kotest = "6.1.11"
            robolectric = "4.16.1"
            roborazzi = "1.64.0"
            sqlcipher-android = "4.16.0"
            tink-android = "1.22.0"
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "gradle.properties").write_text(
        dedent(
            """
            projectMinSdk=26
            projectTargetSdk=36
            projectCompileSdk=37
            projectVersionCode=2102
            projectVersionName=1.9.53
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "gradle" / "wrapper" / "gradle-wrapper.properties").write_text(
        dedent(
            """
            distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
            distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "README.md").write_text(
        dedent(
            """
            ![Version](https://img.shields.io/badge/version-v1.9.53-blue)
            | Area | What's in v1.9.53 | Privacy posture |
            |------|-------------------|-----------------|
            dependency freshness is pinned through Compose BOM 2026.06.00 / KSP 2.3.9 / AboutLibraries 15.0.3 / Roborazzi 1.64.0
            - Kotlin 2.4.0, Compose BOM 2026.06.00, Material 3.
            - AGP 9.2.1, Gradle 9.6.1, JDK 21.
            - KSP 2.3.9, Room 2.8.4, SQLCipher 4.16.0, Tink Android 1.22.0.
            - Kotest 6.1.11 unit-test runner; Roborazzi 1.64.0 and Robolectric 4.16.1 for regressions.
            - minSdk **26** (Android 8.0); targetSdk **36** (Android 16); compileSdk **37** (Android 17 APIs available).
            # Gradle 9.6.1 (use the bundled wrapper)
            - **Visual regression:** Roborazzi 1.64.0, plugin alias active.
            Current release: **v1.9.53**
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "docs" / "SECURITY.md").write_text(
        dedent(
            """
            `TinkStringPreferenceCrypto` uses Tink Android `1.22.0`, creates keys.
            As of SQLCipher 4.16.0, Zetetic documents the provider matrix.
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "docs" / "REPRODUCIBLE_BUILDS.md").write_text(
        dedent(
            """
            | Gradle distribution | `gradle/wrapper/gradle-wrapper.properties` | 9.6.1 | `distributionSha256Sum=9c0f7fae...` |
            | Android Gradle Plugin | `gradle/libs.versions.toml` `[versions] android-gradle-plugin` | 9.2.1 | resolves |
            | Kotlin | `gradle/libs.versions.toml` `[versions] kotlin` | 2.4.0 | fixed |
            | KSP | `gradle/libs.versions.toml` `[versions] ksp` | 2.3.9 | fixed |
            git clone --branch v1.9.53 --depth 1 https://github.com/SysAdminDoc/SwiftFloris.git
            APK_PUBLISHED=app-release-v1.9.53.apk
              - versionName: "1.9.53"
                versionCode: 2102
                commit: v1.9.53
            CurrentVersion: "1.9.53"
            CurrentVersionCode: 2102
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "fdroid" / "io.github.sysadmindoc.swiftfloris.yml").write_text(
        dedent(
            """
            Builds:
              - versionName: "1.9.53"
                versionCode: 2102
                commit: v1.9.53
            CurrentVersion: "1.9.53"
            CurrentVersionCode: 2102
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(CHECKER), "--root", str(root)],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture)
        passing = run_checker(fixture)
        if passing.returncode != 0:
            print(passing.stdout)
            print("expected matching docs to pass")
            return 1

        readme = fixture / "README.md"
        readme.write_text(
            readme.read_text(encoding="utf-8").replace("Roborazzi 1.64.0 and", "Roborazzi 1.63.0 and"),
            encoding="utf-8",
        )
        failing = run_checker(fixture)
        if failing.returncode != 1 or "Roborazzi" not in failing.stdout:
            print(failing.stdout)
            print("expected stale Roborazzi docs to fail")
            return 1

        write_fixture(fixture)
        readme = fixture / "README.md"
        readme.write_text(
            readme.read_text(encoding="utf-8").replace("AboutLibraries 15.0.3", "AboutLibraries 15.0.2"),
            encoding="utf-8",
        )
        failing = run_checker(fixture)
        if failing.returncode != 1 or "AboutLibraries" not in failing.stdout:
            print(failing.stdout)
            print("expected stale AboutLibraries docs to fail")
            return 1

        write_fixture(fixture)
        security = fixture / "docs" / "SECURITY.md"
        security.write_text(
            security.read_text(encoding="utf-8").replace("Tink Android `1.22.0`", "Tink Android `1.21.0`"),
            encoding="utf-8",
        )
        failing = run_checker(fixture)
        if failing.returncode != 1 or "Tink" not in failing.stdout:
            print(failing.stdout)
            print("expected stale Tink docs to fail")
            return 1

        write_fixture(fixture)
        repro = fixture / "docs" / "REPRODUCIBLE_BUILDS.md"
        repro.write_text(
            repro.read_text(encoding="utf-8").replace("CurrentVersionCode: 2102", "CurrentVersionCode: 2101"),
            encoding="utf-8",
        )
        failing = run_checker(fixture)
        if failing.returncode != 1 or "CurrentVersionCode" not in failing.stdout:
            print(failing.stdout)
            print("expected stale reproducible-build version code to fail")
            return 1

        write_fixture(fixture)
        fdroid = fixture / "fdroid" / "io.github.sysadmindoc.swiftfloris.yml"
        fdroid.write_text(
            fdroid.read_text(encoding="utf-8").replace("commit: v1.9.53", "commit: v1.9.52"),
            encoding="utf-8",
        )
        failing = run_checker(fixture)
        if failing.returncode != 1 or "F-Droid YAML build commit tag" not in failing.stdout:
            print(failing.stdout)
            print("expected stale checked-in F-Droid YAML to fail")
            return 1

    print("public doc version pin checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
