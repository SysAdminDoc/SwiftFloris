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
- Item 3 now has a checked-in trace replay fixture path: anonymized JSONL suggestion traces can be parsed by local JVM tests and replayed through the ranker, currently covering row-gap adjacent correction, adjacent transposition correction, missing-letter correction, extra-letter correction, double-letter correction, mixed-language literal protection, empty-field quick prediction insertion, and rejected-correction demotion.
- Item 3 also protects bounded touch-edit alignment: the scorer now handles same-length nearby-key substitution, adjacent transposition, interior missing letters, supported extra letters, and accidental double letters before promoting a candidate to `SpatialCorrection`.
- Item 3 now feeds correction outcomes back into spatial ranking: accepted typed/corrected pairs build local confidence, rejected pairs dampen spatial confidence before role selection, and both are represented in JSONL replay fixtures.
- Item 4 now has recency-aware personal context scoring plus a first English cold-start prior layer: bigram and trigram stores persist `lastSeenMs`, upgrade legacy TSV rows on flush, rank personal continuations with a 21-day half-life, and fall back to curated sentence-start/common-continuation priors before raw dictionary frequency.
- Item 4 now has richer phrase priors: cold-start English prediction checks two- and three-word phrase contexts before falling back to single-word continuations, covering common patterns like `let me -> know`, `as soon as -> possible`, and `thank you for -> the`.
- Item 5 now has a first short-glide context rescue path: Flow commits keep a bounded pending candidate list for 6 seconds, the next committed glide word scores the previous candidates against personal/cold-start next-word context, and conservative short-word rescoring can replace ambiguous commits such as `in` with `I'm` before the next word is committed.
- Item 6 is expanded: typed-word known detection now checks every active subtype locale, candidate dictionary frequency is scored across active locales, and a pure `MultilingualTokenScorer` lowers wrong-language correction confidence when the typed token is recognized in another enabled language.
- Item 7 now has the optional neural reranker seam: `NeuralCandidateReranker` can reorder scored candidates while the shipped default remains a no-op heuristic fallback.
- The first debug trace recorder is implemented and enriched: creating `<filesDir>/swiftkey_trace.enabled` enables local JSONL capture of suggestion scores, previous words, touch evidence, candidate source/index, auto-commit eligibility, and accepted/rejected autocorrect events at `<filesDir>/swiftkey_typing_traces.jsonl`.
- Item 8 now exposes the learned-model trust surface: Typing stats shows learned dictionary words, bigram/trigram counts plus disk usage, correction outcome prior count, and adaptive-touch samples, with local reset actions for phrase predictions, correction memory, adaptive touch, and all non-dictionary typing learning.
- Item 3 and Item 8 are now connected: Typing stats can enable, share, and clear local JSONL trace capture, and the checked-in replay fixture set adds `thos -> this` plus phrase-context prediction coverage.
- Item 3 now has aggregate replay outcome metrics: full-ranking hits, spacebar-action hits, expected role hits, and typed-literal protection misses are derived from the JSONL fixture set and guarded in tests.
- Item 3 and Item 5 now have category-specific replay guards: suggestion JSONL fixtures tag bilingual token-protection cases, and glide context has a separate JSONL fixture set with aggregate metrics for short-word rescue and conservative no-op behavior.
- Item 2 and Item 5 now have explicit tuning boundaries: candidate scoring and glide context rescue expose default-preserving tuning objects, and replay tests prove conservative variants are caught by aggregate metrics before production defaults change.
- Item 3 and Item 8 now have a fixture export bridge: Typing stats can share sanitized replay fixtures converted from the local trace file, preserving candidate evidence and accepted/rejected outcomes while dropping timing and surrounding-context metadata.
- Items 3, 4, and 5 are now connected more tightly: cold-start phrase priors feed partial-word candidate scoring, checked-in replay fixtures guard `let me kn -> know`, and glide context fixtures cover a longer contraction rescue (`were` + `going` -> `we're`). Undo/retype handling also keeps a manually restored word literal after an autocorrect rejection instead of allowing a different replacement to take over the same word slot.

## Current Slice

Keep expanding items 2, 3, 4, 5, and 6 until every SwiftKey-like typing decision has a replay case. The next concrete work is to use the expanded replay set to tune correction thresholds and glide rescoring.

Current next step: keep promoting real exported trace failures into checked-in fixtures, especially active-locale switching and multi-word autocorrect repairs, then use the tuning objects to justify any production default changes with before/after replay metrics.
