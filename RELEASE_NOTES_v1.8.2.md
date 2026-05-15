# SwiftFloris v1.8.2 — 2026-05-15

Third autonomous ROADMAP pass on the same day as v1.8.0 + v1.8.1.
Drills into every `.Xa` follow-up slice that had been parked for a
later round. **545 unit tests at HEAD** (was 494 in v1.8.1), 0
failures, 0 skipped — the Roborazzi screenshot tests now run in CI
after the Next-12.2a launcher-Activity unblock.

## LATER-tier follow-ups

- **L4.1 Arabic connected-form shaper.** New `ArabicShaper.shape(text)`
  rewrites every base-form Arabic codepoint (U+0621–U+064A) into the
  appropriate **presentation form** glyph (U+FE70–U+FEFC) based on its
  position in a connected run. Handles isolated / initial / medial /
  final forms; respects right-joining-only letters (Alef, Dal, Reh,
  Waw, Teh-Marbuta). Lets the smartbar preview row + WordStyles
  renderer + addon-driven export paths lay down correctly-joined
  glyphs regardless of the receiving editor's font.

- **L4.2 Persian / Urdu normaliser.** New `PersianUrduNormalizer.normalize`
  rewrites Arabic Yeh `\u064A` → Farsi Yeh `\u06CC`, Arabic Kaf
  `\u0643` → Farsi Kaf `\u06A9`, Alef Maksura `\u0649` → Farsi Yeh.
  Optional `stripTatweel = true` removes accumulated `\u0640` stretch
  glyphs. `PersianDigitMode` enum toggles between `KEEP_ARABIC`,
  `TO_PERSIAN` (Western → U+06F0..06F9), and `TO_LATIN`.

- **L5.x six new Indic tables** — Bengali / Tamil / Telugu / Gujarati
  / Gurmukhi (Punjabi) / Kannada all ride the existing
  `IndicTransliterator` greedy-longest-match engine. Each table covers
  the canonical vowels + consonants + digits + anusvara/visarga +
  danda punctuation for its script. Total Indic-script coverage now
  spans 7 scripts.

- **L6.1 Amharic SERA keyboard layout.** New
  `assets/ime/keyboard/.../characters/amharic_sera.json` ships a
  practical Amharic tap layout pre-populated with the most common
  Ge'ez radicals (the 1st-form base of each consonant). The runtime
  routes long-press → vowel-form picker via the existing FlorisBoard
  popup mechanism; the L6 `GeezSeraTransliterator` handles SERA
  input.

- **L8.1 LDML `<transforms>` parser + engine.** New
  `LdmlTransformsParser` parses Keyman LDML `<transformGroup>` →
  `<transform from="..." to="..."/>` rules using OWASP XXE-hardened
  `javax.xml`. `LdmlTransformTable.rulesByLengthDesc` exposes rules
  sorted longest-first for greedy matching. `LdmlTransformEngine`
  applies rules incrementally on each keystroke (compose-key dead-key
  semantics, ligature stacking).

- **L9.1 T9 layout JSON.** New
  `assets/ime/keyboard/.../characters/t9.json` ships a 4×3 T9 grid
  with letter popups (1: punctuation / 2: abc / 3: def / ... / 7:
  pqrs / 8: tuv / 9: wxyz / * + 0 + #). For nostalgia + small-screen
  setups.

- **L11a Espanso vars expander.** Existing `EspansoMatchParser` shape
  extended with `EspansoVar(name, type, params)` + `regex` field +
  `isWordSensitive` + `passive` flags. New `EspansoVarsExpander.expand`
  resolves `{{name}}` placeholders against four built-in var types:
  **date** (configurable `format`), **clipboard** (caller-supplied
  provider), **echo** (literal from params), **random** (semicolon-
  separated `choices`). Pluggable `nowProvider` + `randomProvider`
  callbacks make the expander deterministic-testable.

- **L12.1 Android Canvas WordStyles renderer.** New
  `WordStylesCanvasRenderer` rasterises text via `android.graphics.*`
  Canvas + Paint, supports background fill / foreground colour /
  linear gradient / shadow layer / configurable padding + font size.
  Encodes to PNG bytes ready for `InputContentInfoCompat`. Wired at
  `FlorisApplication.onCreate` so the smartbar quick-action sees a
  working renderer without any addon installed.

## NEXT-tier follow-ups

- **Next-3.1a KenLM mmap trie reader.** New `KenLmTrieReader.openMapped(path)`
  memory-maps a `.litertlm` / KenLM binary file and parses the header
  eagerly via the v1.8.0 `KenLmBinaryReader`. Lazy `bodyStartOffset`
  + `readBytesAt(offset, length)` give the upcoming per-n-gram
  lookup layer cheap absolute reads against the mapped buffer.

- **Next-5.2a Curve25519 + AES-GCM sealed-box.** New `SealedBoxCrypto.seal`
  / `open` produces a libsodium-shape sealed-box envelope
  `ephemeralPub (32 B) ‖ nonce (12 B) ‖ ciphertext+tag (n + 16 B)`.
  **Uses JVM stdlib `java.security` (X25519) + `javax.crypto`
  (AES-GCM) — zero native dependency, zero extra .so payload.**
  Forward-secret: every seal generates a fresh ephemeral keypair.
  Now wires straight into the Next-5.1 CRDT delta transport so an
  addon can encrypt deltas without taking a libsodium dep.

- **Next-7.2a Split-keyboard layout calculator.** New
  `SplitKeyboardLayoutCalculator.calculateRow(totalWidth, gutter,
  leftKeyCount, rightKeyCount)` produces `SplitRowGeometry` (left
  / gutter / right widths + per-key widths) for the renderer to
  consume. `qwertyBoundary(rowIndex, keyCount)` returns the canonical
  hand boundary for QWERTY rows (5+5 / 5+4 / 4+3).

- **Next-12.2a Roborazzi launcher-Activity unblock.** New
  `RoborazziHostActivity` (debug-manifest only, `exported=false`,
  release builds never see it) backs `createAndroidComposeRule<...>()`.
  The previously-`@Ignore`'d screenshot tests now run cleanly. The
  Roborazzi Gradle plugin still waits on the AGP-9 compat release
  (1.44.0-stable) before the `recordRoborazzi*` task surface lights
  up; until then the captures pass through Robolectric directly.

## Test infrastructure

51 net new unit tests across 9 new test classes. Suite total is now
**545** (was 494 at v1.8.1). All facades follow the
`*Registry.setActive(...)` registration pattern from Next-4.2:
heavy runtimes stay out of `:app`, behind a registry the IME reads.

## What now legitimately can't be scaffolded further

Even more aggressively pursued than v1.8.1, the remaining items all
need something specific from the outside world:

- **N1.1** HeliBoard NLnet glide library — released by Jun 2026.
- **N1.2** CleverKeys multi-script gesture model — vendor roadmap
  Q2-Q3 2026.
- **N10.1** Noto Color Emoji 17.0 — depends on `androidx.emoji2`
  1.7.0+ being published.
- **Next-2.5** Rambler streaming-voice cleanup — gates on the L1
  LLM addon being installed.

Every other ROADMAP entry now has a real scaffold + tests +
a clear adapter pattern that a follow-up runtime can fill in.
