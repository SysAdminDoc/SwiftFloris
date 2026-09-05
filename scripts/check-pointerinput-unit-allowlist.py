#!/usr/bin/env python3
"""Fails when production `pointerInput(Unit)` usages drift beyond the reviewed allowlist.

`Modifier.pointerInput(key)` remembers its lambda and only restarts the block
when `key` changes, so `pointerInput(Unit)` captures whatever the enclosing
composable held at the *first* composition and never refreshes it. Reading a
`State` object inside the block is fine, because the `.value` read still happens
live. Capturing a snapshot *value* -- a `by` delegate read into a local `Int` or
`Long`, a parameter, or a lambda closing over one -- is not: the gesture goes on
using a value the user has since changed.

Two real defects of that shape were fixed on 2026-09-05. An emoji key committed
the skin tone that was current when it first composed, and the suggestion
strip's long-press threshold ignored the preference for the life of the
composition. Neither was visible in review, because the call site looks
identical to the seventeen that are safe.

So the rule is not "never write `pointerInput(Unit)`" -- most uses are correct.
It is "every one is reviewed once and recorded", which is the same contract
`check-runblocking-allowlist.py` holds over `runBlocking`.

Run: python scripts/check-pointerinput-unit-allowlist.py [--root <repo-root>]
"""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import re
import sys


DEFAULT_ROOT = Path(__file__).resolve().parents[1]
ALLOWLIST_REL = Path("scripts/pointerinput-unit-allowlist.txt")

# Why each reviewed site is allowed to keep an unchanging key.
ALLOWED_VERDICTS = frozenset(
    {
        # The block reads nothing at all from the enclosing composable.
        "no-capture",
        # Everything it reads is fixed for the composable's lifetime: a
        # singleton, a CompositionLocal provided once, a keyless `remember`d
        # State object, or a parameter that is always the same constant.
        "stable-capture",
        # A capture does change, but the node is created and disposed together
        # with the thing it captures (inside an `if`, a Popup, a conditional
        # modifier), so it can never be observed stale.
        "scoped-node",
    }
)

POINTER_INPUT_UNIT = re.compile(r"\bpointerInput\s*\(\s*Unit\s*\)")


def normalize(line: str) -> str:
    return re.sub(r"\s+", " ", line.strip())


def iter_source_files(root: Path) -> list[Path]:
    files: list[Path] = []
    scan_roots = (
        root / "app" / "src" / "main",
        root / "lib",
    )
    for scan_root in scan_roots:
        if not scan_root.exists():
            continue
        for path in scan_root.rglob("*.kt"):
            rel = path.relative_to(root).as_posix()
            if "/build/" in rel:
                continue
            if rel.startswith("lib/") and "/src/main/" not in rel:
                continue
            files.append(path)
    return sorted(files)


def production_occurrences(root: Path) -> Counter[tuple[str, str]]:
    occurrences: Counter[tuple[str, str]] = Counter()
    for path in iter_source_files(root):
        rel = path.relative_to(root).as_posix()
        for line in path.read_text(encoding="utf-8").splitlines():
            if POINTER_INPUT_UNIT.search(line):
                occurrences[(rel, normalize(line))] += 1
    return occurrences


def parse_metadata(allowlist_rel: Path, line_no: int, metadata: str) -> list[str]:
    errors: list[str] = []
    fields: dict[str, str] = {}
    # rationale is free prose and routinely contains semicolons, so it is taken
    # whole from the first `rationale=` onwards; only the fields before it are
    # split on `;`.
    head, sep, rationale = metadata.partition("rationale=")
    if sep:
        fields["rationale"] = rationale.strip()
    for chunk in head.split(";"):
        chunk = chunk.strip()
        if not chunk:
            continue
        key, key_sep, value = chunk.partition("=")
        if not key_sep:
            errors.append(
                f"{allowlist_rel.as_posix()}:{line_no}: expected key=value, got {chunk!r}"
            )
            continue
        fields[key.strip()] = value.strip()

    verdict = fields.get("verdict")
    if verdict is None:
        errors.append(f"{allowlist_rel.as_posix()}:{line_no}: missing verdict=<verdict>")
    elif verdict not in ALLOWED_VERDICTS:
        errors.append(
            f"{allowlist_rel.as_posix()}:{line_no}: unknown verdict {verdict!r}; "
            f"expected one of {', '.join(sorted(ALLOWED_VERDICTS))}"
        )
    if not fields.get("rationale"):
        errors.append(f"{allowlist_rel.as_posix()}:{line_no}: missing rationale=<why>")
    return errors


def allowlisted_occurrences(root: Path) -> Counter[tuple[str, str]]:
    allowlist = root / ALLOWLIST_REL
    if not allowlist.exists():
        raise SystemExit(f"missing allowlist: {ALLOWLIST_REL.as_posix()}")
    allowed: Counter[tuple[str, str]] = Counter()
    errors: list[str] = []
    for line_no, raw in enumerate(allowlist.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [part.strip() for part in line.split("|", 2)]
        if len(parts) != 3 or not all(parts):
            errors.append(
                f"{ALLOWLIST_REL.as_posix()}:{line_no}: expected path | code | "
                "verdict=<verdict>; rationale=<why>"
            )
            continue
        path, code, metadata = parts
        errors.extend(parse_metadata(ALLOWLIST_REL, line_no, metadata))
        allowed[(path, normalize(code))] += 1
    if errors:
        raise SystemExit("\n".join(errors))
    return allowed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    args = parser.parse_args(argv)
    root = args.root.resolve()

    actual = production_occurrences(root)
    allowed = allowlisted_occurrences(root)
    unexpected = actual - allowed
    stale = allowed - actual
    if not unexpected and not stale:
        print(
            "pointerInput(Unit) allowlist OK "
            f"({sum(actual.values())} reviewed production sites)"
        )
        return 0
    if unexpected:
        print("Unallowlisted production pointerInput(Unit) sites:", file=sys.stderr)
        for (path, code), count in sorted(unexpected.items()):
            suffix = f" x{count}" if count > 1 else ""
            print(f"  {path} | {code}{suffix}", file=sys.stderr)
        print(
            "Check what the block captures from the enclosing composable. If any "
            "captured value changes at runtime, key the pointerInput on it or wrap "
            "it in rememberUpdatedState; otherwise add a reviewed entry with "
            "verdict=<verdict>; rationale=<why>.",
            file=sys.stderr,
        )
    if stale:
        print("Stale pointerInput(Unit) allowlist entries:", file=sys.stderr)
        for (path, code), count in sorted(stale.items()):
            suffix = f" x{count}" if count > 1 else ""
            print(f"  {path} | {code}{suffix}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
