# SwiftFloris v1.8.20 — 2026-05-15

Twentieth autonomous slice. **853 unit tests** at HEAD, 0 failures.

## L2.1e — Translation language-pack manager

New `ime/translate/TranslationLanguagePackManager` is the
bookkeeping surface the Settings → Translate screen + the Translate
quick-action consume to render the language-list UI:

- **`installedPairs()`** — currently-bound Bergamot language pairs
  (the addon registers them via `setInstalled`).
- **`availablePairs()`** — pairs the addon manifest advertises as
  downloadable.
- **`downloadablePairs()`** — `available − installed` set
  subtraction view; what the "download more languages" UI shows.
- **`preferredTargetLocale()` / `setPreferredTargetLocale(locale)`**
  — the user's default target locale; nullable. Setter enforces
  lowercase ISO 639-1.
- **`defaultPairFor(sourceLocale)`** — picks the installed pair
  whose target matches the user's preferred locale when available,
  falls back to the first installed pair with that source locale,
  else null. Drives the Translate quick-action's pre-fill.
- **Atomic snapshots** via `AtomicReference` — concurrent reads
  from the IME thread never see a half-replaced state.
- **De-dupes by `pairKey`** — two descriptors with the same
  `src-tgt` pair (e.g. tiny + base quality tiers) collapse to one
  entry per direction.

7 unit tests cover the empty-state contract, de-dup,
`downloadablePairs` set subtraction, preferred-target-honouring
pair selection, fall-back-to-first-installed match, no-match-
returns-null, and the lowercase-ISO-639-1 setter invariant.

## L5.x — Three more 20th-21st century constructed alphabets

Total transliteration coverage from 48 to **51 scripts**:

- **Wancho** (U+1E2C0 block, supplementary plane) — 20th-century
  alphabet for the Wancho Naga language of Arunachal Pradesh +
  Myanmar. Created by Banwang Losu c. 2001. Encoded in Unicode 12
  (March 2019).
- **Nyiakeng Puachue Hmong** (U+1E100 block, supplementary plane)
  — sister of Pahawh Hmong (shipped v1.8.16); a separate
  Hmong-language script created by Reverend Chervang Kong Vang in
  the 1980s. Encoded in Unicode 12.
- **Medefaidrin** (U+16E40 block, supplementary plane) — 20th-
  century constructed alphabet used by the Oberi Okaime Christian
  community in southeast Nigeria. Created c. 1930 by Michael
  Ukpong + Akpan Akpan Udofia. Encoded in Unicode 11.

4 unit tests cover the three new tables (first-letter glyph,
Nyiakeng Puachue Hmong `ch` digraph vs `c` greedy match, sane
size assertions).

## Tests

853 unit tests at HEAD (was 842 at v1.8.19), 0 failures, 0 skipped.
11 net new tests across 2 new test classes
(TranslationLanguagePackManagerTest + IndicScriptExtendedTest
extensions).
