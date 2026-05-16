# SwiftFloris v1.8.25 — 2026-05-16

Typed-word memory finally wired into the suggester. **925 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Words you type **now climb the suggestion ranking** as you reuse them.
The keyboard already wrote those words to the on-disk user dictionary
on every committed word, but the suggester wasn't reading them — so
"foobar" you typed twenty times would still lose to whatever SCOWL
had at that prefix. After this release:

- **Type a word once** → it's known to the suggester. The spell-check
  underline disappears the next time you type it.
- **Type it again** → it climbs above lower-frequency SCOWL words
  sharing the same prefix.
- **Type it thirty times** → it ranks at the top of its prefix slot,
  matching SCOWL's most-common-words tier.
- **Long-press → "Forget"** still works — clears the in-memory bump
  and the disk entry in the same call.
- **Password / PIN / no-learn fields** are still skipped (the existing
  N7 `SensitiveFieldGuard` chain is unchanged).
- **Incognito mode** is still skipped (existing `learnIfAllowed` gate).

## What changed (internal)

### Next-3 — `UserDictionaryOverlay`

New `ime/dictionary/UserDictionaryOverlay`:

- Process-wide singleton. Per-locale `ConcurrentHashMap<word, freq>`.
- **Frequency scale matches SCOWL exactly**: initial 80, +6 per
  re-use, capped at 250 (same as `DictionaryManager.LEARN_*`).
- **Lock-free reads** via `ConcurrentHashMap`. Writes use an
  optimistic increment loop so concurrent commits converge on the
  cap without locks.
- **Per-locale isolation** — `kabob` typed in en-US vs es-ES tracks
  separately.
- **Same normaliser as `learnWord`**: trims trailing junk, accepts
  internal `'` and `-` (real-word punctuation), rejects internal
  underscores / symbols / digits.
- **`learn` / `forget` / `frequencyFor` / `contains` /
  `wordsWithPrefix` / `snapshotFor` / `hydrateLocale` /
  `clearLocale` / `clearAll`** API.

### Existing `DictionaryManager.learnWord` / `forgetWord` updated

- **`learnWord`** now bumps the overlay **before** kicking off the
  IO-thread DAO write. The next keystroke's `suggest` already sees
  the new entry — no IO latency.
- **`forgetWord`** drops the overlay entry first, then deletes from
  the DAO.

### New `DictionaryManager.hydrateOverlay(locale)`

Lazy DAO snapshot loader. Idempotent — overlay tracks which locales
it's hydrated. Called from the suggester on every suggest; the
overlay's `isHydrated` flag short-circuits all calls after the first
on a given locale. So a process restart picks up the user's full
saved vocabulary without blocking the typing path.

### `LatinDictionarySuggester` consults the overlay

- **`suggest()`** gains a `userOverlay: Map<String, Int>` parameter
  (default empty for tests / multilingual paths that don't need it).
- **`completions()`** now merges SCOWL prefix-matches with overlay
  prefix-matches before ranking. Duplicates dedup; overlay-only
  words appear with their overlay frequency.
- **Ranking** uses `max(scowl_normalised, overlay_normalised)` so:
  - A heavily-typed user word (overlay → 250 / 255 ≈ 0.98) outranks
    its mid-frequency SCOWL look-alikes.
  - A SCOWL top-1000 word still wins over a once-typed user variant.
- **Overlay-known words skip the corrections path** — the suggester
  treats them as their own valid form, so the user's invented
  proper-noun isn't autocorrected to a SCOWL look-alike.

### `LatinLanguageProvider.spell` treats overlay-known words as valid

The existing dictionary `contains()` check is supplemented with an
`UserDictionaryOverlay.contains(word, locale)` check before
returning `SpellingResult.typo`. So a word the user typed before
doesn't get the spell-check red underline.

### Tests

- **`UserDictionaryOverlayTest`** — 13 tests covering learn/forget
  bump-and-cap, case-insensitivity, length + punctuation
  rejections, per-locale isolation, prefix lookup, hydrate
  idempotence, clearLocale / clearAll.
- **`LatinDictionarySuggesterTest` extensions** — 4 new tests:
  overlay surfaces an unknown-to-SCOWL word, overlay boosts a word
  above lower-frequency SCOWL completions, overlay-known word
  skips the autocorrect substitution, empty-overlay path is
  identical to overlay-less.

925 unit tests at HEAD (was 908 at v1.8.24), 0 failures, 0 skipped.
17 net new tests across 2 new test classes.
