# SwiftFloris v1.8.0 — 2026-05-15

11-item autonomous ROADMAP pass closing the remaining heavy NEXT-tier
items + the cheap-to-scaffold half of the LATER tier. **416 unit tests
at HEAD** (413 pass, 3 `@Ignore`'d pending Next-12.2a Robolectric manifest
fix). `:app:compileDebugKotlin` + `:app:assembleDebug` clean.

## NEXT-tier closure

- **Next-3.1 KenLM binary header reader.** `KenLmBinaryReader.readHeader`
  parses the 64-byte magic, `ModelType` enum, order, `FixedWidthParameters`,
  and per-order uint64 n-gram counts. Cheap probe lets the NLP pipeline
  decide whether to mmap a real KenLM trie or fall back to the existing
  bigram chain. Trie body parsing + JNI to the upstream KenLM C++ library
  moves to Next-3.1a.

- **Next-3.2 Zipf-scale subtitle-frequency overlay.** New `ZipfFrequencyTable`
  loads `assets/freq/<lang>.tsv` and blends `0.6 * scowl + 0.4 * (zipf/8.0)`
  in `LatinDictionarySnapshot.frequencyFor(word)`. Seed `en.tsv` ships
  with ~1,000 high-frequency entries (rspeer/wordfreq CC-BY-SA). Full
  SUBTLEX tables ride into a Next-10.3 dictionary-pack addon.

- **Next-4.2 stroke-recogniser facade.** New `ime/handwriting/` package:
  `Stroke` / `StrokePoint` capture pen polylines with timing, `StrokeRecognizer`
  interface returns ranked `StrokeCandidate`s. **ML Kit Digital Ink is
  intentionally not a `:app` dep** — `RemoteModelManager.download(...)`
  needs `INTERNET`, breaking §1's no-network promise; the actual ML Kit
  binding moves to a future `addons/handwriting-mlkit/` opt-in APK.

- **Next-5.1 + Next-5.2 CRDT personal-dictionary scaffold.**
  `PersonalDictionaryCrdt` with observed-add / LWW-delete semantics +
  deterministic tie-break. `merge(a, b) == merge(b, a)`, `merge(a, a) ==
  merge(merge(a, a), a)`. `PairingPayload` pins the QR-encoded JSON
  shape (Curve25519 pubkey hex validation). Automerge-rs JNI + libsodium
  sealed-box wrap ride in Next-5.1a + Next-5.2a.

- **Next-6.4 KLC (Windows) hardware-keyboard layout parser scaffold.**
  `KlcLayoutParser.parse(klcText)` consumes Microsoft Keyboard Layout
  Creator exports: BOM, comments, tab/space columns, `@`-suffix dead
  keys, `%`/`-1` no-output slots. macOS `.keylayout` parser + Android
  `InputManager` runtime mapper land in Next-6.4a + Next-6.4b.

- **Next-7.2 split-keyboard window-mode foundation.** New `Fixed.SPLIT`
  sub-mode + `ImeWindowConstraints.Fixed.Split` with 80dp default gutter
  and 600dp `minTabletWidthDp` viability check. New
  `prefs.keyboard.splitKeyboardEnabled` boolean (default off). Renderer
  key-rect distribution lands in Next-7.2a.

- **Next-9.4 custom emoji tag predict.** New `CustomEmojiTagStore` lets
  users attach personal keywords to any emoji (e.g. 🦋 → "freedom").
  Wired into `EmojiSuggestionProvider`'s parallel-stream candidate
  scoring at 0.20 weight alongside name (0.55) and bundled-keyword
  (0.25). Atomic on-disk JSON writes (`.tmp` + rename) for crash
  safety. Caps: 16 tags per emoji, 5,000 tagged emoji, 32-char tag
  length.

- **Next-10.3 dictionary-pack addon descriptor + spec.**
  `DictionaryPackDescriptor` pins the JSON schema every dictionary-pack
  addon must follow. `docs/addons/dictionary-pack-spec.md` writes up
  the full AndroidManifest + descriptor + assets contract, including
  the banned-network-permission rule. Polish (2025 baseline) data
  rides in a sibling addon-repo when the dataset extraction lands.

