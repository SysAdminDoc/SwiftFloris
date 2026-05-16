# SwiftFloris v1.8.18 — 2026-05-15

Eighteenth autonomous slice. **826 unit tests** at HEAD, 0 failures.

## N7 — OptInAddonDispatcher (privacy-load-bearing chokepoint)

New `ime/smartcompose/OptInAddonDispatcher` is the single facade
the IME's typing pipeline calls into to invoke any of the three
opt-in addon surfaces. Every entry point runs through
`SensitiveFieldGuard.isSensitive(inputType, imeOptions)` first and
short-circuits to a safe "no result" answer when the field is
sensitive:

- **`predictNextTokens(context, inputType, imeOptions, maxCandidates)`**
  — returns `SmartComposeResult.NoSuggestion` on sensitive fields.
- **`translate(sourceText, sourceLocale, targetLocale, inputType, imeOptions)`**
  — returns `TranslationResult.Unavailable` on sensitive fields.
- **`callMcpTool(daemonKey, toolName, parameterJson, inputType, imeOptions, timeoutMillis)`**
  — returns `McpToolCallResponse` with `errorCode = PERMISSION_DENIED`
  on sensitive fields.

The dispatcher takes the three providers as constructor arguments,
so production code plugs in `SmartComposeProviderRegistry.active`
+ `InlineTranslatorRegistry.active` + `McpClientRegistry.active`,
while tests drive synthetic providers without touching the
registries.

This is the load-bearing privacy seam: smart-compose / translation /
MCP **never** fire from a password / PIN / no-learn field regardless
of what the underlying provider would have returned.

6 unit tests cover smart-compose suppression on password fields,
smart-compose forwarding on plain TEXT, translation suppression +
forwarding, MCP suppression with PERMISSION_DENIED, and
`IME_FLAG_NO_PERSONALIZED_LEARNING` suppressing all three.

## L2.1d — Sentence boundary tokenizer

New `ime/translate/SentenceTokenizer` is the paragraph-splitter for
the Bergamot per-sentence inference path (Bergamot models produce
noticeably worse output on multi-sentence input than on per-
sentence dispatch):

- **`split(text)`** — splits paragraph into sentences, preserving
  trailing terminator + inter-sentence whitespace so the call site
  can concat translated chunks without re-deriving spacing.
- **`hasMultipleSentences(text)`** — cheap predicate; the
  translation surface only routes through the tokenizer when the
  paragraph actually contains more than one sentence.
- **Multi-script terminator support** — `.` / `!` / `?` plus
  Arabic `۔` / `؟`, Devanagari `।` / `॥`, CJK `。` / `！` / `？`,
  Ethiopic `።`.
- **Consecutive-terminator coalescing** — "Wait!? Really." becomes
  two sentences, not three.

10 unit tests cover empty input, no-terminator passthrough, English
multi-sentence, round-trip stitching, consecutive-terminator
collapse, CJK / Devanagari / Arabic terminators, and the
`hasMultipleSentences` predicate both ways.

## L5.x — Three more Brahmic scripts: Soyombo + Marchen + Chakma

Total transliteration coverage from 42 to **45 scripts**:

- **Soyombo** (U+11A50 block, supplementary plane) — 17th-century
  alphabetic script created by the Mongolian lama Zanabazar for
  writing Sanskrit, Tibetan, and Mongolian. The symbol on the
  modern Mongolian flag derives from this script.
- **Marchen** (U+11C70 block, supplementary plane) — historical
  script of the Bon religion (Tibet), used between the 17th and
  20th centuries for liturgical texts. Brahmic-derived.
- **Chakma** (U+11100 block, supplementary plane) — Brahmic-derived
  script of the Chakma language (Chittagong Hill Tracts, Bangladesh
  + Tripura, India). Recently revived in education + literature.

4 unit tests cover the three new tables (first-letter glyph, Marchen
`ts` digraph, Chakma `sh` digraph, sane size assertions).

## Tests

826 unit tests at HEAD (was 806 at v1.8.17), 0 failures, 0 skipped.
20 net new tests across 3 new test classes (OptInAddonDispatcherTest +
SentenceTokenizerTest + IndicScriptExtendedTest extensions).
