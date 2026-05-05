# Standard Word Dictionaries

`en.txt` is the plain one-word-per-line English dictionary used by providers that need a direct word set instead of
frequency data. It is generated from `../ime/dict/data.json` by preserving ASCII English tokens in frequency order.

The default Latin NLP provider reads `../ime/dict/data.json` directly so it can use word frequencies for completions,
corrections, and autocorrect ranking.
