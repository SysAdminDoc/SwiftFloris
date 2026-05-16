# SwiftFloris v1.8.16 — 2026-05-15

Sixteenth autonomous slice. **793 unit tests** at HEAD, 0 failures.

## L2.1b — Translation LRU cache

New `ime/translate/TranslationCache` wraps any `InlineTranslator`
with an access-order LRU cache. Matches the
`KenLmScoreCache` design pattern:

- **Triple-keyed lookup** — `(sourceText, sourceLocale, targetLocale)`
  via unit-separator joins so different target locales for the same
  source text don't collide (e.g. "no" Catalan→English vs
  Spanish→English).
- **`Unavailable` never cached** — when the underlying translator
  returns `TranslationResult.Unavailable` the result is *not*
  stored, so binding an addon mid-session flips the result live
  without needing `clear()`.
- **Default capacity 2,048** — sized for a conversational session
  worth of repeat translations.
- **`hits` + `misses` counters** + **`clear()`** + **`size()`**
  diagnostics.
- Pass-through `isLanguagePairReady` + `installedPairs` so the cache
  is a drop-in replacement for the wrapped translator.

6 unit tests cover repeat-hit, distinct-locale-pair miss,
`Unavailable`-not-cached behaviour, `clear()` reset, eviction at
capacity, and capacity-≥-1 invariant.

## L2.1c — Script-based language detector

New `ime/translate/LanguageDetector` is the pre-bind language
detection helper for the Translate quick-action surface:

- **`detect(text)` → `Detection(script, confidence)`** — picks the
  majority script and returns the fraction-of-letters confidence.
- **`DetectedScript`** enum: LATIN / CYRILLIC / GREEK / HEBREW /
  ARABIC / DEVANAGARI / BENGALI / CJK (Han + Hiragana + Hangul) /
  THAI / UNKNOWN.
- **Whitespace + digits + punctuation excluded** from the
  denominator — "Привет 12345" still classifies as Cyrillic with
  full confidence.
- **Mixed-script text** picks the majority by letter count; the
  confidence drops into `(0, 1)` so callers can gate the
  pre-fill on a confidence threshold.

9 unit tests cover the eight named scripts (Latin / Cyrillic /
Hebrew / Arabic / Devanagari / CJK across three sub-ranges / Thai),
mixed-script majority detection, and the empty / pure-digit
returns-UNKNOWN edge case.

## L5.x — Three more scripts: Bassa Vah + Mende Kikakui + Pahawh Hmong

Total transliteration coverage from 36 to **39 scripts**:

- **Bassa Vah** (U+16AD0 block, supplementary plane) — 20th-century
  alphabet for the Bassa language of Liberia. Created by Thomas Flo
  Lewis c. 1900. 35 letters.
- **Mende Kikakui** (U+1E800 block, supplementary plane, RTL) —
  20th-century syllabary for the Mende language of Sierra Leone +
  Liberia. 195 syllables; this table ships representative CV
  combinations.
- **Pahawh Hmong** (U+16B00 block, supplementary plane) —
  20th-century writing system for the Hmong language created by
  Shong Lue Yang c. 1959. Uses both consonants + vowels (uniquely
  among the modern indigenous scripts that share this Unicode
  region).

4 unit tests cover the three new tables (first-character glyph for
each, Mende Kikakui `ka` CV-syllable greedy match, Pahawh Hmong
`ph` digraph vs `p` greedy match, sane size assertions).

## Tests

793 unit tests at HEAD (was 774 at v1.8.15), 0 failures, 0 skipped.
19 net new tests across 3 new test classes (TranslationCacheTest +
LanguageDetectorTest + IndicScriptExtendedTest extensions).
