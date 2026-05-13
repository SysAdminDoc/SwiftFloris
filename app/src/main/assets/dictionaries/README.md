# Standard Word Dictionaries

`en.txt` is the plain one-word-per-line English dictionary used by providers that need a direct word set instead of
frequency data. It is generated from the merged English runtime dictionary: `../ime/dict/data.json` plus the
low-priority `../ime/dict/en_supplemental.json` expansion.

The default Latin NLP provider reads `../ime/dict/data.json` and merges `../ime/dict/en_supplemental.json` for
English, preserving word frequencies for completions, corrections, and autocorrect ranking.

## Provenance

The English dictionary is a merge of three sources:

1. **Curated high-frequency corpus (~50k words, freq band 128–255).** The original frequency-ranked subset shipped
   with SwiftFloris, derived from the FlorisBoard project's bundled dictionary. Real-world frequency data informs the
   ranking — common words like `the`, `of`, `and` sit at 254–255.

2. **SCOWL long-tail expansion (~67k additional words, freq band 80–127).** Sourced from Kevin Atkinson's
   [Spell-Checker Oriented Word Lists v2020.12.07](http://wordlist.aspell.net/), specifically the
   `english-words.{10,20,35,40,50,60}` + `american-words.{10,20,35,40,50,60}` + selected proper-name lists.
   These words are included for spell-check membership (so legitimate uncommon words don't get red-squiggled or
   silently auto-corrected), but ranked below the curated corpus so autocorrect still prefers high-frequency forms.

3. **Low-priority supplemental expansion (~183k additional words, freq band 48-96).** Stored in
   `../ime/dict/en_supplemental.json` and merged at runtime for English. Most entries come from SCOWL's larger
   `70` and `80` word-list tiers. A small maintained `utils/english_modern_terms.txt` list adds current technical,
   healthcare, Android, and AI vocabulary not covered by SCOWL 2020.

Total merged plain English list: ~299k words.

Profanity is filtered using the
[LDNOOBW English bad-words list](https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words)
(CC-BY 4.0).

## Licenses

- **SCOWL** — see `../../../../LICENSES/SCOWL-Copyright.txt`. BSD-style permissive notice; Apache-2.0 compatible.
- **LDNOOBW** — CC-BY 4.0; attribution preserved in the project NOTICE file (Apache-2.0 § 4(d)).

## Regenerating the dictionary

```sh
# 1. Download SCOWL
cd /tmp && curl -L -o scowl.tar.gz \
    https://qa.debian.org/watch/sf.php/wordlist/scowl-2020.12.07.tar.gz
tar xzf scowl.tar.gz

# 2. Download profanity blocklist
curl -sL -o /tmp/profanity_en.txt \
    https://raw.githubusercontent.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/master/en

# 3. Generate low-priority supplement and merged plain word list
cd <repo_root>
python3 utils/build_english_supplemental_dictionary.py \
    --base app/src/main/assets/ime/dict/data.json \
    --scowl-final /tmp/scowl-2020.12.07/final \
    --modern-terms utils/english_modern_terms.txt \
    --profanity /tmp/profanity_en.txt \
    --output-json app/src/main/assets/ime/dict/en_supplemental.json \
    --output-word-list app/src/main/assets/dictionaries/en.txt
```

The older one-file merge flow is still available if `data.json` itself needs to be regenerated:

```sh
cd scowl-2020.12.07/final
cat english-words.{10,20,35,40,50,60} american-words.{10,20,35,40,50,60} \
    english-proper-names.{50,60} american-proper-names.{50} 2>/dev/null \
  | iconv -f ISO-8859-1 -t UTF-8 | tr -d '\r' > /tmp/scowl_combined.txt

# 3. Filter profanity (LDNOOBW)
curl -sL -o /tmp/profanity_en.txt \
    https://raw.githubusercontent.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/master/en
grep -vxFf /tmp/profanity_en.txt /tmp/scowl_combined.txt > /tmp/scowl_clean.txt

# 4. Merge into existing data.json (preserves curated frequencies, adds long tail)
cd <repo_root>
python3 utils/expand_dictionary.py \
    --existing app/src/main/assets/ime/dict/data.json \
    --scowl /tmp/scowl_clean.txt \
    --output app/src/main/assets/ime/dict/data.json
```
