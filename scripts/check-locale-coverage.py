#!/usr/bin/env python3
"""Report and gate Android resource and typing-language coverage.

The translated Android resource directories are intentionally not treated as
complete UI locales merely because they contain many strings.  The English
source resources are the only reviewed UI-complete surface today.  A locale
can be promoted only after human review updates REVIEWED_UI_LOCALES and its
ratchet floor.

The report contains resource identities, counts, and source locations only;
it never reads or emits user data.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
RESOURCES_ROOT = Path("app/src/main/res")
TYPING_EXTENSION = Path(
    "app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json"
)

TRANSLATABLE_TYPES = frozenset({"string", "plurals", "string-array", "array"})
LOCALE_DIRECTORY_RE = re.compile(r"^values-([a-z]{2,3}(?:-.+)?)$")
PLACEHOLDER_RE = re.compile(r"\{[^{}]+\}|%(?:\d+\$)?[a-zA-Z]")
STRING_LITERAL_RE = re.compile(
    r'"""(.*?)"""|"((?:\\.|[^"\\])*)"|\'((?:\\.|[^\'\\])*)\'',
    re.DOTALL,
)
UI_LITERAL_MARKER_RE = re.compile(
    r"\b(?:Text|BasicText|Toast\.makeText)\s*\(|"
    r"\bcontentDescription\s*=",
)
CRITICAL_COPY_RE = re.compile(
    r"\b(?:backup|restore|privacy|permission|password|sensitive|recovery|"
    r"delete|remove|reset|clear|confirm|warning|error|passphrase|trust)\b",
    re.IGNORECASE,
)

# This is a deliberately curated set rather than a name-wide English-word
# search.  It covers permission, privacy, destructive, backup, recovery,
# sensitive-data, and trust-confirmation flows without turning ordinary
# keyboard labels into release blockers.
CRITICAL_NAME_PREFIXES = (
    "settings__privacy",
    "settings__home__privacy",
    "settings__backup",
    "settings__restore",
    "settings__recovery",
    "settings__permission",
    "settings__permissions",
    "backup_and_restore__",
    "scheduled_backup__",
    "setup__privacy",
    "setup__grant_",
    "permission__",
    "calendar__permission__",
    "action__delete",
    "action__reset",
    "action__remove",
    "action__restore",
    "action__cancel",
    "action__confirm",
    "clipboard__confirm",
    "clipboard__item_description_sensitive",
    "clipboard__text_item_sensitive",
    "pref__clipboard__auto_clean_sensitive",
    "settings__typing_stats__erase_everything",
    "settings__typing_stats__trace_",
    "settings__udm__encrypted_dictionary",
    "settings__learned_entries__",
    "settings__mcp__trust",
    "settings__mcp__reset_trust",
    "settings__addons__trust",
    "settings__snippet__clear_all",
    "settings__snippet__delete_file_a11y",
    "quick_action__insert_task__sensitive_field",
    "quick_action__insert_calendar_event__permission_required",
    "about__privacy",
    "about__view_privacy_policy",
    "about__ai_features__privacy",
    "crash_dialog__redaction",
    "error__",
)

REQUIRED_CRITICAL_NAMES = (
    "settings__privacy_posture__title",
    "settings__privacy_audit__title",
    "backup_and_restore__back_up__failure_title",
    "backup_and_restore__restore__failure_title",
    "setup__grant_notification_permission__title",
    "calendar__permission__title",
    "action__delete_confirm_title",
)

# A locale is not advertised as complete until a human has reviewed the full
# UI.  Keep this list intentionally small and explicit.  The source English
# resources are the complete fallback contract; every other locale is partial
# until a maintainer makes a deliberate review decision here.
REVIEWED_UI_LOCALES = frozenset({"en"})

PSEUDO_LOCALES = frozenset({"en-XA", "ar-XB"})

CRITICAL_SCREEN_PREFIXES = {
    "privacy": (
        "settings__privacy",
        "settings__home__privacy",
        "about__privacy",
        "about__view_privacy_policy",
        "setup__privacy",
    ),
    "backup_restore": (
        "settings__backup",
        "settings__restore",
        "backup_and_restore__",
        "scheduled_backup__",
    ),
    "permissions": (
        "settings__permission",
        "settings__permissions",
        "setup__grant_",
        "permission__",
        "calendar__permission__",
    ),
    "destructive": (
        "action__delete",
        "action__reset",
        "action__remove",
        "action__restore",
        "action__cancel",
        "action__confirm",
        "clipboard__confirm",
        "settings__typing_stats__erase_everything",
        "settings__snippet__clear_all",
    ),
}

# Each tuple is (minimum translatable resource count, minimum critical count).
# These are the checked-in floors from the current resource tree.  Adding a
# translation is always safe; removing one is a release-gate failure.
RESOURCE_COVERAGE_FLOORS = {
    "values-ar": (836, 50),
    "values-ast-rES": (286, 20),
    "values-bg": (1006, 53),
    "values-bs": (68, 0),
    "values-ca": (876, 50),
    "values-ckb": (709, 39),
    "values-cs": (1005, 53),
    "values-da": (445, 3),
    "values-de": (875, 50),
    "values-el": (390, 17),
    "values-eo": (715, 47),
    "values-es": (984, 52),
    "values-et-rEE": (73, 0),
    "values-fa": (325, 3),
    "values-fi": (447, 13),
    "values-fr": (878, 50),
    "values-hr": (207, 0),
    "values-hu": (999, 53),
    "values-in": (961, 50),
    "values-it": (796, 40),
    "values-iw": (311, 7),
    "values-ja": (943, 50),
    "values-ko-rKR": (528, 32),
    "values-ku": (378, 24),
    "values-lv-rLV": (940, 52),
    "values-mk": (17, 0),
    "values-nds-rDE": (2, 0),
    "values-nl": (904, 50),
    "values-no": (487, 11),
    "values-pl": (1006, 53),
    "values-pt": (983, 52),
    "values-pt-rBR": (810, 49),
    "values-ru": (992, 53),
    "values-sk": (363, 37),
    "values-sl": (36, 0),
    "values-sq-rAL": (69, 0),
    "values-sr": (47, 0),
    "values-sv": (80, 2),
    "values-tr": (983, 53),
    "values-uk": (835, 50),
    "values-ur-rPK": (0, 0),
    "values-zgh": (17, 0),
    "values-zh-rCN": (1005, 53),
}


@dataclass(frozen=True)
class Resource:
    identity: str
    resource_type: str
    name: str
    values: tuple[str, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Report Android resource coverage, typing-language coverage, "
            "and pseudolocale contracts."
        )
    )
    parser.add_argument(
        "--root",
        default=str(ROOT),
        help="Repository root. Defaults to the parent of this script directory.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Apply release-gate checks and return non-zero on violations.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the deterministic machine-readable report.",
    )
    parser.add_argument(
        "--no-ratchet",
        action="store_true",
        help="Skip repository locale floors; intended for isolated checker fixtures.",
    )
    return parser.parse_args()


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def element_text(element: ET.Element) -> str:
    return "".join(element.itertext())


def resource_values(element: ET.Element, resource_type: str) -> tuple[str, ...]:
    if resource_type == "string":
        return (element_text(element),)
    return tuple(element_text(child) for child in element)


def parse_resource_directory(path: Path) -> tuple[dict[str, Resource], list[str]]:
    resources: dict[str, Resource] = {}
    duplicates: list[str] = []
    if not path.is_dir():
        raise ValueError(f"missing resource directory: {path.as_posix()}")

    for xml_path in sorted(path.glob("*.xml")):
        try:
            document = ET.parse(xml_path)
        except ET.ParseError as exc:
            raise ValueError(f"{xml_path.as_posix()}: invalid XML: {exc}") from exc
        for element in document.getroot():
            resource_type = local_name(element.tag)
            if resource_type not in TRANSLATABLE_TYPES:
                continue
            if element.get("translatable", "true").lower() == "false":
                continue
            name = element.get("name")
            if not name:
                raise ValueError(f"{xml_path.as_posix()}: translatable resource has no name")
            identity = f"{resource_type}:{name}"
            if identity in resources:
                duplicates.append(f"{xml_path.as_posix()}: duplicate {identity}")
                continue
            resources[identity] = Resource(
                identity=identity,
                resource_type=resource_type,
                name=name,
                values=resource_values(element, resource_type),
            )
    return resources, duplicates


def locale_directory_name(path: Path) -> str | None:
    match = LOCALE_DIRECTORY_RE.fullmatch(path.name)
    if match is None:
        return None
    return match.group(1)


def bcp47_locale(resource_locale: str) -> str:
    if resource_locale.startswith("b+"):
        return resource_locale[2:].replace("+", "-")
    return re.sub(r"-r(?=[A-Z])", "-", resource_locale)


def locale_language(locale_tag: str) -> str:
    return bcp47_locale(locale_tag).split("-", 1)[0].lower()


def normalized_language(language: str) -> str:
    # Android retained these historical resource directory codes.  Treat them
    # as their modern BCP-47 equivalents when comparing to subtype presets.
    return {"in": "id", "iw": "he"}.get(language.lower(), language.lower())


def is_critical(resource: Resource) -> bool:
    return any(resource.name.startswith(prefix) for prefix in CRITICAL_NAME_PREFIXES)


def required_critical_names(base: dict[str, Resource]) -> list[str]:
    return [name for name in REQUIRED_CRITICAL_NAMES if f"string:{name}" not in base]


def parse_typing_languages(root: Path) -> tuple[list[str], list[str]]:
    path = root / TYPING_EXTENSION
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except FileNotFoundError as exc:
        raise ValueError(f"missing typing subtype manifest: {path.as_posix()}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path.as_posix()}: invalid JSON: {exc}") from exc

    presets = data.get("subtypePresets")
    if not isinstance(presets, list):
        raise ValueError(f"{path.as_posix()}: subtypePresets must be an array")
    tags: list[str] = []
    for index, preset in enumerate(presets):
        if not isinstance(preset, dict) or not isinstance(preset.get("languageTag"), str):
            raise ValueError(f"{path.as_posix()}: subtypePresets[{index}] has no languageTag")
        language_tag = preset["languageTag"].strip()
        if not language_tag:
            raise ValueError(f"{path.as_posix()}: subtypePresets[{index}] has an empty languageTag")
        tags.append(language_tag)
    return tags, sorted({normalized_language(tag.split("-", 1)[0]) for tag in tags})


def placeholder_tokens(text: str) -> tuple[str, ...]:
    return tuple(PLACEHOLDER_RE.findall(text))


def pseudo_transform(text: str, locale: str) -> str:
    if locale == "en-XA":
        upper = "ȺƂČĐƐƑǤĦĨĴҠŁⱮŇØƤɊŘŞŦŬṼẆӾŶƵ"
        lower = "ȧƀƈđɛƒɠħɨĵķłɱƞøƥɋřşŧʉṽŵẋƴƶ"
        mapping = str.maketrans(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", upper + lower
        )
        pieces: list[str] = []
        cursor = 0
        for match in PLACEHOLDER_RE.finditer(text):
            pieces.append(text[cursor : match.start()].translate(mapping))
            pieces.append(match.group(0))
            cursor = match.end()
        pieces.append(text[cursor:].translate(mapping))
        return f"[{''.join(pieces)}]"
    if locale == "ar-XB":
        return f"\u202e[{text}]\u202c"
    raise ValueError(f"unsupported pseudolocale: {locale}")


def pseudo_screen_report(
    base: dict[str, Resource], critical: dict[str, Resource]
) -> tuple[dict[str, object], list[str]]:
    errors: list[str] = []
    screens: dict[str, list[Resource]] = {}
    for screen, prefixes in CRITICAL_SCREEN_PREFIXES.items():
        selected = [
            resource
            for resource in critical.values()
            if any(resource.name.startswith(prefix) for prefix in prefixes)
        ]
        screens[screen] = sorted(selected, key=lambda resource: resource.identity)
        if not selected:
            errors.append(f"pseudolocale screen group has no critical resources: {screen}")

    report: dict[str, object] = {}
    for locale, direction in (("en-XA", "ltr"), ("ar-XB", "rtl")):
        screen_report: dict[str, object] = {}
        locale_ok = True
        for screen, resources in screens.items():
            checked_values = 0
            failures = 0
            for resource in resources:
                for value in resource.values:
                    transformed = pseudo_transform(value, locale)
                    checked_values += 1
                    if placeholder_tokens(value) != placeholder_tokens(transformed):
                        failures += 1
                    if any(character.isalpha() for character in value):
                        if locale == "en-XA" and len(transformed) <= len(value):
                            failures += 1
                        if locale == "ar-XB" and (
                            "\u202e" not in transformed or "\u202c" not in transformed
                        ):
                            failures += 1
            screen_ok = bool(resources) and checked_values > 0 and failures == 0
            locale_ok = locale_ok and screen_ok
            screen_report[screen] = {
                "key_count": len(resources),
                "value_count": checked_values,
                "ok": screen_ok,
            }
        report[locale] = {
            "direction": direction,
            "screen_count": len(screen_report),
            "screens": screen_report,
            "ok": locale_ok,
        }
        if not locale_ok:
            errors.append(f"pseudolocale contract failed: {locale}")
    return report, errors


def find_hardcoded_critical_copy(root: Path) -> list[dict[str, object]]:
    source_root = root / "app/src/main/kotlin"
    violations: list[dict[str, object]] = []
    if not source_root.is_dir():
        return violations
    for source_path in sorted(source_root.rglob("*.kt")):
        source = source_path.read_text(encoding="utf-8")
        seen: set[tuple[int, str]] = set()
        for marker in UI_LITERAL_MARKER_RE.finditer(source):
            line_start = source.rfind("\n", 0, marker.start()) + 1
            marker_line = source[line_start : source.find("\n", marker.start())]
            if marker_line.lstrip().startswith(("//", "*", "/*")):
                continue
            # Restrict the literal search to the call's immediate argument
            # window.  This avoids mistaking a logging string on the previous
            # line, a clipboard label, or a code comment for visible UI copy.
            argument_window = source[marker.end() : marker.end() + 320]
            for literal in STRING_LITERAL_RE.finditer(argument_window):
                value = literal.group(1) or literal.group(2) or literal.group(3) or ""
                if not CRITICAL_COPY_RE.search(value):
                    continue
                line_number = source[: marker.start()].count("\n") + 1
                key = (line_number, value)
                if key in seen:
                    continue
                seen.add(key)
                violations.append(
                    {
                        "path": source_path.relative_to(root).as_posix(),
                        "line": line_number,
                    }
                )
    return violations


def exact_base_count(
    locale_resources: dict[str, Resource], critical: dict[str, Resource]
) -> int:
    return sum(
        1
        for identity, base_resource in critical.items()
        if identity in locale_resources
        and locale_resources[identity].values == base_resource.values
    )


def translation_route_errors(root: Path, locale_directories: list[str]) -> list[str]:
    """Every shipped locale must have a route back to the translation source.

    `crowdin.yml` maps a Crowdin locale onto the `%android_code%` substituted
    into the resource path. A `values-*` directory with no mapping entry can be
    edited by hand but can never round-trip: an upload will not carry it and a
    download will not update it, so the translation silently rots. That is how
    zh-rCN — the largest translation in the tree — ended up outside the
    pipeline. Assert the mapping covers what is actually shipped.
    """
    config_path = root / "crowdin.yml"
    try:
        config = config_path.read_text(encoding="utf-8")
    except FileNotFoundError:
        # Retiring the pipeline is a legitimate choice; it just has to be a
        # deliberate one, documented for contributors.
        try:
            guidance = (root / "CONTRIBUTING.md").read_text(encoding="utf-8")
        except FileNotFoundError:
            # Neither file present: a synthetic fixture tree, not a checkout.
            # Nothing to say about a translation route that was never claimed.
            return []
        if "translation" not in guidance.lower():
            return [
                "crowdin.yml is absent and CONTRIBUTING.md documents no "
                "translation path; contributors have no way to submit translations"
            ]
        return []

    mapped = set(re.findall(r'^\s{6,}"?[\w-]+"?:\s*"([^"]+)"\s*$', config, flags=re.MULTILINE))
    errors: list[str] = []
    for directory in sorted(locale_directories):
        android_code = directory.removeprefix("values-")
        if android_code not in mapped:
            errors.append(
                f"locale has no translation route in crowdin.yml: {directory} "
                f"(add a `<crowdin-code>: \"{android_code}\"` entry)"
            )
    return errors


def build_report(root: Path, *, check: bool, ratchet: bool) -> dict[str, object]:
    errors: list[str] = []
    structural_errors: list[str] = []

    try:
        base, base_duplicates = parse_resource_directory(root / RESOURCES_ROOT / "values")
        structural_errors.extend(base_duplicates)
    except ValueError as exc:
        structural_errors.append(str(exc))
        base = {}

    critical = {identity: resource for identity, resource in base.items() if is_critical(resource)}
    missing_required = required_critical_names(base)
    structural_errors.extend(
        f"missing required critical resource: string:{name}" for name in missing_required
    )

    try:
        typing_tags, typing_languages = parse_typing_languages(root)
    except ValueError as exc:
        structural_errors.append(str(exc))
        typing_tags, typing_languages = [], []

    locale_reports: list[dict[str, object]] = []
    locale_paths: dict[str, Path] = {}
    resource_root = root / RESOURCES_ROOT
    for path in sorted(resource_root.glob("values-*")):
        resource_locale = locale_directory_name(path)
        if resource_locale is None:
            continue
        bcp_locale = bcp47_locale(resource_locale)
        if bcp_locale in PSEUDO_LOCALES:
            continue
        locale_paths[path.name] = path
        try:
            resources, duplicates = parse_resource_directory(path)
            structural_errors.extend(duplicates)
        except ValueError as exc:
            structural_errors.append(str(exc))
            resources = {}
        missing = set(base) - set(resources)
        missing_critical = sorted(set(critical) - set(resources))
        locale_reports.append(
            {
                "directory": path.name,
                "locale": bcp_locale,
                "language": locale_language(resource_locale),
                "resource_count": len(resources),
                "coverage_percent": round((len(resources) / len(base) * 100), 2)
                if base
                else 0.0,
                "missing_resource_count": len(missing),
                "critical_present_count": len(critical) - len(missing_critical),
                "critical_missing_count": len(missing_critical),
                "critical_missing_keys": [
                    identity.split(":", 1)[1] for identity in missing_critical
                ],
                "critical_exact_base_count": exact_base_count(resources, critical),
                "status": (
                    "complete_reviewed"
                    if not missing and bcp_locale in REVIEWED_UI_LOCALES
                    else "complete_unreviewed"
                    if not missing
                    else "partial_fallback"
                ),
                "typing_language": normalized_language(locale_language(resource_locale))
                in set(typing_languages),
            }
        )

    floor_errors: list[str] = []
    if ratchet:
        expected_directories = set(RESOURCE_COVERAGE_FLOORS)
        actual_directories = set(locale_paths)
        for missing_directory in sorted(expected_directories - actual_directories):
            floor_errors.append(f"ratchet locale directory missing: {missing_directory}")
        for unexpected_directory in sorted(actual_directories - expected_directories):
            floor_errors.append(f"unratcheted locale directory: {unexpected_directory}")
        for locale_report in locale_reports:
            directory = str(locale_report["directory"])
            if directory not in RESOURCE_COVERAGE_FLOORS:
                continue
            resource_floor, critical_floor = RESOURCE_COVERAGE_FLOORS[directory]
            resource_count = int(locale_report["resource_count"])
            critical_count = int(locale_report["critical_present_count"])
            if resource_count < resource_floor:
                floor_errors.append(
                    f"{directory}: resource coverage regressed from {resource_floor} to {resource_count}"
                )
            if critical_count < critical_floor:
                floor_errors.append(
                    f"{directory}: critical coverage regressed from {critical_floor} to {critical_count}"
                )

    complete_ui_locales = sorted(REVIEWED_UI_LOCALES)
    complete_errors: list[str] = []
    for locale in complete_ui_locales:
        if locale == "en":
            continue
        matching = [item for item in locale_reports if item["locale"] == locale]
        if not matching:
            complete_errors.append(f"reviewed UI locale has no resource directory: {locale}")
            continue
        item = matching[0]
        if item["status"] != "complete_reviewed":
            complete_errors.append(f"reviewed UI locale is not complete: {locale}")
    complete_errors.extend(
        f"unreviewed locale has complete resources: {item['locale']}"
        for item in locale_reports
        if item["status"] == "complete_unreviewed"
    )

    pseudo_report, pseudo_errors = pseudo_screen_report(base, critical)
    hardcoded_copy = find_hardcoded_critical_copy(root)
    hardcoded_errors = [
        f"hard-coded critical UI copy: {violation['path']}:{violation['line']}"
        for violation in hardcoded_copy
    ]

    route_errors = translation_route_errors(root, sorted(locale_paths))

    if check:
        errors.extend(structural_errors)
        errors.extend(floor_errors)
        errors.extend(complete_errors)
        errors.extend(pseudo_errors)
        errors.extend(hardcoded_errors)
        errors.extend(route_errors)

    resource_languages = sorted({item["language"] for item in locale_reports})
    typing_only_languages = sorted(
        set(typing_languages) - {normalized_language(language) for language in resource_languages}
    )
    partial_locales = sorted(
        item["locale"] for item in locale_reports if item["status"] == "partial_fallback"
    )

    report: dict[str, object] = {
        "schema_version": 1,
        "base": {
            "resource_count": len(base),
            "critical_resource_count": len(critical),
            "critical_keys": sorted(
                identity.split(":", 1)[1] for identity in critical
            ),
        },
        "ui_coverage": {
            "reviewed_ui_locales": complete_ui_locales,
            "complete_ui_locales": complete_ui_locales,
            "partial_fallback_locales": partial_locales,
            "locale_count": len(locale_reports),
            "locales": locale_reports,
        },
        "typing_language_coverage": {
            "subtype_count": len(typing_tags),
            "unique_language_count": len(typing_languages),
            "language_tags": sorted(set(typing_tags)),
            "languages_with_ui_resources": sorted(
                normalized_language(language) for language in resource_languages
            ),
            "typing_only_languages": typing_only_languages,
        },
        "pseudolocales": pseudo_report,
        "hard_coded_critical_copy": hardcoded_copy,
        "ratchet": {
            "enabled": ratchet,
            "floor_locale_count": len(RESOURCE_COVERAGE_FLOORS),
            "floor_errors": floor_errors,
        },
        "violations": {
            "structural": structural_errors,
            "complete_ui": complete_errors,
            "pseudolocale": pseudo_errors,
            "hard_coded_critical_copy": hardcoded_errors,
            "translation_route": route_errors,
        },
        "errors": errors,
    }
    return report


def print_summary(report: dict[str, object]) -> None:
    base = report["base"]
    coverage = report["ui_coverage"]
    typing = report["typing_language_coverage"]
    pseudo = report["pseudolocales"]
    print(
        "locale-coverage: "
        f"{base['resource_count']} base resources, "
        f"{base['critical_resource_count']} critical resources"
    )
    print(
        "ui coverage: "
        f"{coverage['locale_count']} translated locales; "
        f"complete={','.join(coverage['complete_ui_locales']) or 'none'}; "
        f"partial/fallback={len(coverage['partial_fallback_locales'])}"
    )
    print(
        "typing coverage: "
        f"{typing['subtype_count']} subtype presets; "
        f"typing-only languages={','.join(typing['typing_only_languages']) or 'none'}"
    )
    pseudo_ok = all(bool(item["ok"]) for item in pseudo.values())
    print(f"pseudolocales: {'OK' if pseudo_ok else 'FAIL'} (en-XA, ar-XB critical screens)")
    errors = report["errors"]
    if errors:
        print(f"locale-coverage: FAIL ({len(errors)} error(s))")
        for error in errors:
            print(f"::error::locale-coverage: {error}")
    else:
        print("locale-coverage: OK")


def main() -> int:
    arguments = parse_args()
    root = Path(arguments.root).resolve()
    try:
        report = build_report(
            root,
            check=arguments.check,
            ratchet=not arguments.no_ratchet,
        )
    except (OSError, ValueError, TypeError) as exc:
        if arguments.json:
            print(json.dumps({"schema_version": 1, "errors": [str(exc)]}, indent=2))
        else:
            print(f"::error::locale-coverage: {exc}")
        return 1

    if arguments.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_summary(report)
    return 1 if report["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
