# SwiftFloris v1.7.5 — SwiftKey indistinguishability wave

**Released:** 2026-05-09
**Versioning:** 1.7.4 → **1.7.5** (versionCode 174 → 175)

This release closes the new **N12 "SwiftKey indistinguishability"** roadmap section plus a chunk of the original **Next-1 SymSpell** item — twelve commits, all on-device, no Copilot, no cloud, no account. The goal: a user can't tell whether they're typing on SwiftKey or on SwiftFloris.

See `SWIFTKEY_PARITY_RESEARCH.md` for the underlying research.

---

## What's new

### Surface fixes (the two paper-cuts you'd notice in 30 seconds)
- **Auto-space after punctuation defaults to ON.** Period / comma / `?` / `!` now insert a trailing space without a settings tweak. Existing user overrides still win, so already-installed users toggle in Settings → Typing.
- **Suggestion-tap haptic.** Tapping an autocorrect / suggestion strip word now fires the same key-press vibration as tapping a letter key, with `keyLongPress()` on the long-press branch.

### N12.1 — Adaptive touch model
New `AdaptiveTouchModel` keeps per-subtype, per-key Welford-online stats of the user's actual tap-offset distribution (normalised by key half-size). After ≥30 samples per key, hit-tests bias toward where the user actually taps using a 2D-Gaussian log-likelihood — the SwiftKey "feels accurate" effect, all on-device, no offsets ever written to disk.

### N12.2 — Next-word predictions via PersonalBigramStore
New per-locale bigram counter persisted to `<filesDir>/personal_bigrams_<localeTag>.tsv`. Caps: 2,000 prev words per locale, 16 next words per prev, max count 1,000, MIN_COUNT=2. The suggestion strip is no longer empty after a space — it shows the top bigram completions for the previous word.

### N12.3 — Multi-language hot-switch
When a subtype has secondary locales enrolled (`SubtypeEditorScreen` already supports this), `LatinLanguageProvider.suggest` queries every enrolled locale's dictionary and merges per-locale candidates with a `prior` of `1.0` for any locale that recognised the typed word and `0.4` for those that didn't. `isEligibleForAutoCommit` is gated to recognising locales — no more wrong-language autocorrect mid-sentence.

### N12.4 — Flow Through Space
`GlideTypingGesture.Detector.signalWordBoundary()` snapshots and resets the trace mid-stroke; the controller fires it when the trace re-enters the SPACE key after first leaving it. Phantom-space inserts the " " between committed words, classifier resets between words, trail-fade visually punctuates each. Glide a word, drag finger across the space bar, glide the next word — all without lifting.

### N12.5 — Trigram next-word predictor
New `PersonalTrigramStore` is a per-locale `(prev2, prev1) → next` counter persisted to `<filesDir>/personal_trigrams_<localeTag>.tsv`. Caps: 4,000 contexts, 12 next words per context. `KeyboardManager.learnIfAllowed` now learns both bigrams and trigrams via a sliding two-word window. After typing `the quick brown fox` a couple of times, typing `the quick` surfaces `brown` as the top suggestion.

### N12.6 — Typing stats screen
New Settings → Typing → "Typing stats" screen reads three on-device numbers off-thread:
- Words-learned count + top-10 personal-dictionary entries by frequency
- Total bigram-store size on disk
- Adaptive-touch-model session sample count

No data leaves the device.

### N12.7 — Cold-start bootstrap from dictionary frequency
`LatinDictionarySnapshot.topByFrequency(n)` lazily caches the top-64 high-frequency dictionary words. Suggestions now layer Tier 0 (trigram, 0.80–0.45) → Tier 1 (bigram, 0.55–0.20) → Tier 2 (dict bootstrap, 0.30–0.55). Result: never-empty suggestion strip on cold-start and after sentence-ending punctuation. Sentence-start detection auto-capitalises the first letter.

### N12.8 — Adaptive touch model feeds the glide classifier
`AdaptiveTouchModel.adjustedCenter(...)` returns user-personalised pixel centers. `StatisticalGlideTypingClassifier.findNClosestKeys` (matching) and `Pruner.generateIdealGestures` (template) both consult `adjustedCenter()` instead of `key.visibleBounds.center`. Bias clamped to ±0.5×half so a heavily-skewed learner can't drag the template outside the visible key. Gives glide the same per-user spatial bias N12.1 already gives taps.

### N12.9 — Sentence-case suggestions
After `.`, `!`, or `?` (or empty input), every next-word suggestion's first letter is capitalised. SwiftKey-parity at sentence start.

### N12.10 — Long-press suggestion to forget
`WordSuggestionCandidate` from next-word predictions and personal-dict suggestions now both ship `isEligibleForUserRemoval = true`. New `DictionaryManager.forgetWord`, `PersonalBigramStore.forget`, `PersonalTrigramStore.forget` are all consulted by `LatinLanguageProvider.removeSuggestion`. Long-press a noisy suggestion → it's gone from personal dict, bigrams, *and* trigrams in one stroke.

### Next-1.A — SymSpell delete-index for distance-1 corrections
New pure-Kotlin `SymSpellIndex.kt`. `LatinDictionarySnapshot.symSpellIndex` is `by lazy` so the build (~100–300 ms over the 117k-word EN dict) lands on first correction call. `LatinDictionarySuggester.knownEdits1` now calls `dictionary.symSpellIndex.candidatesAtDistance1(input)` instead of generating Norvig's `L · 54` candidate strings per call — ~50× speedup on the per-keystroke correction path.

### Next-1.B — Distance-2 high-frequency auto-commit
New `AutoCommitMinFrequencyDistance2 = 0.92` threshold. Distance-2 corrections now auto-commit on space when the candidate is in the very common bucket (~top 3k SCOWL words). Closes the long-word-typo gap: `recieved → received`, `tommorrow → tomorrow`, `seperate → separate`, `definately → definitely`.

---

## Settings reference

Every new behavior is gated behind a pref so power-users can opt out:

- **Typing → Adaptive touch model** (default on)
- **Typing → Predict the next word** (default on)
- **Typing → Multilingual suggestions** (default on)
- **Gestures → Flow through space** (default on)
- **Typing → Typing stats** (link to the new screen)

---

## Out of scope (explicit non-goals)

- Microsoft Copilot / Editor / Tone
- DALL-E sticker / Designer
- Microsoft account login or sync
- Federated learning aggregator
- Anything that requires the `INTERNET` permission

---

## Roadmap status

- **N12 SwiftKey indistinguishability** — 10/10 ticked (12.5 absorbed into the trigram tier)
- **Next-1 SymSpell** — 1.A and 1.B ticked, 1.C (full d2 SymSpell index) deferred pending field data
- **L1 On-device LLM (Gemma 3 270M Q4)** — still the next big jump; multi-week
- **Next-3.1 Pre-trained KenLM 5-gram bootstrap** — would give first-time users rich predictions before they've typed anything

---

## Verification

- `./gradlew :app:assembleDebug` — green
- `./gradlew :app:assembleRelease` — green (signed when `SIGNING_KEYSTORE_BASE64` is set; otherwise falls through to debug signing as documented in N6.2)
- `:app:verifyNoInternetPermission` — green (no INTERNET permission added by any item)
- adb-installed on local Pixel-class device, smoke-tested across all five new prefs
