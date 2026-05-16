# SwiftFloris v1.8.17 — 2026-05-15

Seventeenth autonomous slice. **806 unit tests** at HEAD, 0 failures.

## L1.1c — Smart-compose LRU cache

New `ime/smartcompose/SmartComposeCache` wraps any
`SmartComposeProvider` with an LRU result cache. Matches the
`KenLmScoreCache` + `TranslationCache` design but tuned for the
ghost-text replay pattern:

- **Tuple-keyed lookup** — `(locale, editorPackageName,
  maxCandidates, precedingText, composingPrefix)` so per-app LoRA
  variants (L1.3) cache separately and so a pause-then-resume
  replays cheaply during the suggestion-acceptance window.
- **`NoSuggestion` never cached** — when the provider returns
  `NoSuggestion` the result is *not* stored, so a mid-session
  addon-bound flip lights up the ghost-text overlay live.
- **Default capacity 512** — tighter than the translation cache's
  2,048 because a smart-compose key can hold a whole sentence in
  `precedingText`.
- **`hits` + `misses` counters + `clear()` + `size()`** diagnostics
  + pass-through `isReady` / `activeModel` / `supportedLocales`.

4 unit tests cover repeat-hit, `NoSuggestion`-not-cached, distinct-
locale-keys cache separately, and `clear()` reset.

## N7 — SensitiveFieldGuard privacy gate

New `ime/smartcompose/SensitiveFieldGuard` is the predicate the
NlpManager smart-compose / inline-translation / MCP dispatch paths
ask before calling any opt-in addon:

- **`isSensitive(inputType, imeOptions)`** returns true for:
  - `TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD`
  - `TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`
  - `TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_PASSWORD`
  - `TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD`
  - Any `imeOptions` with `IME_FLAG_NO_PERSONALIZED_LEARNING` set.
- **`reasonFor(inputType, imeOptions)`** returns a human-readable
  reason string (or `null`) for the dev-log line emitted when the
  IME suppresses the opt-in surface.
- **Bitwise int probe** — constants are mirrored from `InputType`
  / `EditorInfo` so the unit tests don't need Robolectric.

This complements the existing CAKI (Content-Aware Keyboard
Injection) hardening in `EditorInstance`; CAKI gates the learn-
from-text path, this gates the opt-in *prediction* path.

5 unit tests cover the four password-field shapes,
`IME_FLAG_NO_PERSONALIZED_LEARNING` override, plain-text non-
sensitive baseline, and non-password number field passthrough.

## L5.x — Three more scripts: Tifinagh + Vithkuqi + Hanunoo

Total transliteration coverage from 39 to **42 scripts**:

- **Tifinagh** (U+2D30 block) — Neo-Tifinagh consonantal alphabet
  used by the Berber / Amazigh language family across North Africa
  (Morocco, Algeria, Libya, Niger, Mali). Modern standardised form.
- **Vithkuqi** (U+10570 block, supplementary plane) — 19th-century
  Albanian alphabet created by Naum Veqilharxhi in 1844, used
  briefly before being replaced by the modern Latin Albanian
  alphabet. Encoded in Unicode 14 (Sept 2021).
- **Hanunoo** (U+1720 block) — Brahmic-derived Philippine script
  still in active use by the Mangyan people of Mindoro. Vertical
  bottom-to-top traditionally; encoded horizontally in Unicode.

4 unit tests cover the three new tables (first-letter / first-
syllable glyph, Tifinagh `gh` digraph greedy match, Hanunoo `nga`
CV-syllable greedy match, sane size assertions).

## Tests

806 unit tests at HEAD (was 793 at v1.8.16), 0 failures, 0 skipped.
13 net new tests across 2 new test classes (SmartComposeCacheAndGuardTest +
IndicScriptExtendedTest extensions).
