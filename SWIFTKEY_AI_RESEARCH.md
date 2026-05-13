# SwiftKey AI Parity Research

**Date:** 2026-05-13

## Source-backed targets

- Microsoft documents three SwiftKey spacebar modes: plain space, current-word completion, and Quick prediction insert. Quick prediction insert always inserts the middle prediction plus a space, including next-word predictions.
- SwiftKey's prediction bar is a three-choice model where the middle candidate is the default spacebar action, and users can recover mistakes by tapping backspace and selecting a replacement prediction.
- SwiftKey Flow is mode-free: users can switch between tapping and gliding, Flow Through Space can cross the spacebar into the next word, and Flow can show either correction candidates or next-word predictions after a glide.
- AOSP LatinIME remains the strongest Apache-compatible reference for proximity-aware keyboard decoding concepts.
- FUTO Keyboard and CleverKeys are useful architectural references for modern offline keyboards, but their licenses and model assets must be treated carefully before any code or weight reuse.
- CleverKeys shows a practical current-generation path: quantized ONNX models, beam search, vocabulary filtering, and no network permission.
- ONNX Runtime's Android NNAPI execution provider is a plausible future integration path because it targets Android 8.1+ and can route supported operators through Android's neural acceleration APIs.

## Open-source projects worth studying

| Project | Useful idea | Reuse posture |
| --- | --- | --- |
| AOSP LatinIME | Proximity-aware correction, dictionary-backed suggestion architecture, IME battle-testing | Study concepts and APIs; compatible source may be reusable only after deliberate license review |
| FUTO Keyboard | Privacy-first LatinIME fork with modern offline keyboard direction | Study product and architecture; do not import code without license approval |
| CleverKeys | Public ONNX swipe pipeline, quantized transformer decoder, beam search, offline/no-network stance | Study architecture; do not copy GPL code into this Apache-rooted app |
| ONNX Runtime Mobile | On-device inference path for a future neural reranker or glide decoder | Candidate dependency only after model boundary, size, latency, and license checks |

## Recommended build path

1. Keep improving the current heuristic decoder until it exposes one clean score boundary.
2. Persist per-key touch distributions and feed normalized touch evidence into every candidate score.
3. Add an optional `NeuralCandidateReranker` interface that accepts previous words, active candidates, typed text, locale, and touch evidence.
4. Train or import only models that can ship locally, fit the APK budget, and run under 50-100 ms on mid-range hardware.
5. Use ONNX Runtime or TensorFlow Lite only behind that interface; keep heuristic ranking as the fallback.
6. Build a replay harness with synthetic typo paths, real rejected corrections, and glide traces before enabling any neural model by default.

## Immediate product gap closed in this pass

The SwiftKey quick spacebar mode is now implemented as a user-visible `Quick prediction insert` setting. It reuses the existing candidate strip, inserts the middle next-word prediction when there is no active word, ignores non-word suggestions for automatic insertion, and keeps the plain autocorrect behavior unchanged unless the user enables the setting.

## Adaptive touch persistence

Adaptive touch is now persisted locally by subtype. That closes the biggest weakness in the first adaptive-touch slice: the keyboard no longer forgets learned tap offsets when the process restarts.

## Sources

- Microsoft SwiftKey spacebar modes: https://support.microsoft.com/en-US/swiftkey-keyboard/how-does-the-spacebar-work-with-autocorrect-in-microsoft-swiftkey-keyboard
- Microsoft SwiftKey prediction bar: https://support.microsoft.com/en-us/swiftkey-keyboard/how-does-the-microsoft-swiftkey-prediction-bar-work
- Microsoft SwiftKey Flow: https://support.microsoft.com/en-us/swiftkey-keyboard/what-is-flow-and-how-do-i-enable-it-with-microsoft-swiftkey-keyboard
- AOSP LatinIME: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- FUTO Keyboard mirror: https://github.com/futo-org/android-keyboard
- CleverKeys project: https://cleverkeys.app/
- CleverKeys neural prediction spec: https://cleverkeys.app/specs/neural-prediction.html
- ONNX Runtime Android NNAPI provider: https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html
