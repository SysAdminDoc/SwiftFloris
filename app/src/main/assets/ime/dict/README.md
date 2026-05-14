# Latin Gesture Dictionaries

SwiftFloris uses this directory for bundled Latin word-frequency dictionaries consumed by gesture typing and basic
suggestions.

## Asset formats

- `data.json` is the core English frequency map used for completions, corrections, and autocorrect ranking.
- `en_supplemental.json` is a low-priority English expansion merged after `data.json`. It adds large SCOWL
  `70`/`80`/`95` long-tail coverage and current technical/healthcare/Android/AI vocabulary while keeping frequencies below
  autocorrect auto-commit thresholds.
- The Latin provider loads the full merged English map for recognition, but builds the memory-heavy correction index only
  from high-confidence words at frequency `96+` so active typing remains stable on Android's IME heap.
- `{language}.fldic` files are FlorisBoard NLP v0~draft1 dictionaries. SwiftFloris reads only their `[words]` section
  and normalizes the absolute word scores into the existing 0..255 frequency range.

## Imported assets

The following dictionaries were imported from the FlorisBoard NLP repository:

- `de.fldic`: `data/dicts/v0~draft1/words_de.fldic`
- `es.fldic`: `data/dicts/v0~draft1/words_es.fldic`
- `fr.fldic`: `data/dicts/v0~draft1/words_fr.fldic`
- `it.fldic`: `data/dicts/v0~draft1/words_it.fldic`

Source: https://github.com/florisboard/nlp/tree/main/data/dicts/v0~draft1

Upstream metadata describes these dictionaries as Apache-2.0 FlorisBoard dictionary extensions preprocessed from
Wiktextract and Google Ngram data.

`pt.fldic` was generated from `diplomaticvegetation/portuguese` `words-top.txt` on Hugging Face, which is published
under CC0-1.0. The source file is a frequency-ranked Portuguese word list with counts and source URLs; SwiftFloris
imports only normalized words and counts.

Source: https://huggingface.co/datasets/diplomaticvegetation/portuguese/blob/main/words-top.txt
