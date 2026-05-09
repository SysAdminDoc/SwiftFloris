# SwiftFloris v1.5.4

Typing-quality pass focused on SwiftKey-parity feel: random capitalization gone, autocorrect less aggressive, and a real "Autocorrect" toggle separate from "Display suggestions".

## Capitalization fixes

- **Auto-cap no longer triggers after digits, abbreviations, or URLs.** Typing `192.168.0.1`, `3.14`, `e.g.`, or `U.S.A.` no longer arms the next letter to be capitalized. Auto-cap now requires the punctuation to actually end a word: a letter immediately before `.`, `!`, or `?`, with no second `.` two characters back (excludes ellipses and abbreviation chains).
- **Auto-cap no longer secretly inserts a space after `.!?`.** Auto-capitalization and auto-space-after-punctuation are now fully independent settings — turning auto-space off no longer leaves `.` injecting a stealth space.
- **Auto-cap state no longer "sticks" across cursor moves.** Tapping into the middle of a sentence no longer leaves the next letter unexpectedly capitalized. The shifted-automatic state is now re-evaluated against the actual text before the cursor (with a fallback for apps like TikTok that hide caps mode from the IME).

## Autocorrect fixes

- **Pronoun substitution respects the dictionary.** `ill`, `id`, `im`, and `ive` are real English words — they are no longer silently auto-replaced with `I'll`/`I'd`/`I'm`/`I've`. Multi-letter pronoun forms are still offered as tap-to-accept suggestions when the typed word is not a real word; only the unambiguous `i` → `I` substitution still auto-commits.
- **Stricter auto-commit threshold.** The minimum candidate frequency for silent replacement-on-space rose from 0.62 to 0.78, and the minimum word length rose from 3 to 4. Rare words and proper nouns are no longer swapped to common dictionary lookalikes.

## New setting: Autocorrect toggle

- **Settings → Typing → Corrections → Autocorrect.** Defaults to on. Turn it off to keep the suggestion strip visible while disabling silent word replacement on space/punctuation — matching SwiftKey's "Autocorrect" master switch.

## Files changed

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/ImmediateAutocorrect.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/typing/TypingScreen.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties`, `README.md`, `ROADMAP.md`
