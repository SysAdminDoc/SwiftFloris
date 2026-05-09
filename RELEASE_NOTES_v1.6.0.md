# SwiftFloris v1.6.0

Three SwiftKey-parity moves: personal-dictionary auto-learning, a 2.3× larger English dictionary, and the first slice of SwiftKey's visual design tokens.

## 1. Personal dictionary auto-learning

The keyboard now learns the words you actually type and bumps them in suggestions / autocorrect over time. Matches SwiftKey's "personal language" behavior.

**How it works**
- Every word you commit (via space, punctuation, gesture, or accepted suggestion) is fed to `DictionaryManager.learnWord(...)`.
- New words are inserted into the FlorisUserDictionary at frequency **80**; existing entries are reinforced by **+6** per use, capped at **250** so curated top-tier corpus words at 255 still rank first.
- The personal dictionary is already merged ahead of the main dictionary in suggestion ranking via `SuggestionCandidateMerger.mergePreferred(...)`, so learned words rise to the top of the suggestion strip after 2–3 uses.
- All inserts/updates run on `Dispatchers.IO` — no input-event lag.

**Privacy gates**
- Skipped entirely when **incognito mode** is active.
- Skipped when **personal dictionary** is disabled in settings.
- Skipped for tokens that don't look like real words: less than 3 chars, more than 32 chars, contain digits, or contain punctuation other than `'` or `-`.

**Manageable**
- Learned words appear in Settings → Dictionary → User dictionary alongside any words you've added manually. Unwanted entries can be deleted there.

## 2. English dictionary expansion: 49,981 → 117,022 words

The bundled English dictionary (`assets/ime/dict/data.json`) now ships **2.34× more words** for spell-check coverage, while keeping the existing high-frequency ranking so autocorrect still prefers common words.

**Composition**
- **49,981 curated high-frequency entries** kept at their original 128–255 frequency band. These are the words autocorrect actively prefers.
- **67,041 new long-tail entries** from SCOWL v2020.12.07 (`english-words.{10,20,35,40,50,60}` + `american-words.{10,20,35,40,50,60}` + selected proper-name lists) added at frequency band 80–127. These exist for spell-check membership — legitimate uncommon words no longer get red-squiggled or silently auto-corrected, but they don't outrank the curated corpus.
- Profanity filtered using LDNOOBW's English bad-words list (CC-BY 4.0).

**Sizes**
- `data.json`: 807KB → 1.78MB (still loaded once on subtype init, cached for the session)
- `en.txt`: regenerated to match, frequency-sorted

**Licenses**
- SCOWL — BSD-style permissive, see `LICENSES/SCOWL-Copyright.txt`.
- LDNOOBW — CC-BY 4.0, attribution in `NOTICE`.
- Dictionary regeneration script: `utils/expand_dictionary.py` (re-runnable when SCOWL releases new data).

## 3. SwiftKey visual design — first slice

Per a research pass on Microsoft SwiftKey's 2026 visual spec (Pure Light/Dark themes, Microsoft-aligned accent palette):

- **Accent color flipped to SwiftKey's 2020+ blue `#319DFF`** (was `#4A90E2`). The pre-2020 SwiftKey teal `#2596BE` is preserved as `accent_teal_legacy` for users who want the nostalgic look.
- **SwiftKey "Pure" theme palette added as design tokens** — `swiftkey_pure_light_*` and `swiftkey_pure_dark_*` families in `colors_branding.xml` (kbd bg, key bg, special key bg, key text, hint glyph). Ready for a future "SwiftKey Pure" theme preset to consume them; not yet wired into the default theme.
- **Key dimensions bumped toward SwiftKey's premium feel** — `key_width` 33→36dp, `key_height` 42→56dp.
- **Suggestion chip radius dropped 32dp → 6dp** — SwiftKey's strip is unchipped (text on dividers); the 32dp pill was a Material You convention.

Out of scope for this release (will land in a follow-up): full SwiftKey theme preset wired into the theme picker, sans-serif-medium font on keys, long-press popup color tweak, SwiftKey-default haptic 20ms@153 amplitude.

## Files changed

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt` — added `learnWord(...)` + IO scope + tier constants
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt` — added `learnIfAllowed(...)` and wired it into `commitCandidate`, `commitGesture`, and `handleSpace`
- `app/src/main/assets/ime/dict/data.json` — regenerated, 49,981 → 117,022 entries
- `app/src/main/assets/dictionaries/en.txt` — regenerated to match
- `app/src/main/assets/dictionaries/README.md` — provenance + regeneration recipe
- `app/src/main/res/values/dimens.xml` — key dims + chip radius
- `app/src/main/res/values/colors_branding.xml` — SwiftKey palette tokens
- `LICENSES/SCOWL-Copyright.txt` — SCOWL license bundled
- `NOTICE` — SCOWL + LDNOOBW attribution
- `utils/expand_dictionary.py` — re-runnable dictionary expansion
- `gradle.properties`, `README.md` — version bump
