# SwiftFloris v1.5.5

SwiftKey-parity contraction autocorrect — 130+ contractions across two safety tiers, with case preservation.

## What changed

The previous five-entry first-person-pronoun table is now a comprehensive English contractions table backed by a two-tier safety model:

- **TIER 1 — SAFE (immediate auto-commit on space).** Substitutions where the typed-without-apostrophe form is *not* a real English word; safe to commit without consulting the dictionary.
- **TIER 2 — DICTIONARY_GATED (auto-commit only when the typed word is not in the dictionary).** Substitutions that collide with valid English words ("ill", "well", "hell", "shell", "wed", "shed", "lets", "wont", "cant", "its", "id", "im", "ive"). The dictionary check ensures the user's intended word is not silently overwritten.

Excluded entirely: `were` → `we're`. Past-tense `were` is far too common; even SwiftKey gets complaints when it auto-corrects this.

## SAFE-tier contractions added

- **Negative -n't** — `dont`, `isnt`, `wasnt`, `werent`, `arent`, `didnt`, `doesnt`, `havent`, `hasnt`, `hadnt`, `wouldnt`, `shouldnt`, `couldnt`, `mustnt`, `neednt`, `mightnt`, `oughtnt`, `shant`, `aint`
- **Modal + 've** — `wouldve`, `shouldve`, `couldve`, `mightve`, `mustve`
- **Pronoun + auxiliary** — `youre`, `youve`, `youll`, `youd`, `theyre`, `theyve`, `theyll`, `theyd`, `weve`, `itll`, `itd`
- **Wh- + 's/'re/'ll/'d/'ve** — `whats`, `whatre`, `whatll`, `whatd`, `whatve`, `whos`, `whod`, `wholl`, `whove`, `wheres`, `whered`, `wherell`, `whens`, `whyd`, `whys`, `hows`, `howd`, `howll`
- **Demonstratives** — `theres`, `thered`, `therell`, `thereve`, `thats`, `thatll`, `thatd`, `thatre`, `heres`
- **Indefinite-pronoun + 's** — `someones`, `everyones`, `anyones`, `nobodys`, `everybodys`, `anybodys`, `somebodys`, `somethings`, `nothings`
- **Misc** — `oclock`, `yall`, `maam`, `ima`
- **First-person standalone** — `i` → `I` (unchanged from v1.5.4)

## DICTIONARY_GATED contractions added/refined

These substitute *only* when the dictionary confirms the typed word is not itself a real word the user might have meant:

- `im`/`id`/`ill`/`ive` (collide with IM/id/ill/ive)
- `well`/`hell`/`shell`/`hes`/`shes`/`hed`/`shed`/`wed`/`lets`
- `wont`/`cant`/`its`

## Behavior details (SwiftKey-aligned)

- **ALL-CAPS skip** — Tokens in all caps are never re-cased ("DONT" stays "DONT", "ID" stays "ID"). Matches SwiftKey's acronym-preservation policy.
- **Sentence-start case preservation** — Lowercase typed → lowercase contraction; capitalized typed → capitalized contraction (`Dont` → `Don't`, `Youre` → `You're`). First-person "I" forms keep the capital I regardless of typed case (`im` → `I'm`, `Im` → `I'm`).
- **Already-correct skip** — `don't` typed exactly as-is is left alone.
- **Straight apostrophe** — Uses U+0027 (`'`), matching SwiftKey's default.
- **Non-English locales** — Substitution skipped entirely.

## Files changed

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/ImmediateAutocorrect.kt` (rewrite — generalized from first-person-pronoun-only to all English contractions; added Tier enum, table builder, case-aware output)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt` (renamed call site)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt` (renamed `englishPronounCorrection` → `englishContractionCorrection`; tier-based dictionary gate)
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/ImmediateAutocorrectTest.kt` (rewrite — encodes the new contract per tier)
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinDictionarySuggesterTest.kt` (added contraction test dictionary entries + safe-tier substitution test)
- `gradle.properties`, `README.md` (version bump)
