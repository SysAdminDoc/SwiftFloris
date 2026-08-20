# SwiftFloris Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-10)

### P1

### P2

### P3

## Research-Driven Additions (2026-08-11)

### P3

- [ ] P3 — Turn the spacebar into a continuous cursor trackpad
  Why: spacebar gestures currently dispatch discrete DPAD key events bound to four swipe directions; Gboard and HeliBoard both ship a continuous drag that moves the cursor proportionally, which is materially better for editing and is the most-cited text-editing improvement of 2026. The existing gesture pipeline and the clamped selection bounds are the hard parts and both already exist.
  Evidence: `app/prefs/GesturesPrefs.kt:90-117` (four discrete swipe actions plus sensitivity; the comment at `:95` already anticipates "continuous vertical trackpad"); `ime/keyboard/KeyboardManager.kt:648-663` dispatches DPAD events; clamping at `ime/editor/EditorInstance.kt:533,544`
  Touches: `ime/text/gestures/SwipeGesture.kt`, `ime/keyboard/KeyboardManager.kt`, `app/prefs/GesturesPrefs.kt`, `app/settings/gestures/GesturesScreen.kt`
  Acceptance: holding and dragging the spacebar moves the cursor continuously with a configurable ratio, releasing leaves the cursor where the finger stopped, the discrete swipe actions remain available for users who prefer them, and every computed index stays inside `safeEditorBounds`.
  Complexity: M

- [ ] P3 — Split the offensive-word filter and expose autocorrect aggressiveness
  Why: `blockPossiblyOffensive` is a single boolean, so a user who wants profanity suggested but slurs filtered has no option — FUTO split exactly this in v0.1.29.1. And autocorrect is on/off with a commit-mode enum but no confidence threshold, while HeliBoard shipped a confidence slider in v4.0; users repeatedly report over-correction of ordinals and punctuation.
  Evidence: `app/prefs/SuggestionPrefs.kt:86`; `app/prefs/CorrectionPrefs.kt:80,84`; `ime/nlp/ImmediateAutocorrect.kt`; https://github.com/futo-org/android-keyboard/releases/tag/v0.1.29.1 ; https://github.com/HeliBorg/HeliBoard/releases/tag/v4.0-alpha1 ; https://github.com/HeliBorg/HeliBoard/issues/2665 ; https://github.com/HeliBorg/HeliBoard/issues/2727
  Touches: `app/prefs/SuggestionPrefs.kt`, `app/prefs/CorrectionPrefs.kt`, `ime/nlp/ImmediateAutocorrect.kt`, `ime/nlp/SwiftKeyCandidateRanker.kt`, `app/settings/typing/`
  Acceptance: the offensive filter has at least a slurs-only tier alongside the existing all-or-nothing setting; autocorrect exposes a confidence threshold that feeds the ranker's accept bar; the typing-quality scorecard records the score at each threshold so the default is chosen from data.
  Complexity: M

- [ ] P3 — Align the split-keyboard gutter to the physical hinge
  Why: split geometry is derived from `WindowSizeClass` alone; only `androidx.window:window-core` is on the classpath, so there is no `WindowInfoTracker`/`FoldingFeature` consumer and the gutter never lands on the fold. Foldable-specific keyboard bugs are the most common hardware complaint against every competitor, and HeliBoard shipped separate foldable scaling in v4.0.
  Evidence: `ime/window/ImeFormFactor.kt:23-24` (only `WindowSizeClass`); `gradle/libs.versions.toml` pins `androidx-window-core`, not `androidx.window`; `ime/window/SplitKeyboardLayoutCalculator.kt`, `ime/text/keyboard/SplitGutterPostPass.kt`; https://github.com/HeliBorg/HeliBoard/issues/2708
  Touches: `gradle/libs.versions.toml`, `ime/window/ImeFormFactor.kt`, `ime/window/SplitKeyboardLayoutCalculator.kt`, `app/prefs/KeyboardPrefs.kt`
  Acceptance: on a device reporting a vertical `FoldingFeature`, the split gutter aligns to the hinge bounds and the halves size to the reported posture; behaviour on non-folding devices is byte-identical to the pre-change baseline, proved by the existing split tests and a Roborazzi capture. Verify on the emulator foldable profile — the attached device does not fold.
  Complexity: M

