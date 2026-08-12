#!/usr/bin/env python3
"""Fixture tests for the release-front-door publication gate."""

from __future__ import annotations

import os
import shlex
import shutil
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from textwrap import dedent


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-release-front-door.sh"


def run(command: list[str], cwd: Path, *, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        env=env,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def require_ok(result: subprocess.CompletedProcess[str]) -> None:
    if result.returncode != 0:
        raise AssertionError(result.stdout)


def bash_path(path: Path) -> str:
    resolved = path.resolve()
    if os.name != "nt":
        return resolved.as_posix()
    bash = (shutil.which("bash") or "").lower()
    drive = resolved.drive.rstrip(":").lower()
    tail = resolved.as_posix()[2:]
    if "system32\\bash" in bash or "windowsapps\\bash" in bash:
        return f"/mnt/{drive}{tail}"
    return f"/{drive}{tail}"


def write_fixture(root: Path, *, version: str = "1.9.54", code: str = "2103", readme_version: str | None = None) -> None:
    (root / "scripts").mkdir(parents=True, exist_ok=True)
    (root / "fastlane" / "metadata" / "android" / "en-US" / "changelogs").mkdir(parents=True, exist_ok=True)
    (root / "fdroid").mkdir(exist_ok=True)
    shutil.copyfile(CHECKER, root / "scripts" / "check-release-front-door.sh")
    (root / "scripts" / "check-locale-coverage.py").write_text(
        "# Fixture stub: release-front-door owns invocation, locale tests own behavior.\n"
        "raise SystemExit(0)\n",
        encoding="utf-8",
    )
    readme_claim = readme_version or version
    (root / "gradle.properties").write_text(
        f"projectVersionName={version}\nprojectVersionCode={code}\n",
        encoding="utf-8",
    )
    (root / "README.md").write_text(
        dedent(
            f"""
            ![Version](https://img.shields.io/badge/version-v{readme_claim}-blue)
            - **v{readme_claim}** (2026-07-02) - Fixture release.
            Current release: **v{readme_claim}**
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "fastlane" / "metadata" / "android" / "en-US" / "changelogs" / f"{code}.txt").write_text(
        "Fixture release notes.\n",
        encoding="utf-8",
    )
    (root / "fdroid" / "io.github.sysadmindoc.swiftfloris.yml").write_text(
        dedent(
            f"""
            Builds:
              - versionName: "{readme_claim}"
                versionCode: {code}
                commit: v{readme_claim}
            CurrentVersion: "{readme_claim}"
            CurrentVersionCode: {code}
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )


def init_repo(root: Path, *, tag: str | None = None, push_tag: bool = False) -> None:
    require_ok(run(["git", "init", "-q"], root))
    require_ok(run(["git", "config", "user.name", "SysAdminDoc"], root))
    require_ok(run(["git", "config", "user.email", "matt_parker@outlook.com"], root))
    require_ok(run(["git", "add", "."], root))
    require_ok(run(["git", "commit", "-qm", "fixture"], root))
    if tag:
        require_ok(run(["git", "tag", tag], root))
    remote = root.parent / "remote.git"
    require_ok(run(["git", "init", "--bare", "-q", str(remote)], root))
    require_ok(run(["git", "remote", "add", "origin", str(remote)], root))
    if push_tag and tag:
        require_ok(run(["git", "push", "-q", "origin", tag], root))
    require_ok(run(["git", "remote", "set-url", "origin", f"file://{bash_path(remote)}"], root))


def gh_stub_setup(root: Path, *, released_tag: str | None) -> str:
    stub_dir = f"/tmp/swiftfloris-rfd-{os.getpid()}-{root.name}"
    stub_path = f"{stub_dir}/gh"
    if released_tag:
        quoted_tag = shlex.quote(released_tag)
        body = (
            "#!/usr/bin/env bash\n"
            f"printf '%s\\n' {quoted_tag}\n"
            "exit 0\n"
        )
    else:
        body = "#!/usr/bin/env bash\nexit 1\n"
    return (
        f"rm -rf {shlex.quote(stub_dir)}; "
        f"mkdir -p {shlex.quote(stub_dir)}; "
        f"cat > {shlex.quote(stub_path)} <<'SWIFTFLORIS_GH'\n"
        f"{body}"
        "SWIFTFLORIS_GH\n"
        f"chmod +x {shlex.quote(stub_path)}; "
        f"export GH_BIN={shlex.quote(stub_path)}; "
        f"export PATH={shlex.quote(stub_dir)}:\"$PATH\";"
    )


def run_checker(root: Path, *args: str, released_tag: str | None = None) -> subprocess.CompletedProcess[str]:
    bash = shutil.which("bash")
    if not bash:
        raise AssertionError("bash is required for release-front-door fixture tests")
    env = os.environ.copy()
    command = " ".join(["scripts/check-release-front-door.sh", *(shlex.quote(arg) for arg in args)])
    return run([bash, "-lc", f"{gh_stub_setup(root, released_tag=released_tag)} {command}"], root, env=env)


def main() -> int:
    with TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        fixture = Path(tmp) / "published"
        fixture.mkdir()
        write_fixture(fixture)
        init_repo(fixture, tag="v1.9.54", push_tag=True)
        passing = run_checker(fixture, released_tag="v1.9.54")
        if passing.returncode != 0:
            print(passing.stdout)
            print("expected published release-front-door fixture to pass")
            return 1

    with TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        fixture = Path(tmp) / "missing"
        fixture.mkdir()
        write_fixture(fixture)
        init_repo(fixture)
        failing = run_checker(fixture, released_tag=None)
        if failing.returncode != 1 or "missing local tag v1.9.54" not in failing.stdout:
            print(failing.stdout)
            print("expected missing publication proof to fail")
            return 1

    with TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        fixture = Path(tmp) / "prep"
        fixture.mkdir()
        write_fixture(fixture, version="1.9.55", code="2104", readme_version="1.9.54")
        init_repo(fixture, tag="v1.9.54", push_tag=True)
        passing = run_checker(fixture, "--allow-unpublished", released_tag=None)
        if passing.returncode != 0:
            print(passing.stdout)
            print("expected unpublished local prep to pass when public surfaces do not claim the new version")
            return 1

    with TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        fixture = Path(tmp) / "claimed"
        fixture.mkdir()
        write_fixture(fixture)
        init_repo(fixture)
        failing = run_checker(fixture, "--allow-unpublished", released_tag=None)
        if failing.returncode != 1 or "--allow-unpublished cannot pass" not in failing.stdout:
            print(failing.stdout)
            print("expected allow-unpublished to fail once public surfaces claim the release")
            return 1

    with TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        fixture = Path(tmp) / "fdroid-ref"
        fixture.mkdir()
        write_fixture(fixture)
        init_repo(fixture, tag="v1.9.54", push_tag=True)
        recipe = fixture / "fdroid" / "io.github.sysadmindoc.swiftfloris.yml"
        recipe.write_text(
            recipe.read_text(encoding="utf-8").replace(
                "commit: v1.9.54",
                "commit: v9.9.99",
            ),
            encoding="utf-8",
        )
        failing = run_checker(fixture, released_tag="v1.9.54")
        if failing.returncode != 1 or "commit ref v9.9.99 does not resolve to a local tag" not in failing.stdout:
            print(failing.stdout)
            print("expected an unresolvable F-Droid commit ref to fail")
            return 1

    print("release-front-door checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
