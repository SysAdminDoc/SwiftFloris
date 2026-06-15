#!/usr/bin/env python3
"""
scripts/osv-release-gate.py

Release-time OSV severity gate. Parses osv-result.json and fails if any
HIGH or CRITICAL advisory is present that is not explicitly overridden in
.github/osv-overrides.json.

Exit codes:
  0 — no blocking findings (clean, or all high/critical overridden)
  1 — at least one high/critical advisory is not overridden; release blocked
  2 — osv-result.json missing or unparseable (scan did not complete)

Override file format (.github/osv-overrides.json):
{
  "overrides": [
    {
      "id": "GHSA-xxxx-yyyy-zzzz",
      "severity": "HIGH",
      "rationale": "Not reachable: SwiftFloris never calls the affected API.",
      "owner": "matt_parker@outlook.com",
      "expiry": "2026-09-01"
    }
  ]
}
"""

import json
import sys
from datetime import date
from pathlib import Path

OSV_RESULT = Path("osv-result.json")
OVERRIDES_FILE = Path(".github/osv-overrides.json")

BLOCKING_SEVERITIES = {"HIGH", "CRITICAL"}


def load_overrides():
    if not OVERRIDES_FILE.exists():
        return {}
    data = json.loads(OVERRIDES_FILE.read_text())
    today = date.today().isoformat()
    active = {}
    for entry in data.get("overrides", []):
        advisory_id = entry.get("id", "")
        expiry = entry.get("expiry", "")
        if expiry and expiry < today:
            print(f"::warning::OSV override expired: {advisory_id} (expiry {expiry})")
            continue
        if not all(entry.get(k) for k in ("id", "severity", "rationale", "owner")):
            print(f"::warning::OSV override missing required fields: {entry}")
            continue
        active[advisory_id] = entry
    return active


def extract_severity(vuln):
    for sv in vuln.get("severity", []):
        score_str = sv.get("score", "")
        if "CVSS" in sv.get("type", ""):
            try:
                score = float(score_str.split("/")[0].split(":")[-1])
            except (ValueError, IndexError):
                continue
            if score >= 9.0:
                return "CRITICAL"
            if score >= 7.0:
                return "HIGH"
            if score >= 4.0:
                return "MEDIUM"
            return "LOW"
    db_severity = vuln.get("database_specific", {}).get("severity", "").upper()
    if db_severity in ("CRITICAL", "HIGH", "MEDIUM", "LOW"):
        return db_severity
    return "UNKNOWN"


def main():
    if not OSV_RESULT.exists() or OSV_RESULT.stat().st_size == 0:
        print("::warning::osv-result.json missing or empty — scan did not complete.")
        sys.exit(2)

    try:
        data = json.loads(OSV_RESULT.read_text())
    except json.JSONDecodeError as e:
        print(f"::error::osv-result.json is not valid JSON: {e}")
        sys.exit(2)

    overrides = load_overrides()

    blocking = []
    non_blocking = []

    for result in data.get("results", []):
        for pkg in result.get("packages", []):
            pkg_name = pkg.get("package", {}).get("name", "unknown")
            for vuln in pkg.get("vulnerabilities", []):
                vuln_id = vuln.get("id", "unknown")
                severity = extract_severity(vuln)
                summary = vuln.get("summary", "")[:120]
                aliases = vuln.get("aliases", [])

                all_ids = {vuln_id} | set(aliases)
                overridden = any(aid in overrides for aid in all_ids)

                entry = {
                    "id": vuln_id,
                    "severity": severity,
                    "package": pkg_name,
                    "summary": summary,
                    "overridden": overridden,
                }

                if severity in BLOCKING_SEVERITIES and not overridden:
                    blocking.append(entry)
                else:
                    non_blocking.append(entry)

    if non_blocking:
        print(f"Non-blocking findings ({len(non_blocking)}):")
        for e in non_blocking:
            status = " [overridden]" if e["overridden"] else ""
            print(f"  {e['severity']:8s} {e['id']} ({e['package']}){status}")

    if blocking:
        print(f"\n::error::Release blocked by {len(blocking)} HIGH/CRITICAL advisory(ies):")
        for e in blocking:
            print(f"  {e['severity']:8s} {e['id']} ({e['package']}): {e['summary']}")
        print(
            "\nTo override, add entries to .github/osv-overrides.json with:"
            "\n  id, severity, rationale, owner, expiry (YYYY-MM-DD)"
        )
        sys.exit(1)

    total = len(blocking) + len(non_blocking)
    if total == 0:
        print("OSV release gate: PASS (0 advisories)")
    else:
        print(f"OSV release gate: PASS ({total} advisory(ies), none blocking)")


if __name__ == "__main__":
    main()
