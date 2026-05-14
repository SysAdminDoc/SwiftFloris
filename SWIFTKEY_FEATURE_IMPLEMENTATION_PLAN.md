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

## Second Slice

The second implementation slice starts touch-aware typing:

- Rescue character-key taps that land just outside key bounds but still close to a visible key.
- Keep the rescue bounded so accidental touches far from the keyboard are ignored.
- Train the adaptive touch model on successful normal tap-up events rather than touch-down events.
- Avoid learning from cancelled touches, long-presses, swipe gestures, popup selections, or glide traces.

## Third Slice

The third implementation slice tightens SwiftKey-style spacebar semantics:

- Treat the classic candidate strip as left alternative, middle action, right alternative.
- Place recognized typed words in the middle slot so pressing space keeps the literal word.
- Let pressing space complete or correct an unrecognized current word with the middle prediction.
- Keep high-confidence autocorrect and shortcut replacements ahead of generic prediction insertion.
- Leave next-word "always insert prediction" as a separate explicit setting, because SwiftKey exposes it separately from normal autocorrect.

## Fourth Slice

The fourth implementation slice starts the real touch-decoder loop:

- Record the primary key plus bounded nearby letter alternatives on successful tap-up events.
- Feed the current word's recent touch evidence into the central SwiftKey-style ranker.
- Promote candidates whose replacement path matches nearby-key evidence, even when their raw confidence is lower than a generic fallback.
- Keep recognized typed words as the middle spacebar action, so spatial evidence does not replace deliberate valid words.
- Keep the evidence transient, incognito-gated, password-field-gated, and bounded until the per-subtype persistent touch model is ready.

## Fifth Slice

The fifth implementation slice closes SwiftKey's documented quick spacebar mode:

- Add an explicit `Quick prediction insert` Typing setting, matching SwiftKey's separate spacebar behavior.
- Let space insert the middle next-word prediction when no current word is active.
- Keep normal autocorrect semantics intact when the setting is disabled.
- Skip emoji and clipboard candidates for automatic spacebar insertion.
- Suppress a plain space when quick insertion is waiting on a word prediction, so the setting behaves like SwiftKey's "prediction, not space" mode.

## AI Research Slice

The next high-leverage build path is an optional on-device neural reranker, not a cloud feature:

- Borrow architecture ideas from AOSP LatinIME's proximity model and CleverKeys' public ONNX swipe pipeline, while keeping SwiftFloris' Apache-compatible codebase clean.
- Build a small decoder contract that can score `(typed word, touch evidence, previous words, candidate list)` and return rescored candidates.
- Start with the current heuristic ranker as the fallback implementation.
- Later attach a quantized ONNX model through Android NNAPI/XNNPACK after the candidate contract is stable and test-covered.
- Keep the base APK free of network permissions and keep every model local, user-resettable, and incognito-aware.

## Sixth Slice

The sixth implementation slice makes adaptive touch durable:

- Persist per-subtype tap-offset distributions locally.
- Restore learned offsets at app startup so hit-testing, nearby-key evidence, and glide ideal traces keep improving after process death.
- Save in bounded batches to avoid disk churn while typing.
- Keep the existing privacy gates: no learning in incognito, password fields, long-presses, popup selections, or gesture moves.
- Acceptance: adaptive-touch sample counts and adjusted key centers survive a serialize/restore cycle.

## Seventh Slice

The seventh implementation slice starts the unified scorer and replay harness:

- Replace role-only candidate ordering with an explicit `SwiftKeyCandidateScore`.
- Score role, spatial likelihood, preferred-vs-fallback source affinity, provider confidence, dictionary frequency, personal context probability, language confidence, rejection penalty, edit proximity, completion affinity, and length penalty in one object.
- Expose scored candidates for deterministic tests and future debugging.
- Add replay cases for adjacent-key correction, known-word literal preservation, quick prediction spacebar insertion, dictionary frequency, personal phrase context, and rejected autocorrect demotion.
- Acceptance: existing candidate-strip behavior remains stable while every new SwiftKey-like behavior can be protected by a replay case.

## Eighth Slice

The eighth implementation slice should move from synthetic replay to field-tunable local learning:

