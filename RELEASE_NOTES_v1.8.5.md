# SwiftFloris v1.8.5 — 2026-05-15

Fifth autonomous slice. Closes the P3-renderer wire-up so split-
keyboard mode actually renders split rows in tablet landscape, ships
a debug-only mock smart-compose provider so the v1.8.3 ghost-text
surface is exercisable on a connected device today, adds the
benchmarks docs scaffold, and expands LDML script coverage with five
new fixtures. **601 unit tests** at HEAD, 0 failures.

## P3-renderer — final TextKeyboardLayout wire-up

- `TextKeyboardLayout.kt` now detects `Fixed.SPLIT` mode from the
  active `windowSpec` and, after the existing `keyboard.layout(...)`
  call, invokes
  `SplitGutterPostPass.apply(keyboard, 80.dp.toPx())` to shift the
  right half of every row by 80 dp.
- `splitMode` is added to the `remember(...)` key list so toggling
  the preference correctly triggers a re-layout.
- Only applies to `KeyboardMode.CHARACTERS` (numeric / symbols /
  phone-pad keep their single-block layout).
- Closes the P3-renderer slice tracked in ROADMAP §0.

## L1 debug-only mock smart-compose provider

- New `app/src/debug/kotlin/DebugSmartComposeProvider` (lives in the
  `debug` source set so release builds **never** compile it).
- Ships a 10-entry hard-coded trigram lookup that returns sensible
  continuations for common sentence prefixes ("on my" → "way",
  "thank you so" → "much", "looking forward to" → "hearing from you",
  etc.). Pure offline lookup — no model, no network, no telemetry.
- Wired in `FlorisApplication.init` via **reflection** so release
  builds (which can't see the debug class) gracefully fall back to
  the default no-op `SmartComposeProvider`.
- Lets us verify the v1.8.3 ghost-text candidate plumbing on the
  installed debug APK before the real L1.1a LiteRT-LM addon ships.

## Next-12.1 — BENCHMARKS.md template

- New `docs/BENCHMARKS.md` documents:
  - How to run `:benchmark:connectedBenchmarkAndroidTest` against a
    clocks-locked device.
  - The four-benchmark table (imeFirstRender /
    suggestionStripRecomposition / dictionaryColdLoad / themeSwitch).
  - Trace-section naming convention (`swiftfloris.<subsystem>.<action>`)
    + the six existing sections.
  - The **8 % median frame regression threshold** for shipping a
    release that touches the IME hot path.
  - Historical baseline file pattern at
    `docs/benchmark-results/baseline-YYYY-MM-DD.json`.

## L8.3 — LDML script fixtures

New `LdmlScriptFixturesTest` covers five scripts the L8 / L8.1 / L8.2
parsers will see in real Keyman keyboards:

- **Khmer** — combining-mark display label round-trip (◌ា, ◌ោ).
- **Burmese** — transforms + displays interaction for medial Ya.
- **Tibetan** — consonant + vowel-mark transform with display.
- **Lao** — tone-mark display label; bare consonant has no override.
- **Sinhala** — mixed transforms-then-displays section ordering.

## Tests

601 unit tests at HEAD (was 596 at v1.8.4), 0 failures, 0 skipped.
5 net new tests from the LDML fixtures suite.
