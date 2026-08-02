#!/usr/bin/env python3
"""Self-tests for check-locale-coverage.py."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts/check-locale-coverage.py"


BASE_RESOURCES = """\
<resources>
    <string name="settings__privacy_posture__title">Privacy posture</string>
    <string name="settings__privacy_audit__title">Privacy audit</string>
    <string name="backup_and_restore__back_up__failure_title">Backup failed</string>
    <string name="backup_and_restore__restore__failure_title">Restore failed</string>
    <string name="setup__grant_notification_permission__title">Notification permission</string>
    <string name="calendar__permission__title">Calendar permission</string>
    <string name="action__delete_confirm_title">Confirm delete</string>
    <string name="general__hello">Hello</string>
</resources>
"""


def write_fixture(root: Path, *, hardcoded: bool = False) -> None:
    values = root / "app/src/main/res/values"
    values.mkdir(parents=True)
    (values / "strings.xml").write_text(BASE_RESOURCES, encoding="utf-8")
    fr = root / "app/src/main/res/values-fr"
    fr.mkdir()
    (fr / "strings.xml").write_text(
        "<resources>\n"
        '    <string name="settings__privacy_posture__title">Posture de confidentialité</string>\n'
        '    <string name="general__hello">Bonjour</string>\n'
        "</resources>\n",
        encoding="utf-8",
    )
    manifest = root / (
        "app/src/main/assets/ime/keyboard/"
        "org.florisboard.localization/extension.json"
    )
    manifest.parent.mkdir(parents=True)
    manifest.write_text(
        json.dumps(
            {
                "subtypePresets": [
                    {"languageTag": "en-US"},
                    {"languageTag": "fr-FR"},
                ]
            }
        ),
        encoding="utf-8",
    )
    source = root / "app/src/main/kotlin/dev/example/Fixture.kt"
    source.parent.mkdir(parents=True)
    source.write_text(
        'fun fixture() { Text("Delete backup") }\n' if hardcoded else
        'fun fixture() { Text(stringResource(R.string.action__delete)) }\n',
        encoding="utf-8",
    )


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(CHECKER),
            "--root",
            str(root),
            "--check",
            "--no-ratchet",
            "--json",
        ],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


def test_fixture_report_and_pseudolocales() -> None:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        write_fixture(root)
        result = run_checker(root)
        assert result.returncode == 0, result.stdout + result.stderr
        report = json.loads(result.stdout)
        assert report["errors"] == []
        assert report["base"]["resource_count"] == 8
        assert report["base"]["critical_resource_count"] == 7
        assert report["ui_coverage"]["complete_ui_locales"] == ["en"]
        assert report["ui_coverage"]["locales"][0]["status"] == "partial_fallback"
        assert report["pseudolocales"]["en-XA"]["ok"] is True
        assert report["pseudolocales"]["ar-XB"]["ok"] is True
        assert report["typing_language_coverage"]["subtype_count"] == 2


def test_hardcoded_critical_ui_copy_is_a_gate() -> None:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        write_fixture(root, hardcoded=True)
        result = run_checker(root)
        assert result.returncode == 1
        report = json.loads(result.stdout)
        assert report["hard_coded_critical_copy"] == [
            {"line": 1, "path": "app/src/main/kotlin/dev/example/Fixture.kt"}
        ]
        assert any("hard-coded critical UI copy" in error for error in report["errors"])


def main() -> int:
    test_fixture_report_and_pseudolocales()
    test_hardcoded_critical_ui_copy_is_a_gate()
    print("test-check-locale-coverage: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
