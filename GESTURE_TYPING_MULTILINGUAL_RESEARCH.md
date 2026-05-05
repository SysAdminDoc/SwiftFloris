# Multi-Language Gesture Typing Research

Date: 2026-05-05

## Scope

Roadmap item: Phase 1 research feasibility for German, French, Spanish, Italian, and Portuguese gesture typing.

Questions reviewed:

- Does the gesture classifier work language-agnostically?
- What dictionary structure do spell-checkers and suggestions expect?
- Can existing NLP dictionaries be reused for swipe predictions?
- What should the next implementation batch change first?

## Architecture Findings

### Glide manager and classifier

`GlideTypingManager` already passes `subtypeManager.activeSubtype` into `GlideTypingClassifier.setLayout(keys, subtype)` whenever the text keyboard layout changes.

`StatisticalGlideTypingClassifier` stores three subtype markers:

- `layoutSubtype`
- `wordDataSubtype`
- `currentSubtype`

The classifier only reports `ready` when layout and word data are initialized for the same subtype. This is a good foundation for per-language gesture typing because the active subtype is already part of the classifier lifecycle.

### Gesture scoring

The statistical classifier is mostly language-agnostic:

- It builds ideal word gestures from the current keyboard geometry.
- It uses shape distance and location distance rather than language-specific ML.
- It normalizes accented letters to base characters when matching dictionary words to key centers.
- It ranks candidates with `nlpManager.getFrequencyForWord(currentSubtype, word)`.

This means German, French, Spanish, Italian, and Portuguese are feasible for Latin layouts as long as their dictionaries use letters that can map to the active layout. Diacritics can work because the classifier already falls back to base letters for gesture path generation.

### Dictionary path

The classifier gets words and frequencies through:

```kotlin
nlpManager.getListOfWords(subtype)
nlpManager.getFrequencyForWord(subtype, word)
```

Those calls delegate to the active subtype's suggestion provider.

The default `SubtypeNlpProviderMap` uses `LatinLanguageProvider.ProviderId` for suggestions. `LatinLanguageProvider` currently loads `assets/ime/dict/data.json`, which is an English frequency map, regardless of the active subtype locale.

`AdvancedPredictionProvider` has a per-language asset shape:

- `assets/dictionaries/{language}.txt`
- `assets/lm/{language}_unigrams.txt`
- `assets/lm/{language}_bigrams.txt`

However, the current checked-in assets only provide English placeholders for that advanced path, and the default subtype provider does not use `AdvancedPredictionProvider`.

## Feasibility Decision

Multi-language gesture typing is feasible, but the next code change should be dictionary infrastructure rather than classifier math.

The classifier already has the right subtype hook. The missing piece is locale-specific word and frequency data returned by the suggestion provider used by glide typing.

## Recommended Implementation Path

1. Extend the Latin dictionary asset model from one global English JSON to locale-keyed frequency dictionaries.
2. Keep `LatinLanguageProvider` as the canonical source for glide word lists because it is already the default suggestion provider.
3. Load language-specific frequency maps by `subtype.primaryLocale.language`, falling back to English when a locale dictionary is missing.
4. Add German, French, Spanish, Italian, and Portuguese frequency assets using the same `word -> 0..255` shape as `ime/dict/data.json`.
5. Increase the glide pruner cache from 5 to at least 6 entries so English plus the five target languages do not evict each other during normal subtype switching.
6. Add tests around dictionary selection and fallback before adding settings UI.

## Risks

- Dictionary licensing must be verified before adding new word-frequency data.
- APK size can grow quickly if full language dictionaries are bundled.
- The current classifier handles Latin diacritics by base-letter fallback, but language-specific letters not present on the active layout may reduce accuracy.
- A single active subtype is supported today; simultaneous multi-language swipe across secondary locales needs a separate merge strategy and should not be assumed.

## Next Batch

Implement dictionary augmentation infrastructure:

- Add a locale-aware dictionary loader for `LatinLanguageProvider`.
- Preserve the existing English dictionary as the fallback.
- Add tests proving `getListOfWords()` and `getFrequencyForWord()` use the active subtype language.
- Add a small fixture-backed path first if production dictionaries are not ready, then add full licensed dictionaries as a separate asset batch.
