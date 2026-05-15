# SwiftFloris v1.8.3 — 2026-05-15

**SwiftKey full-parity slice.** Closes the three IME-side gaps from
the SwiftKey parity audit. After this slice, every visible SwiftKey
typing feature has a working surface in SwiftFloris; the only
remaining work is two opt-in addon APKs that supply the heavy native
runtimes (LiteRT-LM smart-compose, Bergamot translator).

**572 unit tests at HEAD**, 0 failures, 0 skipped.
`:app:compileDebugKotlin` + `:app:assembleDebug` clean.

## P1 — Smart-Compose inline ghost-text (IME-side)

- New `GhostTextSuggestionCandidate` data class added to
  `ime/nlp/SuggestionCandidate.kt` alongside the existing
  `WordSuggestionCandidate` / `ClipboardSuggestionCandidate` /
  `EmojiSuggestionCandidate` types.
- `NlpManager.suggest` now asks
  `SmartComposeProviderRegistry.active.predictNextTokens(...)` for a
  ghost-text continuation given the text-before-selection. When the
  active provider reports `isReady(locale) = true` AND returns a
  candidate at confidence ≥ 0.45, the candidate is appended to the
  suggestion list.
- Default provider behaviour is unchanged: `SmartComposeProvider.Default`
  returns `NoSuggestion`, so the strip looks exactly as before. The
  ghost-text candidate only appears once the L1.1a addon
  (`addons/smart-compose-litert/`) is installed and registers a real
  provider via `SmartComposeProviderRegistry.setActive(...)`.

## P2 — Translation smartbar quick-action (IME-side)

- New `QuickAction.TranslateSelection` data object added to
  `ime/smartbar/quickaction/QuickAction.kt`.
- On tap, reads the current selection from `EditorInstance`, calls
  `InlineTranslatorRegistry.active.translate(rawSelection, "auto", "en")`,
  and on success commits the translated text in place. On
  `TranslationResult.Unavailable` (default — no addon installed) it
  shows a Toast pointing the user at the InlineTranslator addon.
- Button label uses the 🌐 globe emoji glyph so it fits a single
  smartbar quick-action slot; full tooltip surfaces on long-press.
- Settings → Smartbar → Customize quick actions exposes
  `TranslateSelection` alongside the existing entries via the
  serialiser's `@SerialName("translate_selection")` discriminator.

## P3 — Split-keyboard preference → active mode wire-up

- `ImeWindowController.onWindowShown` now reads
  `prefs.keyboard.splitKeyboardEnabled` (default off, shipped in
  v1.8.0). When the toggle is on AND the IME is in fixed mode AND
  the current form-factor's
  `ImeWindowConstraints.Fixed.Split.isViable` returns true, the
  fixed sub-mode is promoted from `Fixed.NORMAL` to `Fixed.SPLIT`
  on the next session show.
- Per-key split-row rendering inside `TextKeyboardLayout` (the
  actual mid-row gutter emission + per-side touch hit-test math)
  is the heavier follow-up slice tracked as **P3-renderer** /
  prompt D5 in `docs/AI_PROMPTS_EXTERNAL_WORK.md`. The
  `SplitKeyboardLayoutCalculator` shipped in v1.8.2 already produces
  the per-row geometry the renderer will consume.

## Tests

- 572 unit tests at HEAD (was 545 at v1.8.2).
- New `GhostTextSuggestionCandidateTest` pins 5 invariants on the
  ghost-text candidate (validation of confidence + text + tokenCount;
  default tokenCount = 1; non-auto-commit; non-user-removable).

## Tracker update

ROADMAP §0 SwiftKey Full-Parity Tracker now shows P1 / P2 / P3 all
✅ on the IME side. The two remaining items for *full* parity
(including the runtime behaviour, not just the surface) are the L1.1a
and L2.1a addons; both are documented as standalone AI prompts in
`docs/AI_PROMPTS_EXTERNAL_WORK.md`.
