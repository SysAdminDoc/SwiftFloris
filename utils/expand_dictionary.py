#!/usr/bin/env python3
"""
Merge SCOWL word membership into the existing en data.json so common words keep
their corpus-derived 128-255 frequency ranking while the long tail of rare-but-
valid English words gets added at frequency 80-127. Result: ~80-100k words, no
more red-squiggles on legitimate uncommon words, but autocorrect still prefers
the canonical high-frequency forms.

Usage:
    python3 utils/expand_dictionary.py \\
        --existing app/src/main/assets/ime/dict/data.json \\
        --scowl /tmp/scowl_clean.txt \\
        --output app/src/main/assets/ime/dict/data.json
"""

import argparse
import json
import re
import sys
from pathlib import Path

# Words must be lowercase ASCII letters with optional internal apostrophe or hyphen.
# Single-letter words allowed (a, i). Excludes anything with digits, accents, etc.
WORD_RE = re.compile(r"^[a-z](?:[a-z'-]*[a-z])?$")

# Tail frequency band assigned to SCOWL words not present in the existing dict.
# Stays strictly below the existing 128-255 band so curated frequencies still rank first.
TAIL_FREQ_MAX = 127
TAIL_FREQ_MIN = 80


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--existing", required=True, type=Path, help="Existing data.json path")
    parser.add_argument("--scowl", required=True, type=Path, help="Combined SCOWL wordlist (one word per line)")
    parser.add_argument("--output", required=True, type=Path, help="Output data.json path (can be same as --existing)")
    args = parser.parse_args()

    if not args.existing.is_file():
        print(f"error: existing dictionary not found: {args.existing}", file=sys.stderr)
        return 1
    if not args.scowl.is_file():
        print(f"error: scowl wordlist not found: {args.scowl}", file=sys.stderr)
        return 1

    with args.existing.open("r", encoding="utf-8") as fh:
        existing = json.load(fh)
    print(f"existing: {len(existing)} words, freq range "
          f"{min(existing.values())}–{max(existing.values())}", file=sys.stderr)

    # Build SCOWL set: lowercase, normalized, valid-word-shape only.
    scowl_words: list[str] = []
    seen: set[str] = set()
    with args.scowl.open("r", encoding="utf-8") as fh:
        for raw in fh:
            w = raw.strip().lower()
            if not w:
                continue
            if not WORD_RE.fullmatch(w):
                continue
            if w in seen:
                continue
            seen.add(w)
            scowl_words.append(w)
    print(f"scowl: {len(scowl_words)} valid words after shape filter", file=sys.stderr)

    # Words to add: SCOWL words not already in the existing dictionary.
    new_words = [w for w in scowl_words if w not in existing]
    print(f"new tail words to add: {len(new_words)}", file=sys.stderr)

    # Assign descending frequencies in the tail band based on input order.
    # SCOWL files are concatenated 10 → 60, so earlier entries are more common.
    n = len(new_words)
    if n == 0:
        merged = dict(existing)
    else:
        tail_span = TAIL_FREQ_MAX - TAIL_FREQ_MIN
        merged = dict(existing)
        for i, w in enumerate(new_words):
            # i=0 → TAIL_FREQ_MAX; i=n-1 → TAIL_FREQ_MIN
            freq = TAIL_FREQ_MAX - int(round(tail_span * i / max(n - 1, 1)))
            merged[w] = freq

    print(f"merged: {len(merged)} words, freq range "
          f"{min(merged.values())}–{max(merged.values())}", file=sys.stderr)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as fh:
        # Compact JSON, no whitespace — matches existing data.json shape.
        json.dump(merged, fh, ensure_ascii=False, separators=(",", ":"))
        fh.write("\n")

    print(f"wrote {args.output} ({args.output.stat().st_size:,} bytes)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
