# SwiftFloris v1.8.22 — 2026-05-15

Twenty-second autonomous slice. **886 unit tests** at HEAD, 0 failures.

## L2.1f — TranslationRouter (end-to-end composition)

New `ime/translate/TranslationRouter` is the sibling of
`SmartComposeRouter` (v1.8.21) for the inline-translation surface.
Layers every v1.8.x translation building block in the right order:

  1. `SensitiveFieldGuard` — short-circuit on password / PIN /
     no-learn fields.
  2. `LanguageDetector` — auto-detect source locale when caller
     supplies `sourceLocale = null`. Maps detected script to
     ISO 639-1 best-guess (Latin → en, Cyrillic → ru, Hebrew → he,
     Arabic → ar, Devanagari → hi, Bengali → bn, CJK → zh,
     Thai → th, etc.). Requires confidence ≥ 0.5 to commit.
  3. `TranslationLanguagePackManager` — picks the installed
     `LanguagePairDescriptor` for resolved source + target.
  4. `SentenceTokenizer` — splits paragraph-length input into
     sentences (Bergamot prefers per-sentence inference).
  5. `TranslationCache` — internal LRU per sentence.
  6. Underlying `InlineTranslator` — usually
     `InlineTranslatorRegistry.active`.
  7. Stitch per-sentence translations preserving inter-sentence
     whitespace.

API:

- **`Request(sourceText, sourceLocale?, targetLocale?, inputType, imeOptions)`**
  — input. Optional locale fields allow auto-detection + preferred
  default fall-back.
- **`Response.Translated(translatedText, resolvedSourceLocale, resolvedTargetLocale, pair)`**
  / **`Response.Suppressed(reason)`** — structured output with
  categorised failure reasons (`"sensitive field"`, `"blank input"`,
  `"source-locale detection failed"`, `"no target locale resolved"`,
  `"source == target"`, `"no installed pair for X→Y"`,
  `"translator returned Unavailable"`).
- **`PackManagerView.from()`** — production factory backed by the
  `TranslationLanguagePackManager` singleton; tests inject fake
  views directly.
- **`bypassCache = true`** — skips the LRU for benchmarks.
- **`clearCache()`** — flushes on language-pack swap.

10 unit tests cover password-field suppression, blank-input
suppression, explicit src+tgt happy path, Latin auto-detection to
`en`, source-equals-target rejection, missing-target rejection,
paragraph dispatch + stitching, cache de-duplication, no-installed-
pair rejection, and Unavailable-translator → Suppressed.

## L5.x — Three more historical Brahmic scripts

Total transliteration coverage from 54 to **57 scripts**:

- **Modi** (U+11600 block, supplementary plane) — historically used
  for Marathi in western India c. 13th-20th century. Replaced by
  Devanagari in modern Marathi but undergoing cultural revival.
  Native digits U+11650..U+11659.
- **Sharada** (U+11180 block, supplementary plane) — historically
  used for Sanskrit + Kashmiri in northern India c. 8th-20th
  century. Replaced by Devanagari + Perso-Arabic for modern
  Kashmiri but retained liturgically. Native digits
  U+111D0..U+111D9.
- **Takri** (U+11680 block, supplementary plane) — historically
  used for Dogri / Chambeali / Kishtwari / Bilaspuri in the Punjab
  + Himachal Pradesh + Jammu hills c. 16th-20th century. Replaced
  by Devanagari + Perso-Arabic in modern usage but undergoing
  limited revival. Native digits U+116C0..U+116C9.

4 unit tests cover the three new tables (first-consonant glyph,
native-digit round-trips, sane size assertions).

## Tests

886 unit tests at HEAD (was 872 at v1.8.21), 0 failures, 0 skipped.
14 net new tests across 2 new test classes (TranslationRouterTest +
IndicScriptExtendedTest extensions).
