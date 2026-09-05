#!/usr/bin/env python3
"""Guard the invariants the F-Droid recipe depends on.

F-Droid builds from source and signs the result with its own key, so the
artifact our build hands it has to be unsigned and has to appear at the exact
path the recipe names. Both of those live in a different file from the recipe,
so nothing stops them drifting apart, and the failure is invisible until an
F-Droid build runs somewhere none of us can see.

The `binary:` URL has the same problem from the other end: it names a release
asset that is uploaded by hand, so a rename on either side silently breaks the
reproducible-build comparison. On 2026-09-04 it named `app-release.apk` while
every published release carried `SwiftFloris-v<version>-release.apk`, so the
URL had been returning 404 for as long as it had existed.

Run: python scripts/check-fdroid-recipe.py [repo-root]
     python scripts/check-fdroid-recipe.py [repo-root] --check-published
"""

from __future__ import annotations

import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

RECIPE = Path("fdroid/io.github.sysadmindoc.swiftfloris.yml")
BUILD_FILE = Path("app/build.gradle.kts")

# AGP names the artifact app-release-unsigned.apk only when the release build
# type carries no signing config. A recipe pointing anywhere else is either
# naming a file that will not exist or naming a signed one.
UNSIGNED_RELEASE_OUTPUT = "app/build/outputs/apk/release/app-release-unsigned.apk"

# The file name every published GitHub release asset uses. `%v` is F-Droid's
# versionName placeholder, so the recipe URL expands to the real asset name.
# This is the name the release step actually uploads; it is not the Gradle
# output name, which is `app-release.apk` and never leaves the build directory.
RELEASE_ASSET_NAME = "SwiftFloris-v%v-release.apk"
RELEASE_DOWNLOAD_URL = (
    "https://github.com/SysAdminDoc/SwiftFloris/releases/download/v%v/" + RELEASE_ASSET_NAME
)
REPRODUCIBLE_BUILDS_DOC = Path("docs/REPRODUCIBLE_BUILDS.md")
GRADLE_PROPERTIES = Path("gradle.properties")


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

    # Allow exactly one shape, rather than blacklisting the ways of writing a
    # fallback. A denylist was evadable by hoisting the debug config into a
    # variable above buildTypes and referring to it by name, which reads
    # innocuously and still debug-signs the release.
    permitted = 'signingConfig = signingConfigs.findByName("release")'
    for line in signing_lines:
        if line != permitted:
            errors.append(
                f"{BUILD_FILE} release build type assigns a signing config this gate does not "
                f"recognise: {line!r}. The only accepted form is `{permitted}`. Without a release "
                "keystore the release build must stay unsigned, or F-Droid receives an APK signed "
                "by a key it did not make, under a filename its recipe does not name."
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


def check_binary_url(recipe_text: str, errors: list[str]) -> None:
    """The `binary:` URL must name the asset the release actually publishes.

    F-Droid downloads this file and compares it byte for byte against what it
    built. A URL that 404s does not fail loudly; the comparison simply never
    happens, so the recipe looks fine while proving nothing.
    """
    match = re.search(r"^\s*binary:\s*(\S+)\s*$", recipe_text, re.M)
    if match is None:
        errors.append(f"{RECIPE} declares no binary URL to compare the build against.")
        return
    declared = match.group(1)
    if declared != RELEASE_DOWNLOAD_URL:
        errors.append(
            f"{RECIPE} names binary '{declared}'. Published releases carry "
            f"'{RELEASE_ASSET_NAME}', so the recipe must name "
            f"'{RELEASE_DOWNLOAD_URL}' or F-Droid's binary comparison downloads nothing."
        )


def check_signing_key_pinned(recipe_text: str, errors: list[str]) -> None:
    """An empty AllowedAPKSigningKeys accepts an APK signed by anyone."""
    match = re.search(r"^AllowedAPKSigningKeys:\s*(.*)$", recipe_text, re.M)
    if match is None:
        errors.append(f"{RECIPE} declares no AllowedAPKSigningKeys.")
        return
    inline = match.group(1).strip()
    if inline in {"[]", "''", '""'}:
        errors.append(
            f"{RECIPE} leaves AllowedAPKSigningKeys empty. Pin the SHA-256 of the release "
            "signing certificate, read from a published APK with `apksigner verify "
            "--print-certs`, so a binary signed by another key cannot pass the comparison."
        )
        return
    keys = re.findall(r"^\s*-\s*([0-9a-fA-F]{64})\s*$", recipe_text, re.M)
    if not keys:
        errors.append(
            f"{RECIPE} lists AllowedAPKSigningKeys entries that are not 64 hex characters. "
            "F-Droid expects the certificate SHA-256."
        )


def check_doc_url_agrees(root: Path, recipe_text: str, errors: list[str]) -> None:
    """The reproducible-build doc repeats the recipe verbatim; keep them equal."""
    doc_path = root / REPRODUCIBLE_BUILDS_DOC
    if not doc_path.is_file():
        return
    doc_text = doc_path.read_text(encoding="utf-8")
    if "binary: https://github.com/SysAdminDoc/SwiftFloris/releases/download/" not in doc_text:
        return
    if RELEASE_DOWNLOAD_URL not in doc_text:
        errors.append(
            f"{REPRODUCIBLE_BUILDS_DOC} quotes a binary URL that is not "
            f"'{RELEASE_DOWNLOAD_URL}'. The doc and the recipe must show the same URL."
        )


def resolve_version(root: Path) -> str | None:
    props = root / GRADLE_PROPERTIES
    if not props.is_file():
        return None
    match = re.search(r"^projectVersionName=(\S+)\s*$", props.read_text(encoding="utf-8"), re.M)
    return match.group(1) if match else None


def check_published_binary(root: Path, recipe_text: str, errors: list[str]) -> None:
    """Opt-in: resolve the expanded URL and require the asset to exist.

    Off by default so the gate stays deterministic offline. `release-evidence`
    turns it on, because that is the moment the asset is supposed to exist.
    """
    version = resolve_version(root)
    if version is None:
        errors.append(f"{GRADLE_PROPERTIES} declares no projectVersionName to expand '%v' with.")
        return
    match = re.search(r"^\s*binary:\s*(\S+)\s*$", recipe_text, re.M)
    if match is None:
        return
    url = match.group(1).replace("%v", version)
    request = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
    except OSError as error:
        errors.append(f"Could not reach {url}: {error}")
        return
    if status != 200:
        errors.append(
            f"{RECIPE} binary URL {url} returned HTTP {status}. F-Droid cannot compare its "
            "build against an asset that is not published under that exact name."
        )


def main(argv: list[str]) -> int:
    args = [arg for arg in argv[1:] if not arg.startswith("--")]
    check_published = "--check-published" in argv[1:]
    root = Path(args[0]).resolve() if args else Path(__file__).resolve().parents[1]
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
    check_binary_url(recipe_text, errors)
    check_signing_key_pinned(recipe_text, errors)
    check_doc_url_agrees(root, recipe_text, errors)
    if check_published:
        check_published_binary(root, recipe_text, errors)

    for error in errors:
        fail(error)
    if errors:
        return 1
    print(
        "fdroid recipe: OK (unsigned release output, published binary URL, pinned signing key, "
        "no hollow anti-features)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
