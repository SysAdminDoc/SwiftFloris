#!/usr/bin/env python3
"""Self-test for the pointerInput(Unit) allowlist gate.

Each fixture reintroduces exactly one of the drifts the gate exists to catch,
so a rule that stops working stops passing here too.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from shutil import copy2
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-pointerinput-unit-allowlist.py"
ALLOWLIST_REL = Path("scripts/pointerinput-unit-allowlist.txt")
SOURCE_REL = Path("app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/ImeWindow.kt")


def build_fixture(root: Path) -> None:
    """A minimal tree: the checker, the real allowlist, and the sources it covers."""
    (root / "scripts").mkdir(parents=True, exist_ok=True)
    copy2(CHECKER, root / "scripts" / CHECKER.name)
    copy2(ROOT / ALLOWLIST_REL, root / ALLOWLIST_REL)
    for rel in allowlisted_sources():
        target = root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        copy2(ROOT / rel, target)


def allowlisted_sources() -> list[Path]:
    paths: set[Path] = set()
    for raw in (ROOT / ALLOWLIST_REL).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        paths.add(Path(line.split("|", 1)[0].strip()))
    return sorted(paths)


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(root / "scripts" / CHECKER.name), "--root", str(root)],
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
        if expect_pass(fixture, "the checked-in allowlist"):
            return 1

    with TemporaryDirectory() as tmp:
        # The drift the gate exists for: a new gesture site lands with an
        # unchanging key and nobody has said what it captures.
        fixture = Path(tmp)
        build_fixture(fixture)
        source = fixture / SOURCE_REL
        source.write_text(
            source.read_text(encoding="utf-8")
            + "\n@Composable\nprivate fun UnreviewedGesture() {\n"
            + "    Modifier.pointerInput(Unit) { detectTapGestures { } }\n}\n",
            encoding="utf-8",
        )
        if expect_fail(fixture, "Unallowlisted production pointerInput(Unit) sites", "a new unreviewed site"):
            return 1

    with TemporaryDirectory() as tmp:
        # The opposite drift: a site is keyed or deleted but its entry lingers,
        # so the file stops describing the code.
        fixture = Path(tmp)
        build_fixture(fixture)
        allowlist = fixture / ALLOWLIST_REL
        allowlist.write_text(
            allowlist.read_text(encoding="utf-8")
            + "app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/ImeWindow.kt"
            + " | .pointerInput(Unit) { // gone | verdict=no-capture; rationale=removed site\n",
            encoding="utf-8",
        )
        if expect_fail(fixture, "Stale pointerInput(Unit) allowlist entries", "a stale entry"):
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        build_fixture(fixture)
        allowlist = fixture / ALLOWLIST_REL
        allowlist.write_text(
            allowlist.read_text(encoding="utf-8").replace(
                "verdict=no-capture; rationale=Consumes every pointer event",
                "rationale=Consumes every pointer event",
                1,
            ),
            encoding="utf-8",
        )
        if expect_fail(fixture, "missing verdict", "an entry with no verdict"):
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        build_fixture(fixture)
        allowlist = fixture / ALLOWLIST_REL
        allowlist.write_text(
            allowlist.read_text(encoding="utf-8").replace(
                "verdict=no-capture; rationale=Consumes every pointer event",
                "verdict=looks-fine; rationale=Consumes every pointer event",
                1,
            ),
            encoding="utf-8",
        )
        if expect_fail(fixture, "unknown verdict", "an entry with an invented verdict"):
            return 1

    with TemporaryDirectory() as tmp:
        # A rationale is the whole point of the file, so an empty one fails.
        fixture = Path(tmp)
        build_fixture(fixture)
        allowlist = fixture / ALLOWLIST_REL
        allowlist.write_text(
            allowlist.read_text(encoding="utf-8").replace(
                "verdict=no-capture; rationale=Consumes every pointer event so the editor overlay swallows input; reads nothing from the enclosing scope",
                "verdict=no-capture; rationale=",
                1,
            ),
            encoding="utf-8",
        )
        if expect_fail(fixture, "missing rationale", "an entry with an empty rationale"):
            return 1

    print("pointerInput(Unit) allowlist checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
