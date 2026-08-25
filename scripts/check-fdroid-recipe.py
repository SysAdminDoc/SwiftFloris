#!/usr/bin/env python3
"""Guard the invariants the F-Droid recipe depends on.

F-Droid builds from source and signs the result with its own key, so the
artifact our build hands it has to be unsigned and has to appear at the exact
path the recipe names. Both of those live in a different file from the recipe,
so nothing stops them drifting apart, and the failure is invisible until an
F-Droid build runs somewhere none of us can see.

Run: python scripts/check-fdroid-recipe.py [repo-root]
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RECIPE = Path("fdroid/io.github.sysadmindoc.swiftfloris.yml")
BUILD_FILE = Path("app/build.gradle.kts")

# AGP names the artifact app-release-unsigned.apk only when the release build
# type carries no signing config. A recipe pointing anywhere else is either
# naming a file that will not exist or naming a signed one.
UNSIGNED_RELEASE_OUTPUT = "app/build/outputs/apk/release/app-release-unsigned.apk"


def fail(message: str) -> None:
    print(f"::error::{message}")


def check_output_path(recipe_text: str, errors: list[str]) -> None:
    match = re.search(r"^\s*output:\s*(\S+)\s*$", recipe_text, re.M)
    if match is None:
        errors.append(f"{RECIPE} declares no build output path.")
        return
    declared = match.group(1)
    if declared != UNSIGNED_RELEASE_OUTPUT:
        errors.append(
            f"{RECIPE} names output '{declared}'. F-Droid signs the APK itself, so the "
            f"recipe must name the unsigned artifact '{UNSIGNED_RELEASE_OUTPUT}'."
        )


def check_release_is_unsigned(build_text: str, errors: list[str]) -> None:
    """The release build type must not fall back to any other signing config.

    Only the `release` block matters. `beta` and `benchmark` are ours to sign
    and deliberately do.
    """
    block = re.search(
        r'named\("release"\)\s*\{(.*?)\n        \}',
        build_text,
        re.S,
    )
    if block is None:
        errors.append(f"{BUILD_FILE} has no named(\"release\") build type block to inspect.")
        return
    body = block.group(1)
    signing_lines = [
        line.strip()
        for line in body.splitlines()
        if "signingConfig" in line and not line.strip().startswith("//")
    ]
    if not signing_lines:
        return
    for line in signing_lines:
        if "getByName(" in line or '"debug"' in line:
            errors.append(
                f"{BUILD_FILE} release build type falls back to another signing key: {line!r}. "
                "Without a release keystore the release build must stay unsigned, or F-Droid "
                "receives an APK signed by a key it did not make, under a filename its recipe "
                "does not name."
            )


def check_no_hollow_antifeature(recipe_text: str, errors: list[str]) -> None:
    """An AntiFeature whose description says there is nothing wrong still applies.

    F-Droid reads the presence of the key, not the prose under it, so
    `KnownVuln: en-US: None known.` labels the app as having a known
    vulnerability.
    """
    block = re.search(r"^AntiFeatures:\s*\n((?:[ \t]+.*\n|\n)*)", recipe_text, re.M)
    if block is None:
        return
    body = block.group(1)
    for feature in re.finditer(r"^\s{2}(\w+):\s*\n((?:\s{4}.*\n)*)", body, re.M):
        name, described = feature.group(1), feature.group(2)
        if re.search(r"\b(none|no known|not applicable|n/?a)\b", described, re.I):
            errors.append(
                f"{RECIPE} declares AntiFeature '{name}' while describing it as absent. "
                "F-Droid applies the label regardless of the description; remove the entry."
            )


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path(__file__).resolve().parents[1]
    recipe_path = root / RECIPE
    build_path = root / BUILD_FILE
    errors: list[str] = []

    if not recipe_path.is_file():
        fail(f"Missing {RECIPE}")
        return 1
    if not build_path.is_file():
        fail(f"Missing {BUILD_FILE}")
        return 1

    recipe_text = recipe_path.read_text(encoding="utf-8")
    build_text = build_path.read_text(encoding="utf-8")

    check_output_path(recipe_text, errors)
    check_release_is_unsigned(build_text, errors)
    check_no_hollow_antifeature(recipe_text, errors)

    for error in errors:
        fail(error)
    if errors:
        return 1
    print("fdroid recipe: OK (unsigned release output, no hollow anti-features)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
