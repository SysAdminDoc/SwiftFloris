# Latin Gesture Dictionaries

SwiftFloris uses this directory for bundled Latin word-frequency dictionaries consumed by gesture typing and basic
suggestions.

## Asset formats

- `data.json` is the legacy English frequency map used by SwiftFloris before multilingual swipe support.
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
Wiktextract and Google Ngram data. Portuguese is not bundled here because the upstream repository does not currently
provide a `words_pt.fldic` or `org.florisboard.dictionaries.pt.flex` asset.
