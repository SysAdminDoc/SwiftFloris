# SwiftKey Parity Build Plan

**Goal:** close the remaining typing-behavior gap with SwiftKey while keeping SwiftFloris offline, local-first, and maintainable.

## Build Order

1. **Persistent adaptive touch model**
   - Persist per-subtype tap-offset distributions.
   - Restore learned key centers after app restart.
   - Keep password fields, incognito mode, long-presses, gesture moves, and popup selections out of learning.
   - Acceptance: learned key offsets survive process death and continue to bias tap/glide scoring.

2. **Unified candidate scoring**
   - Replace role-only ranking with an explicit score object.
   - Score spatial likelihood, edit distance, dictionary frequency, personal phrase frequency, context probability, language confidence, and rejection history.
   - Acceptance: every candidate can explain why it ranked where it did, and the same scorer is used for taps, spacebar, and glide.

3. **Replay harness**
   - Add deterministic traces for adjacent-key taps, row-gap taps, rejected corrections, next-word insertion, multilingual words, and glide paths.
   - Acceptance: SwiftKey-like decoder tuning happens against repeatable evidence instead of manual guessing.

4. **Better next-word and phrase prediction**
   - Add phrase continuation, recency decay, sentence-position priors, and cold-start phrase priors.
   - Acceptance: recent and common continuations outrank isolated dictionary completions before personal history is rich.

5. **Flow/glide parity**
   - Keep ambiguous glide words open briefly.
   - Rescore the previous glide candidate after the next word starts.
   - Use adaptive touch offsets in ideal trace generation.
   - Acceptance: tap and glide interleave naturally, and Flow Through Space can recover ambiguous short words from following context.

6. **Multilingual auto-detection**
   - Score every current word across active locales.
   - Suppress wrong-language autocorrect when any enabled language recognizes the typed token.
   - Acceptance: bilingual typing needs less manual language switching.

7. **On-device neural reranker**
   - Add a `NeuralCandidateReranker` boundary behind the unified scorer.
   - Start with a tiny quantized candidate reranker; keep heuristic scoring as fallback.
   - Evaluate ONNX Runtime Mobile or TensorFlow Lite after model size, latency, and license checks.
   - Acceptance: neural scoring improves candidate order without requiring network permission or cloud sync.

8. **Trust and learning controls**
   - Expand typing stats with accepted corrections, rejected corrections, learned words, phrase history, and touch adaptation confidence.
   - Add reset controls for learned words, phrase history, rejected corrections, and adaptive touch.
   - Acceptance: users can see and reset what the keyboard learned.

## Completed Slices

- Item 1 is implemented: adaptive touch persists bounded per-subtype tap-offset distributions locally and restores them at startup.
- Item 2 is substantially expanded: candidate ranking now emits an explicit score object with role, spatial likelihood, source affinity, provider confidence, dictionary frequency, personal context probability, language confidence, rejection penalty, edit proximity, completion affinity, and length penalty.
- Item 3 is expanded: deterministic replay tests now cover adjacent-key correction, known-word literal preservation, quick prediction spacebar behavior, dictionary-frequency ranking, personal phrase-context ranking, and rejected autocorrect demotion.
- Item 4 now has recency-aware personal context scoring plus a first English cold-start prior layer: bigram and trigram stores persist `lastSeenMs`, upgrade legacy TSV rows on flush, rank personal continuations with a 21-day half-life, and fall back to curated sentence-start/common-continuation priors before raw dictionary frequency.
- Item 7 now has the optional neural reranker seam: `NeuralCandidateReranker` can reorder scored candidates while the shipped default remains a no-op heuristic fallback.
- The first debug trace recorder is implemented and enriched: creating `<filesDir>/swiftkey_trace.enabled` enables local JSONL capture of suggestion scores, previous words, touch evidence, candidate source/index, auto-commit eligibility, and accepted/rejected autocorrect events at `<filesDir>/swiftkey_typing_traces.jsonl`.

## Current Slice

Keep expanding items 2, 3, and 4 until every SwiftKey-like typing decision has a replay case. The next concrete work is to promote captured JSONL traces into checked-in replay fixtures, add row-gap and multi-edit replay coverage, and start feeding accepted/rejected correction priors back into touch scoring.
