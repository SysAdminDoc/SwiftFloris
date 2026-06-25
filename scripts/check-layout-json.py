#!/usr/bin/env python3
"""Validate bundled keyboard layout JSON assets.

FlorisBoard layout JSON uses JSONC extensions (hex integer literals like
0x1300, trailing commas, // comments). This script pre-processes those
before standard JSON parsing.

Catches:
  - Unparseable JSON (even after JSONC normalization)
  - Empty layouts (no rows)
  - Empty rows (no keys)
  - Top-level keys that are not objects
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUT_DIRS = [
    ROOT / "app" / "src" / "main" / "assets" / "ime" / "keyboard"
    / "org.florisboard.layouts" / "layouts",
]


def normalize_jsonc(text: str) -> str:
    text = re.sub(r"//.*$", "", text, flags=re.MULTILINE)
    text = re.sub(r"0x([0-9a-fA-F]+)", lambda m: str(int(m.group(1), 16)), text)
    text = re.sub(r",\s*([}\]])", r"\1", text)
    return text


def validate_layout(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        raw = path.read_text(encoding="utf-8")
        normalized = normalize_jsonc(raw)
        data = json.loads(normalized)
    except (json.JSONDecodeError, UnicodeDecodeError) as e:
        return [f"unparseable: {e}"]

    if not isinstance(data, list):
        return [f"top-level value is {type(data).__name__}, expected array of rows"]

    if len(data) == 0:
        return ["layout has no rows"]

    for row_idx, row in enumerate(data):
        if not isinstance(row, list):
            errors.append(f"row {row_idx}: not an array")
            continue
        if len(row) == 0:
            errors.append(f"row {row_idx}: empty row")
            continue
        for key_idx, key in enumerate(row):
            if not isinstance(key, dict):
                errors.append(f"row {row_idx} key {key_idx}: not an object")

    return errors


def main() -> int:
    all_errors: dict[str, list[str]] = {}

    for layout_dir in LAYOUT_DIRS:
        if not layout_dir.exists():
            print(f"::warning::layout dir not found: {layout_dir}")
            continue
        for json_file in sorted(layout_dir.rglob("*.json")):
            errors = validate_layout(json_file)
            if errors:
                rel = json_file.relative_to(ROOT)
                all_errors[str(rel)] = errors

    if all_errors:
        for file_path, errors in all_errors.items():
            for error in errors:
                print(f"::error file={file_path}::{error}")
        total = sum(len(e) for e in all_errors.values())
        print(
            f"\nlayout-json: FAIL ({total} error(s) across "
            f"{len(all_errors)} file(s))"
        )
        return 1

    layout_count = sum(
        len(list(d.rglob("*.json"))) for d in LAYOUT_DIRS if d.exists()
    )
    print(f"layout-json: OK ({layout_count} layouts validated)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
