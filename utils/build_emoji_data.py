#!/usr/bin/env python3
"""
Build the emoji palette and search data (app/src/main/assets/ime/media/emoji/*.txt).

Each file lists every fully qualified emoji from Unicode's emoji-test.txt in
palette order under its category, with the CLDR name and keywords of one
language. root.txt has the emoji without names, for languages CLDR doesn't
annotate. It replaces FlorisBoard's emojicon.py, which wrote CLDR's XML
escapes as they were: a keyword like "<3" came out as "&lt;3", and the
semicolon in it split the line, so the app dropped that emoji.

The shipped files came from Unicode 18.0 and CLDR release-49:

    curl -O https://www.unicode.org/Public/18.0.0/emoji/emoji-test.txt
    # common/annotations/<lang>.xml and common/annotationsDerived/<lang>.xml
    # from https://github.com/unicode-org/cldr/tree/release-49 into cldr/
    python utils/build_emoji_data.py emoji-test.txt cldr/ 49 \
        app/src/main/assets/ime/media/emoji ar ast az bg bn ca cs da de el en es \
        fa fi fo fr he hi hr hu hy id ig is it ja ka kab ko ku lt lv nn no pl pt ro ru \
        sk sl sr sv ta th tr uk ur vi zh

The languages are the ones a bundled keyboard preset types (CLDR spells
Hebrew "he" and Norwegian Bokmal "no"; EmojiData maps the keyboard's codes).
CLDR 49 has no annotations for eo, hoc, rue or udm, and only one emoji for
ckb, so those keyboards search the English names.

Line format, one emoji per line, UTF-8:

    <emoji>;<name>;<keyword>|<keyword>|...

A skin tone variant follows its base on a tab-indented line. The app gives
it its base's name and keywords, so the variant's line carries no name and
only the keywords its base lacks (the tone).

With --usage-counts, it also writes ime/media/emoji_usage_rank.txt next to the
emoji folder, which the app uses to order emoji suggestions that score the
same. The shipped one came from Unicode's emoji-freq.txt (842 emoji with usage
counts, a Unicode Data File under the Unicode License):

    curl -O https://raw.githubusercontent.com/unicode-org/unicodetools/8d6bd2e50b541f68bb86ae4205213f7af71aa8bb/unicodetools/src/main/resources/org/unicode/props/emoji-freq.txt
    python utils/build_emoji_data.py --usage-counts emoji-freq.txt emoji-test.txt cldr/ 49 ...

Each line of that file is one rank, most used first: the emoji followed by
every skin tone and gender form of it, separated by spaces. Emoji the counts
don't cover (newer ones, most ZWJ sequences) have no line.
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

CATEGORIES = {
    "Smileys & Emotion": "smileys_emotion",
    "People & Body": "people_body",
    "Animals & Nature": "animals_nature",
    "Food & Drink": "food_drink",
    "Travel & Places": "travel_places",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}
SKIN_TONES = range(0x1F3FB, 0x1F400)
VARIATION_SELECTOR_16 = chr(0xFE0F)
ZERO_WIDTH_JOINER = chr(0x200D)
GENDER_SIGNS = (chr(0x2640), chr(0x2642))
# CLDR's "same as the parent locale" marker.
INHERITED = chr(0x2191) * 3
# The <parentLocales> entries in CLDR's supplementalData.xml that apply to a
# language listed here. Every other one inherits from root, which has no names.
PARENT_LOCALES = {"nn": "no"}
# CLDR's release data is "contributed" or "approved" (ICU's cutoff); entries below that are proposals.
# Kabyle is the only language here with any (1802 in CLDR 49).
UNRELEASED_DRAFTS = {"unconfirmed", "provisional"}
NO_BREAK_SPACE = chr(0xA0)
ZERO_WIDTH_SPACE = chr(0x200B)


def parse_emoji_test(path):
    """(Emoji version, {category: [[base, variant, ...], ...]}) from emoji-test.txt."""
    version = None
    categories = {category: [] for category in CATEGORIES.values()}
    category = None
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.startswith("# Version:"):
            version = line.split(":", 1)[1].strip()
        elif line.startswith("# group:"):
            category = CATEGORIES.get(line.split(":", 1)[1].strip())
        elif line.startswith("#") or not line.strip() or category is None:
            continue
        else:
            fields = line.split("#", 1)[0].split(";")
            if fields[1].strip() != "fully-qualified":
                continue
            code_points = [int(cp, 16) for cp in fields[0].split()]
            emoji = "".join(map(chr, code_points))
            if len(code_points) > 1 and code_points[1] in SKIN_TONES and categories[category]:
                categories[category][-1].append(emoji)
            else:
                categories[category].append([emoji])
    if version is None:
        sys.exit(f"{path} has no '# Version:' line")
    return version, categories


def parse_annotations(path):
    """{emoji: (name, [keywords])} from one CLDR annotations file, XML escapes resolved."""
    names, keywords = {}, {}
    if not path.is_file():
        return {}
    for annotation in ElementTree.parse(path).getroot().iter("annotation"):
        emoji, text = annotation.get("cp"), (annotation.text or "").strip()
        if not emoji or not text or text == INHERITED or annotation.get("draft") in UNRELEASED_DRAFTS:
            continue
        if annotation.get("type") == "tts":
            names[emoji] = text
        else:
            keywords[emoji] = [word.strip() for word in text.split("|") if word.strip()]
    return {emoji: (names.get(emoji, ""), keywords.get(emoji, [])) for emoji in names.keys() | keywords.keys()}


def collapse_spaces(text):
    """Line breaks, no-break spaces and runs of spaces become one space and zero-width spaces go, so the app's
    word search splits Czech and Slovak names at the no-break space CLDR puts after a one-letter word. French's
    narrow no-break space before a colon ("homme : chauve") stays."""
    text = text.replace(ZERO_WIDTH_SPACE, "")
    return re.sub(f"[ \t\r\n{NO_BREAK_SPACE}]+", " ", text).strip(" ")


def clean_name(name):
    return collapse_spaces(name.replace(";", ","))


def clean_keywords(words):
    seen, kept = set(), []
    for word in words:
        word = collapse_spaces(word)
        if word and ";" not in word and "|" not in word and word.lower() not in seen:
            seen.add(word.lower())
            kept.append(word)
    return kept


def lookup(annotations, emoji):
    return annotations.get(emoji.replace(VARIATION_SELECTOR_16, "")) or annotations.get(emoji) or ("", [])


def write_file(path, categories, annotations, emoji_version, cldr_version):
    lines = [
        "# Generated by utils/build_emoji_data.py from Unicode emoji-test.txt and CLDR annotations",
        "# DO NOT EDIT MANUALLY!",
        f"# CLDR-VERSION: {cldr_version}",
        f"# EMOJI-VERSION: {emoji_version}",
        "",
    ]
    def data_line(line):
        if line.count(";") != 2:
            sys.exit(f"{path.name}: a line doesn't have exactly three fields: {line!r}")
        return line

    for category, emoji_sets in categories.items():
        lines.append(f"[{category}]")
        for emoji_set in emoji_sets:
            base_name, base_keywords = lookup(annotations, emoji_set[0])
            base_keywords = clean_keywords(base_keywords)
            lines.append(data_line(f"{emoji_set[0]};{clean_name(base_name)};{'|'.join(base_keywords)}"))
            known = {word.lower() for word in base_keywords}
            for variant in emoji_set[1:]:
                extra = [word for word in clean_keywords(lookup(annotations, variant)[1]) if word.lower() not in known]
                lines.append(data_line(f"\t{variant};;{'|'.join(extra)}"))
        lines.append("")
    path.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))


def parse_usage_counts(path):
    """Emoji from unicodetools' emoji-freq.txt ("U+1F44D<TAB>15311339"), most used first."""
    counted = []
    for number, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        code_points, _, count = line.partition("\t")
        try:
            emoji = "".join(chr(int(cp.removeprefix("U+"), 16)) for cp in code_points.split())
            counted.append((int(count), emoji))
        except ValueError:
            sys.exit(f"{path}:{number}: not '<U+hex ...><TAB><count>': {line!r}")
    # sorted() is stable, so equal counts keep the file's order.
    return [emoji for _, emoji in sorted(counted, key=lambda entry: -entry[0])]


