# Multilingual Gesture Typing

Date: 2026-05-05

SwiftFloris gesture typing supports English, German, Spanish, French, Italian, and Portuguese through bundled on-device dictionaries. Gesture typing remains private and offline; swipe paths and dictionary lookups do not leave the device.

## Supported Languages

| Language | Dictionary asset | Default state |
| --- | --- | --- |
| English | `ime/dict/data.json` fallback | Enabled |
| German | `ime/dict/de.fldic` | Enabled |
| Spanish | `ime/dict/es.fldic` | Enabled |
| French | `ime/dict/fr.fldic` | Enabled |
| Italian | `ime/dict/it.fldic` | Enabled |
| Portuguese | `ime/dict/pt.fldic` | Enabled |

Languages outside this list do not enable glide typing by default. This avoids misleading English-only swipe candidates on unsupported layouts.

## Settings

Open `Settings > Gestures & Glide typing > Swipe languages`.

Each bundled language has its own toggle:

- Enable swipe for English
- Enable swipe for German
- Enable swipe for Spanish
- Enable swipe for French
- Enable swipe for Italian
- Enable swipe for Portuguese

The main `Enable glide typing` switch is still the master control. Per-language switches only apply when glide typing is globally enabled.

## How It Works

The keyboard passes the active subtype into the glide classifier. The classifier then asks the NLP provider for words and frequencies for that subtype language.

The classifier is language-agnostic at the gesture-shape level. It compares the swipe path against generated word paths, then uses language-specific frequency data to rank candidates. Latin diacritics are normalized to their base letters for key matching, so words such as French or Portuguese accented forms can still map to standard Latin key layouts.

## Validation Status

Completed local validation:

- Bundled dictionary loading for English, German, Spanish, French, Italian, and Portuguese.
- On-device dictionary profile coverage through `MultilingualGlideDictionaryProfileTest`.
- Existing detector latency profile coverage through `GlideTypingGestureLatencyProfileTest`.
- Unit coverage for dictionary selection, `.fldic` parsing, score normalization, and English fallback.

Not yet claimed:

- Native-speaker accuracy parity for every language.
- Accuracy within 5% of English across a broad human test panel.
- Compatibility claims beyond the owned Samsung phone and local emulator unless contributors provide additional results.

## Manual Accuracy Protocol

For each language:

1. Enable only the target language in `Swipe languages`.
2. Add or switch to a matching keyboard subtype.
3. Swipe the 20-word corpus below in a plain text editor.
4. Record exact matches, acceptable accent/case variants, wrong words, missed gestures, and latency notes.
5. Repeat once after force-stopping the app to catch cold dictionary-load behavior.

Minimum pass target for a local smoke test:

- 17 of 20 exact or acceptable matches.
- No repeated candidate from the previously active language.
- No visible pause above 500 ms after releasing a swipe on a modern device.

## Test Corpus

English:
`the`, `and`, `people`, `because`, `today`, `keyboard`, `message`, `friend`, `please`, `after`

German:
`hallo`, `und`, `nicht`, `heute`, `morgen`, `bitte`, `danke`, `freund`, `werden`, `machen`

Spanish:
`hola`, `que`, `para`, `gracias`, `amigo`, `mensaje`, `porque`, `cuando`, `hacer`, `tiempo`

French:
`bonjour`, `que`, `pour`, `merci`, `ami`, `message`, `parce`, `quand`, `faire`, `temps`

Italian:
`ciao`, `che`, `per`, `grazie`, `amico`, `messaggio`, `perche`, `quando`, `fare`, `tempo`

Portuguese:
`ola`, `que`, `para`, `obrigado`, `amigo`, `mensagem`, `porque`, `quando`, `fazer`, `tempo`

## FAQ

### Why is my German gesture less accurate than English?

German has many compounds and inflected forms. If the intended word is rare or very long, the shape can be close to several alternatives. Shorter common words should be more reliable.

### Why do accented words sometimes appear without accents?

The gesture classifier maps accented characters to base Latin keys so the swipe path can be recognized on a standard layout. Candidate text depends on the dictionary entry that wins ranking.

### Why does an unsupported language not glide?

Unsupported languages are disabled by default to avoid English fallback results in the wrong language. Use tap typing or add a supported subtype until a licensed dictionary is bundled.

### Why do normal key swipes work in one language but not another?

General key swipes are paused only when glide typing is enabled for the active language. If glide is disabled for that language, normal key swipe actions are available again.

### What should contributors report?

Report the device model, Android version, keyboard subtype, language, test corpus results, wrong candidates, and whether the issue happens after switching from another language.
