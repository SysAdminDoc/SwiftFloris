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
   - Acceptance: common continuations outrank isolated dictionary completions before personal history is rich.

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

## Current Slice

Build item 1 first. It is the most direct path to SwiftKey's forgiving feel and creates reusable signal for the unified scorer, glide templates, and future neural reranker.
