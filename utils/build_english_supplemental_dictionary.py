#!/usr/bin/env python3
"""
Build the low-priority supplemental English dictionary.

The main English dictionary keeps curated frequencies in the 80-255 range. This
supplement adds large SCOWL long-tail membership and a small modern-terms list
below the autocorrect auto-commit thresholds, so legitimate words are recognized
without making rare words aggressive corrections.
"""

import argparse
import json
import re
import sys
from pathlib import Path

WORD_RE = re.compile(r"^[a-z](?:[a-z']*[a-z])?$")

SCOWL_FILES = (
    "english-words.70",
    "american-words.70",
    "british-words.70",
    "canadian-words.70",
    "english-words.80",
    "american-words.80",
    "british-words.80",
    "canadian-words.80",
    "english-proper-names.70",
    "american-proper-names.80",
)

SCOWL_FREQ_MAX = 79
SCOWL_FREQ_MIN = 48
MODERN_TERM_FREQ = 96


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path, help="Existing data.json dictionary")
    parser.add_argument("--scowl-final", required=True, type=Path, help="SCOWL final/ directory")
    parser.add_argument("--modern-terms", required=True, type=Path, help="One lowercase term per line")
    parser.add_argument("--profanity", type=Path, help="Optional one-term-per-line profanity blocklist")
    parser.add_argument("--output-json", required=True, type=Path, help="Supplemental JSON output")
    parser.add_argument("--output-word-list", required=True, type=Path, help="Merged en.txt output")
    args = parser.parse_args()

    if not args.base.is_file():
        print(f"error: base dictionary not found: {args.base}", file=sys.stderr)
        return 1
    if not args.scowl_final.is_dir():
        print(f"error: SCOWL final directory not found: {args.scowl_final}", file=sys.stderr)
        return 1
    if not args.modern_terms.is_file():
        print(f"error: modern terms file not found: {args.modern_terms}", file=sys.stderr)
        return 1

    base = load_json_dictionary(args.base)
    profanity = load_word_set(args.profanity) if args.profanity else set()
    scowl_words = load_scowl_words(args.scowl_final, profanity)
    modern_terms = load_modern_terms(args.modern_terms, profanity)

    supplemental: dict[str, int] = {}
    scowl_new_words = [word for word in scowl_words if word not in base]
    for index, word in enumerate(scowl_new_words):
        supplemental[word] = scaled_frequency(
            index=index,
            count=len(scowl_new_words),
            minimum=SCOWL_FREQ_MIN,
            maximum=SCOWL_FREQ_MAX,
        )
    for word in modern_terms:
        if word not in base:
            supplemental[word] = max(supplemental.get(word, 0), MODERN_TERM_FREQ)

    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    with args.output_json.open("w", encoding="utf-8") as fh:
        json.dump(dict(sorted(supplemental.items())), fh, ensure_ascii=False, separators=(",", ":"))
        fh.write("\n")

    merged = dict(base)
    for word, frequency in supplemental.items():
        merged[word] = max(merged.get(word, 0), frequency)

    args.output_word_list.parent.mkdir(parents=True, exist_ok=True)
    with args.output_word_list.open("w", encoding="utf-8", newline="\n") as fh:
        for word, _ in sorted(merged.items(), key=lambda item: (-item[1], item[0])):
            fh.write(f"{word}\n")

    print(f"base words: {len(base)}", file=sys.stderr)
    print(f"supplemental words: {len(supplemental)}", file=sys.stderr)
    print(f"merged words: {len(merged)}", file=sys.stderr)
    print(f"wrote {args.output_json} ({args.output_json.stat().st_size:,} bytes)", file=sys.stderr)
    print(f"wrote {args.output_word_list} ({args.output_word_list.stat().st_size:,} bytes)", file=sys.stderr)
    return 0


def load_json_dictionary(path: Path) -> dict[str, int]:
    with path.open("r", encoding="utf-8") as fh:
        raw = json.load(fh)
    return {
        word: int(frequency)
        for word, frequency in raw.items()
        if valid_word(word)
    }


def load_scowl_words(final_dir: Path, profanity: set[str]) -> list[str]:
    seen: set[str] = set()
    words: list[str] = []
    for file_name in SCOWL_FILES:
        path = final_dir / file_name
        if not path.is_file():
            continue
        with path.open("r", encoding="latin-1") as fh:
            for raw in fh:
                word = normalize_word(raw)
                if word is None or word in profanity or word in seen:
                    continue
                seen.add(word)
                words.append(word)
    return words


def load_modern_terms(path: Path, profanity: set[str]) -> list[str]:
    terms: list[str] = []
    seen: set[str] = set()
    with path.open("r", encoding="utf-8") as fh:
        for raw in fh:
            word = normalize_word(raw)
            if word is None or word in profanity or word in seen:
                continue
            seen.add(word)
            terms.append(word)
    return terms


def load_word_set(path: Path | None) -> set[str]:
    if path is None or not path.is_file():
        return set()
    with path.open("r", encoding="utf-8", errors="ignore") as fh:
        return {
            word
            for raw in fh
            if (word := normalize_word(raw)) is not None
        }


def normalize_word(raw: str) -> str | None:
    word = raw.strip().lower()
    return word if valid_word(word) else None


def valid_word(word: str) -> bool:
    return bool(WORD_RE.fullmatch(word))


def scaled_frequency(index: int, count: int, minimum: int, maximum: int) -> int:
    if count <= 1:
        return maximum
    span = maximum - minimum
    return maximum - round(span * index / (count - 1))


if __name__ == "__main__":
    sys.exit(main())
