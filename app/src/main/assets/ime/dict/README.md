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
- `en_misspellings.tsv` lists about 780 common English misspellings and their fixes, one `misspelling<TAB>fix` per
  line. Where British spelling differs, a third column holds it (`jewelery<TAB>jewelry<TAB>jewellery`). Keyboards for
  Britain, Australia and the rest of the Commonwealth use that one. Canada does too, except for -ize words and
  fulfill, which it spells the US way. The English provider fixes them on space, and the dictionary scripts keep them out of `data.json`,
  `en_supplemental.json` and `../../dictionaries/en.txt` (see `../../dictionaries/README.md`).
- `en_unvetted.txt` lists about 28,000 English dictionary words that no vetted word list backs, each one edit from a
  common word. Most came into `data.json` from web text ("thos", "snd", "yoy"). Space corrects a rare one to a far
  more common word one edit away, so a slip that spells one gets fixed, while rare real words ("snide", "plop") stay.
  A word counts as vetted when SCOWL up to size 70 lists it in any spelling, or when `utils/english_modern_terms.txt`
  or `utils/english_chat_terms.txt` has it. `utils/build_english_unvetted_words.py` regenerates it from `data.json`,
  `en_supplemental.json` and SCOWL's `final/` directory; rerun it after either dictionary changes.
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

## English context model

`../../lm/en_context.sflm` tells English autocorrect how well a word fits the one or two words before it. It holds
pruned counts of word pairs and triples among the 50,000 most common words, read with stupid backoff. The keyboard
memory-maps it straight out of the APK, which stores `.sflm` files uncompressed for that, so it takes no heap.
`utils/build_english_context_model.py` builds it and documents the file layout.

The counts come from the first shard of AllenAI's C4 web text, `en/c4-train.00000-of-01024.json.gz` (356,317 pages,
about 127 million words). C4 is released under ODC-BY 1.0 and is also subject to the Common Crawl terms of use. The
model keeps only how often short word sequences occur, not the pages or their sentences.

Source: https://huggingface.co/datasets/allenai/c4