- **Next-11.1 M3 Expressive theme regen.** Seven new bundled themes —
  **Nord (light + dark)**, **Tokyo Night**, **Dracula**, **Catppuccin
  Mocha**, **SwiftKey Pure (M3E light + dark)** — derived from the
  well-tested `swift_slate.json` baseline so the ~500-line Snygg
  selector tree stays consistent. `--shape-chip` moves off the pill
  shape (`rounded-corner(50%)`) to 12dp corner per the no-pill-backdrop
  rule. Generated via `scripts/gen_m3e_themes.py` for reproducible
  re-runs. Theme extension manifest 0.2.0 → 0.3.0. Binds to the
  per-app accent `LocalPerAppAccent` from Next-11.3a at runtime.

- **Next-12.2 Roborazzi screenshot regression scaffold.** Roborazzi
  1.43.1 + Robolectric 4.14.1 wired as `testImplementation` deps;
  `ExtensionMaintainerChipScreenshotTest` pins three chip configurations
  via `captureRoboImage` at `xxhdpi w360dp-h640dp`. `junit-vintage-engine`
  bridges JUnit-4 Robolectric tests onto the project-wide JUnit-5
  platform alongside Kotest. Roborazzi Gradle **plugin** intentionally
  not applied (1.43.x uses the AGP `TestedExtension` API that AGP 9.0.0
  removed); flips back on when Roborazzi 1.44.0-stable lands. Tests
  `@Ignore`'d pending the Robolectric launcher-Activity manifest fix
  (Next-12.2a) — deps + harness shape are in place.

## LATER-tier scaffolds

- **L7 MCP local-LLM bridge contract.** `McpBridgeContract` pins the
  bind-time Intent action, signature-protected permission,
  `<meta-data>` keys, 4 MB payload cap. `McpToolDescriptor` mirrors
  the upstream MCP spec's `tools/list` shape. `McpToolResult` is the
  success/failure envelope. **On-device only by hard contract** —
  service binding, never a network socket.

- **L9 alt-layouts audit.** Colemak, Colemak DH, Colemak DHM, Dvorak,
  Workman are **already in tree** via the FlorisBoard upstream layout
  pack. Remaining L9 work (honeycomb-hex + T9) needs a non-rectangular
  renderer (L9.1).

- **L11 Espanso config import.** `EspansoMatchParser.parse(yaml)`
  consumes `~/.config/espanso/match/*.yml` — inline scalar / quoted /
  escaped strings, literal `|` and folded `>` block scalars with
  indent-stripping, full-line comments, silent skip of blank-trigger
  rows. Hand-rolled, < 200 lines vs Snakeyaml's 600KB+ runtime. The
  Tasker intent surface lands as L11.1.

## Test infrastructure

- 416 unit tests (was 356 at v1.7.9). New suites: `ZipfFrequencyTableTest`,
  `KenLmBinaryReaderTest`, `KlcLayoutParserTest`, `DictionaryPackDescriptorTest`,
  `PersonalDictionaryCrdtTest`, `StrokeRecognizerTest`, `EspansoMatchParserTest`,
  `McpBridgeContractTest`, `ExtensionMaintainerChipScreenshotTest` (Roborazzi,
  `@Ignore`'d).

## Outstanding (still pending real-library bring-up)

- **L1 LiteRT-LM smart-compose** — needs JNI runtime.
- **L2 Bergamot WASM NMT** — needs WASM host.
- **L3 librime CJK** — needs JNI to librime C++.
- **L4 RTL shaping** — ICU shaping pass.
- **L5 Indic transliteration** — needs language-specific tables.
- **L6 Ge'ez script** — needs Amharic/Tigrinya layout files.
- **L8 Keyman LDML importer** — XML schema parser (L8.1 is the next slice).
- **L10 WebAuthn passkey injection** — needs autofill API wiring.
- **L12 WhisperInput streaming** — covered by ongoing N2.x voice work.