- [ ] P3 — Prepare for Unicode 18 emoji data
  Why: Unicode 18.0 ships 2026-09-16 with nine new emoji, and the bundled data is generated from CLDR 48 / Emoji 17.0. The regeneration path is now understood and the version header is honest as of v1.9.59, so this is a scheduled data refresh rather than an investigation — but `EmojiDataVersion` still has no production consumer, so nothing would notice stale data at runtime.
  Evidence: `app/src/main/assets/ime/media/emoji/*.txt` (`# EMOJI-VERSION: 17.0`); `ime/media/emoji/EmojiData.kt:30-33` parsed only by `EmojiDataVersionTest`; https://www.unicode.org/versions/beta-18.0.0.html ; https://emojipedia.org/unicode-18.0
  Touches: `app/src/main/assets/ime/media/emoji/*.txt`, `ime/media/emoji/EmojiData.kt`, `app/src/test/.../EmojiDataVersionTest.kt`, `CHANGELOG.md`
  Acceptance: after CLDR publishes its Unicode 18 update, the assets are regenerated, the declared version matches a probe set of Emoji 18.0 code points present in the data, and `EmojiDataVersion` gains a real consumer so a mismatch is observable at runtime rather than only in a test.
  Complexity: S
  Note (2026-08-20): now schedulable — draft `emoji-test.txt` v18.0 (dated 2026-04-30) is published at unicode.org/Public/draft/emoji/; final data lands at unicode.org/Public/emoji/18.0/ on 2026-09-16. Localized names/annotations/search keywords arrive with CLDR 49 (49-alpha0 tagged 2026-08-14; final ~Oct 2026) — CLDR 48 will never carry Emoji 18. Codepoints and ordering are regenerable today; hold annotation regeneration for CLDR 49.

- [ ] P3 — Evaluate a bundled rule-based offline proofreader
  Why: Gboard's on-device writing tools are gated to Gemini-Nano-class hardware and the Grammarly keyboard is being discontinued, leaving grammar assistance unavailable to everyone on ordinary devices. A rule-and-dictionary proofreader is the one credible offline answer that fits `minSdk 26` and needs no model runtime — and SwiftFloris already has the surfaces (spell-checker service, smartbar candidates, `SensitiveFieldGuard`, the addon contract) to host it without touching the base APK's no-network posture.
  Evidence: `ime/nlp/SpellingResult.kt:52-58,116` already carries the Android 12+ grammar-error attribute but nothing produces one; `ime/smartcompose/SensitiveFieldGuard.kt`; `AddonContract` already defines `SMART_COMPOSE_RUNTIME`; https://github.com/futo-org/android-keyboard/issues/2217 ; https://support.google.com/gboard/answer/16515540 ; https://support.grammarly.com/hc/en-us/articles/25038364027661--The-Grammarly-Keyboard-for-Android-will-be-discontinued
  Touches: `ime/nlp/SpellingResult.kt`, `FlorisSpellCheckerService.kt`, `ime/smartcompose/`, `addons/`, `docs/PRIVACY_AND_AI.md`
  Acceptance: a written evaluation covering licence compatibility with Apache-2.0, per-language rule-data size, and APK-vs-addon packaging, plus a spike proving one English rule set produces `RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR` results through the existing spell-checker service on the attached device. Ship the decision, not the integration, in this item. Distinct from the blocked transformer-prediction addon: no model runtime, no GPU, no `INTERNET`.
  Complexity: L

## Research-Driven Additions (2026-08-20)

### P1

### P2

### P3


