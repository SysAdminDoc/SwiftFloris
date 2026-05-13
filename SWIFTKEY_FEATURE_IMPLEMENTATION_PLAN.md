# SwiftKey Feature Implementation Plan

**Goal:** make SwiftFloris match the parts of SwiftKey that matter for everyday typing: forgiving touch, confident autocorrect, strong next-word prediction, smooth Flow-style glide typing, multilingual typing without mode churn, and clear local-only personalization.

**Non-goals:** Microsoft account sync, Copilot, cloud stickers, telemetry, network permission, or proprietary model/code reuse.

## Current Baseline

SwiftFloris already has the right product thesis: offline keyboard, SwiftKey-like visual layout, adaptive touch hooks, personal dictionary learning, English contraction autocorrect, next-word n-grams, glide typing, multilingual suggestion scaffolding, and autocorrect reject-on-backspace behavior.

The main weakness is architectural: candidate generation is split across dictionary completion, typo correction, user dictionary, emoji, clipboard, and later touch/glide logic. SwiftKey-like behavior needs one decoder decision point that can weigh literal typed text, spatial likelihood, language-model probability, personalization, and context together.

## Build Strategy

Build a local decoder in layers. Every layer must keep the app usable and shippable.

1. **Candidate Decoder Foundation**
   - Centralize ranking of typed literal, autocorrect, completions, personal suggestions, emoji, clipboard, and later touch candidates.
   - Keep typed literal visible as a safe escape hatch.
   - Preserve eligible autocorrect candidates for space/punctuation commit.
   - Add unit tests for ranking stability and duplicate handling.
   - Acceptance: candidate strip behaves predictably without weakening existing auto-commit.

2. **Touch-Aware Tap Decoder**
   - Capture per-key tap evidence from the keyboard touch pipeline.
   - Score nearby-key alternatives instead of only dispatching the resolved key.
   - Feed spatial candidates into the decoder with per-user adaptive offsets.
   - Persist bounded per-subtype touch statistics.
   - Acceptance: common fat-finger errors are corrected by spatial likelihood before language-model fallback.

3. **Autocorrect Semantics**
   - Match SwiftKey's high-confidence spacebar behavior: space accepts the best correction, backspace restores the original, repeated rejection suppresses that pair.
   - Add negative learning and cooldowns for rejected corrections.
   - Tune auto-commit thresholds by word length, edit distance, frequency, and spatial likelihood.
   - Acceptance: accidental corrections become rarer over time, while obvious typos stay automatic.

4. **Language Model Upgrade**
   - Build a local ranker from unigram, bigram, trigram, phrase, and personal frequencies.
   - Add next-word and phrase continuation scoring.
   - Keep all learning incognito-aware and local.
   - Acceptance: common phrase continuations outrank isolated dictionary completions.

5. **Flow / Gesture Parity**
   - Use the adaptive touch model as a spatial prior for glide traces.
   - Add Flow Through Space: a glide can cross the space key and continue into the next word.
   - Handle double letters through dwell/slowdown near a key.
   - Rescore ambiguous short words after the next word starts.
   - Acceptance: tap and glide can interleave without mode switching, and swipe-word boundaries feel natural.

6. **Multilingual Hot-Switch**
   - Score active languages per word instead of relying on manual switching.
   - Support up to five active language dictionaries.
   - Suppress wrong-language autocorrect when a typed word is recognized by any active language.
   - Acceptance: bilingual typing no longer requires keyboard switching for normal mixed-language sentences.

7. **Personalization and Trust UX**
   - Add local typing stats: corrections accepted/rejected, learned words, accuracy delta, top suggestions, and touch adaptation confidence.
   - Add controls to reset learned words, reset touch model, and pause learning.
   - Keep the no-network contract visible and verifiable.
   - Acceptance: users can understand and control what the keyboard learned.

## First Slice

The first implementation slice is the decoder foundation:

- Add a SwiftKey-style candidate ranker.
- Inject the typed literal candidate for active words.
- Preserve autocorrect candidates and user-dictionary priority.
- Route `NlpManager` through that ranker before publishing the strip.
- Cover ranking behavior with unit tests.

This gives future touch and Flow work one stable integration point instead of adding more one-off heuristics.