def usage_key(emoji):
    """What emoji-freq.txt counts an emoji as: no variation selector, skin tone or gender sign. Unicode
    tallies the skin tone and gender forms of an emoji together, so 👍🏾 and 🤦‍♀️ share 👍's and 🤦's rank."""
    key = "".join(ch for ch in emoji if ch != VARIATION_SELECTOR_16 and ord(ch) not in SKIN_TONES)
    for sign in GENDER_SIGNS:
        key = key.removesuffix(ZERO_WIDTH_JOINER + sign)
    return key


def write_usage_rank(path, categories, ranking, emoji_version, source):
    forms = {}
    for emoji_sets in categories.values():
        for emoji_set in emoji_sets:
            for emoji in emoji_set:
                forms.setdefault(usage_key(emoji), []).append(emoji)
    lines = [
        f"# Generated by utils/build_emoji_data.py from Unicode emoji-test.txt and {source}",
        "# DO NOT EDIT MANUALLY!",
        f"# EMOJI-VERSION: {emoji_version}",
        "# One line per rank, most used first: an emoji and its skin tone and gender forms.",
        "",
    ]
    seen = set()
    for emoji in ranking:
        key = usage_key(emoji)
        if key in seen or key not in forms:
            continue
        seen.add(key)
        lines.append(" ".join(forms[key]))
    path.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))
    return len(seen)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("emoji_test")
    parser.add_argument("cldr", help="directory holding annotations/ and annotationsDerived/")
    parser.add_argument("cldr_version")
    parser.add_argument("out")
    parser.add_argument("languages", nargs="+")
    parser.add_argument("--usage-counts", help="unicodetools' emoji-freq.txt; writes emoji_usage_rank.txt beside out")
    args = parser.parse_args()

    emoji_version, categories = parse_emoji_test(args.emoji_test)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    if args.usage_counts:
        ranked = write_usage_rank(
            out.parent / "emoji_usage_rank.txt", categories, parse_usage_counts(args.usage_counts), emoji_version,
            "unicodetools emoji-freq.txt",
        )
        print(f"{ranked} usage ranks")
    write_file(out / "root.txt", categories, {}, emoji_version, args.cldr_version)
    cldr = Path(args.cldr)
    for language in args.languages:
        chain = [language]
        while chain[-1] in PARENT_LOCALES:
            chain.append(PARENT_LOCALES[chain[-1]])
        annotations = {}
        for locale in reversed(chain):
            for kind in ("annotations", "annotationsDerived"):
                for emoji, (name, words) in parse_annotations(cldr / kind / f"{locale}.xml").items():
                    old_name, old_words = annotations.get(emoji, ("", []))
                    annotations[emoji] = (name or old_name, words or old_words)
        if not annotations:
            sys.exit(f"CLDR has no annotations for {language}")
        write_file(out / f"{language}.txt", categories, annotations, emoji_version, args.cldr_version)
    count = sum(len(emoji_set) for emoji_sets in categories.values() for emoji_set in emoji_sets)
    print(f"{count} emoji, Emoji {emoji_version}, CLDR {args.cldr_version}, {len(args.languages)} languages plus root")


if __name__ == "__main__":
    main()