- Add a debug-only typing trace recorder that captures typed text, candidate order, selected candidate, touch evidence, context words, and rejection events without network upload. The first trace recorder is now present and writes local JSONL when `<filesDir>/swiftkey_trace.enabled` exists.
- Add recency decay to personal bigram/trigram scoring so recent user phrases can beat stale history without unbounded growth. This is now implemented with backward-compatible TSV migration and a shared 21-day half-life scorer for personal n-grams.
- Add a `NeuralCandidateReranker` interface behind the current scorer. This boundary now exists with a disabled no-op implementation, safe heuristic backfill, and tests proving a future local model can reorder scored candidates without becoming a hard dependency.
- Acceptance: ranking changes can be evaluated against recorded local traces before enabling more aggressive neural or context scoring.

## Ninth Slice

The ninth implementation slice should turn recorded local evidence into safer tuning:

- Convert selected `swiftkey_typing_traces.jsonl` rows into deterministic replay fixtures that preserve typed text, context words, candidate order, selected candidate, correction acceptance, and rejection events.
- Add replay coverage for row-gap taps, multi-edit typos, mixed-language tokens, short glide ambiguity, and next-word prediction in empty fields.
- Add sentence-position priors so cold-start predictions can prefer likely starts such as "I", "The", "This", and "What" without requiring personal history first. The first English prior layer is now implemented for sentence starts and common short continuations, and is gated to English locales.
- Enrich trace JSON with previous words, touch evidence, auto-commit eligibility, candidate source, and candidate index so local traces contain enough structure to become replay fixtures.
- Define the optional `NeuralCandidateReranker` contract, with heuristic pass-through as the default implementation and no model dependency in the base APK. This is now implemented at the scorer boundary.
- Acceptance: every future scorer weight change can be checked against real local traces plus synthetic SwiftKey-parity cases.

## Tenth Slice

The tenth implementation slice should close the trace-to-replay loop:

- Add a checked-in anonymized JSONL fixture format for selected local traces. The first fixture now lives at `app/src/test/resources/swiftkey/replay/trace_replay_cases.jsonl`.
- Build a parser that turns suggestion trace events into `ReplayRankerCase` instances. The parser now runs in local JVM tests using Kotlin serialization rather than Android `org.json` stubs.
- Add deterministic cases for row-gap taps, multi-edit typos, mixed-language words, short glide ambiguity, and empty-field next-word insertion. Current coverage includes row-gap adjacent correction, adjacent transposition correction, missing-letter correction, extra-letter correction, double-letter correction, mixed-language literal protection, empty-field quick prediction insertion, and rejected-correction demotion.
- Add a tiny benchmark-style unit test that verifies a replay batch can run without Android framework dependencies. The first replay batch test now verifies fixture coverage and ranker replay in a plain unit test.
- Acceptance: score tuning can be driven by captured local behavior instead of manually crafted examples only.

## Eleventh Slice

The eleventh implementation slice uses replay evidence to improve touch correction depth:

- Expand touch scoring from equal-length substitutions and adjacent transpositions to bounded insertion/deletion alignment where key evidence supports the edit. This is now implemented for conservative one- and two-edit paths.
- Add fixture cases for missing-letter, extra-letter, and double-letter mistakes that users commonly hit while typing fast. These cases now live in the checked-in JSONL replay fixture and direct ranker tests.
- Feed rejected correction pairs back into touch evidence weighting so repeated backspace rejection lowers the spatial score for that pair.
- Add a short-glide ambiguity fixture so Flow candidates can stay recoverable until the following word gives context.
- Acceptance: row-gap, transposition, and insertion/deletion corrections are replay-protected before any neural reranker is attached. Short-glide correction and rejected-pair touch weighting remain open.

## Twelfth Slice

The twelfth implementation slice should turn correction outcomes into spatial priors:

- Feed accepted corrections into touch weighting so repeatedly accepted pairs rise faster than generic nearby-key evidence.
- Feed rejected corrections into touch weighting so repeated backspace rejection lowers spatial confidence for that typed/candidate pair.
- Add replay fixtures for accepted correction reinforcement, rejected spatial correction demotion, and short-glide ambiguity after the next word starts.
- Acceptance: the scorer can distinguish a one-off plausible touch correction from a correction the user has repeatedly accepted or rejected.
