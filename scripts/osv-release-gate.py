#!/usr/bin/env python3
"""
scripts/osv-release-gate.py

Release-time OSV severity gate. Parses osv-result.json and fails if any
HIGH, CRITICAL, or unclassified advisory is present that is not explicitly overridden in
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
import math
import sys
from datetime import date
from pathlib import Path

OSV_RESULT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("osv-result.json")
OVERRIDES_FILE = Path(".github/osv-overrides.json")

BLOCKING_SEVERITIES = {"HIGH", "CRITICAL", "UNKNOWN"}
SEVERITY_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2, "CRITICAL": 3, "UNKNOWN": 4}


def classify_score(score):
    """Map a numeric CVSS score to the standard qualitative severity."""
    if not isinstance(score, (int, float)) or isinstance(score, bool):
        return None
    if not 0.0 <= score <= 10.0:
        return None
    if score >= 9.0:
        return "CRITICAL"
    if score >= 7.0:
        return "HIGH"
    if score >= 4.0:
        return "MEDIUM"
    return "LOW"


def roundup(value):
    """Round a CVSS value up to one decimal place."""
    return math.ceil((value - 1e-10) * 10.0) / 10.0


def parse_cvss_vector(vector):
    """Return a CVSS v3 base score, or None for unsupported/invalid vectors.

    OSV commonly publishes CVSS v3 vectors instead of a numeric score. CVSS v4
    uses a lookup/interpolation model rather than the v3 equation; it must not
    be treated as the numeric version prefix. If a v4 result has no numeric
    score, the caller falls back to OSV's database severity and otherwise fails
    closed as UNKNOWN.
    """
    if not isinstance(vector, str):
        return None
    parts = vector.strip().split("/")
    if not parts or parts[0] not in {"CVSS:3.0", "CVSS:3.1"}:
        return None

    metrics = {}
    for part in parts[1:]:
        name, separator, value = part.partition(":")
        if not separator or name in metrics:
            return None
        metrics[name] = value

    required = {"AV", "AC", "PR", "UI", "S", "C", "I", "A"}
    if not required.issubset(metrics) or set(metrics) - required - {"E", "RL", "RC"}:
        return None

    weights = {
        "AV": {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.20},
        "AC": {"L": 0.77, "H": 0.44},
        "UI": {"N": 0.85, "R": 0.62},
        "C": {"N": 0.0, "L": 0.22, "H": 0.56},
        "I": {"N": 0.0, "L": 0.22, "H": 0.56},
        "A": {"N": 0.0, "L": 0.22, "H": 0.56},
    }
    try:
        av = weights["AV"][metrics["AV"]]
        ac = weights["AC"][metrics["AC"]]
        ui = weights["UI"][metrics["UI"]]
        confidentiality = weights["C"][metrics["C"]]
        integrity = weights["I"][metrics["I"]]
        availability = weights["A"][metrics["A"]]
        scope = metrics["S"]
        if scope not in {"U", "C"}:
            return None
        if scope == "U":
            pr = {"N": 0.85, "L": 0.62, "H": 0.27}[metrics["PR"]]
        else:
            pr = {"N": 0.85, "L": 0.68, "H": 0.50}[metrics["PR"]]
    except (KeyError, TypeError):
        return None

    impact_sub_score = 1.0 - (
        (1.0 - confidentiality)
        * (1.0 - integrity)
        * (1.0 - availability)
    )
    if scope == "U":
        impact = 6.42 * impact_sub_score
    else:
        impact = 7.52 * (impact_sub_score - 0.029) - 3.25 * (impact_sub_score - 0.02) ** 15
    if impact <= 0.0:
        return 0.0

    exploitability = 8.22 * av * ac * pr * ui
    if scope == "U":
        base_score = min(impact + exploitability, 10.0)
    else:
        base_score = min(1.08 * (impact + exploitability), 10.0)
    return roundup(base_score)


def extract_cvss_score(score_value):
    """Extract a numeric score or calculate one from a supported CVSS vector."""
    if isinstance(score_value, (int, float)) and not isinstance(score_value, bool):
        return float(score_value)
    if isinstance(score_value, str):
        stripped = score_value.strip()
        try:
            return float(stripped)
        except ValueError:
            return parse_cvss_vector(stripped)
    return None


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
    candidates = []
    for sv in vuln.get("severity", []):
        if "CVSS" not in str(sv.get("type", "")).upper():
            continue
        severity = classify_score(extract_cvss_score(sv.get("score", "")))
        if severity is not None:
            candidates.append(severity)
    database_specific = vuln.get("database_specific", {})
    db_severity = database_specific.get("severity", "").upper() if isinstance(database_specific, dict) else ""
    if db_severity in ("CRITICAL", "HIGH", "MEDIUM", "LOW"):
        candidates.append(db_severity)
    return max(candidates, key=SEVERITY_ORDER.get) if candidates else "UNKNOWN"


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
        print(f"\n::error::Release blocked by {len(blocking)} HIGH/CRITICAL/UNKNOWN advisory(ies):")
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
