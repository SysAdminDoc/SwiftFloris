# AI Prompts — External-Work Backlog (post-v1.8.2)

Each section below is a **self-contained AI prompt** you can paste into
a fresh chat. The AI has no memory of prior work, so each prompt
includes:

- The project's root path on disk.
- The relevant scaffold file already in tree.
- What specifically needs to land.
- The acceptance criteria.
- The verify-and-commit step.

**Universal preamble** — every prompt assumes the AI follows these rules
(copy this into the chat if your local CLAUDE.md doesn't already enforce
them):

> Use the local SwiftFloris repo at `~/repos/SwiftFloris`. Conventional
> commit messages, no Co-Authored-By. Apache-2.0 only in `:app`
> (GPL/AGPL only as conceptually-borrowed reference). Surgical edits.
> Compile-verify (`./gradlew :app:compileDebugKotlin --offline`) before
> committing. Run any new unit tests via `./gradlew :app:testDebugUnitTest
> --tests "<pattern>" --offline`. Auto-commit-and-push to GitHub when
> the change is logically complete.

---

## A. Upstream-release waits

### A1 — N1.1: Wire HeliBoard NLnet open-glide engine when it releases

```
SwiftFloris is a privacy-first Android IME at `~/repos/SwiftFloris`,
forked from FlorisBoard. Currently glide typing routes through
`ime/text/gestures/GlideTypingManager` which calls a built-in
"statistical" heuristic. HeliBoard's NLnet-funded R&D project
(Jun 2025 → Jun 2026; tracked at
https://github.com/Helium314/HeliBoard/issues/2226) is building an
open-source replacement for the closed Google `swypelibs` blob.

Your task: when HeliBoard publishes their open-glide library:
1. Add a Maven coordinate (or git submodule under `lib/glide/`,
   whichever HeliBoard publishes) for the new library.
2. Add a new `GlideEngine.HeliboardOpen` enum value to
   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/`
   that calls into the new library.
3. Surface a preference `prefs.glide.engine` (HELIBOARD_OPEN |
   SWIFTFLORIS_STATISTICAL) under Settings → Glide typing.
4. Default to HELIBOARD_OPEN when present, fall back to the existing
   statistical engine otherwise.
5. Write a unit test that round-trips the preference value through the
   datastore.
6. Commit + push with message
   "Wire N1.1 — HeliBoard open-glide engine ($VERSION)".

Acceptance: typing a glide on a connected device produces sensible
output via the new engine; toggling to statistical falls back to the
heuristic; tests pass.
```

---

### A2 — N1.2: Port CleverKeys multi-script architecture

```
SwiftFloris at `~/repos/SwiftFloris` ships a statistical glide-typing
engine. CleverKeys (GPL-3.0,
https://github.com/CleverKeysOrg/CleverKeys, code can't be linked but
architecture is open) targets a Q2-Q3 2026 release of a multi-layout /
multi-script ONNX transformer gesture model (sub-200ms on Pixel 7).

Your task: once CleverKeys releases their multi-script encoder/decoder
ONNX artifacts:
1. Create `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/
   gesture/CleverKeysGestureEngine.kt` that loads the ONNX model via
   ONNX Runtime Mobile (`com.microsoft.onnxruntime:onnxruntime-android`).
2. The model + its tokenizer ships as a separate `addons/gesture-
   cleverkeys/` APK (~12 MB) the user installs explicitly; addon
   registers itself via `GlideEngineRegistry.setActive` (mirrors
   `StrokeRecognizerRegistry` from `ime/handwriting/`).
3. Add unit tests that load a tiny dummy ONNX model and verify the
   encoder/decoder beam-search pipeline returns ranked candidates.
4. Train your own Apache-2.0 model from the N1.1 dataset once HeliBoard
   releases it — track that as Next-N1.2a.

Acceptance: addon-driven gesture recognition outperforms the
statistical fallback on a 1,000-trace eval set; falls back gracefully
when no addon is installed.
```

---

### A3 — N10.1: Adopt Noto Color Emoji 17.0

```
SwiftFloris at `~/repos/SwiftFloris` currently bundles
`androidx.emoji2 = "1.6.0"` (Emoji 15.1 / Unicode 15.1). Unicode 17
emoji (Distorted Face, Fight Cloud, Hairy Creature, Orca, Landslide,
Trombone, Treasure Chest) need to ship.

Two paths — take whichever has landed first:

**Path A — wait for androidx.emoji2 1.7.0:** Bump the version in
`gradle/libs.versions.toml`, regenerate `assets/ime/keyboard/
org.florisboard.emojis/emojis.csv` from
https://unicode.org/Public/emoji/17.0/emoji-test.txt, update
`EmojiCategoryHelper` if any new category landed in 17.0.

**Path B — bundle NotoColorEmoji.ttf v17 directly:** Place the v17
TTF asset at `app/src/main/assets/fonts/NotoColorEmoji-17.0.ttf`,
update `FlorisEmojiCompat` to use `BundledEmojiCompatConfig` with that
asset (instead of `DefaultEmojiCompatConfig.create()`).

Track upstream emoji2 releases:
https://developer.android.com/jetpack/androidx/releases/emoji2

Acceptance:
- All v17 emoji render in the palette (no missing-glyph squares).
- `EmojiSearchTest` finds new entries by name.
- APK size delta documented in `docs/SIZE_AUDIT.md`.
- Commit + push: "Adopt Unicode 17.0 emoji (path A|B)".
```

---

### A4 — Next-12.2 plugin: Re-enable Roborazzi Gradle plugin

```
SwiftFloris at `~/repos/SwiftFloris` has Roborazzi 1.43.1 wired as a
test dependency but **not** as a Gradle plugin — the 1.43.x line calls
the AGP `TestedExtension` API that AGP 9.0.0 removed. See the comment
at `app/build.gradle.kts:30` and ROADMAP §7 Next-12.2.

Your task: when Roborazzi 1.44.0-stable (or later AGP-9-compatible
release) ships:
1. Bump `roborazzi = "1.44.0"` in `gradle/libs.versions.toml`.
2. Uncomment `alias(libs.plugins.roborazzi)` in `app/build.gradle.kts`.
3. Verify `:app:recordRoborazziDebug` + `:app:verifyRoborazziDebug` +
   `:app:compareRoborazziDebug` tasks light up.
4. Update `ExtensionMaintainerChipScreenshotTest` to use the
   plugin-generated record/verify flow (consult Roborazzi 1.44 docs).
5. Add baseline PNGs to `app/src/test/snapshots/extension_maintainer_
   chip/` and commit them so CI can verify against them on every PR.
6. Add `:app:verifyRoborazziDebug` to the GitHub Actions workflow at
   `.github/workflows/android.yml`.
7. Commit + push: "Re-enable Roborazzi 1.44 — screenshot CI gate".

Acceptance: pushing a commit that changes any Compose surface used in
the screenshot tests produces a `verifyRoborazzi` failure in CI; the
baseline PNGs render the chip in three configurations
(name-only, name+email, name+url).
```

---

## B. Out-of-tree addon APKs (heavy native runtime)

### B1 — L1.1a: LiteRT-LM smart-compose addon

```
SwiftFloris at `~/repos/SwiftFloris` has a smart-compose facade
(`ime/smartcompose/SmartComposeProvider`) waiting for a real LiteRT-LM
backend. The IME's base APK deliberately does NOT depend on LiteRT-LM
because RemoteModelManager.download(...) requires INTERNET, which
breaks the §1 no-network promise on the base APK.

Your task: build a sibling addon project at `~/repos/SwiftFloris-
addons/smart-compose-litert/`:
1. Standalone Gradle/AGP project, applicationId
   `dev.patrickgold.florisboard.addon.smartcompose.litert`.
2. AndroidManifest declares the
   `dev.patrickgold.florisboard.action.REGISTER_ADDON` receiver with
   `addon.type = "smartcompose-provider"` (extend `AddonType` enum in
   the IME repo to add this — separate small commit).
3. Bundle the LiteRT-LM `.so` libs (CPU + GPU backends) and a Gemma 3
   1B int4 (`gemma-3-1b-it-q4_k_m.litertlm`) model file in assets.
4. Provide a `LiteRtSmartComposeProvider` class that subclasses
   `SmartComposeProvider` and uses
   `com.google.ai.edge.litertlm:litertlm-android` to load + run the
   model.
5. On `BootCompletedReceiver`, register the provider via
   `SmartComposeProviderRegistry.setActive(...)` (use an exported
   ContentProvider as a tiny shim so the IME-side enumerator can
   discover the addon).
6. Battery-aware gating: when battery < 30% AND not charging, return
   `NoSuggestion` regardless of model output.
7. Output APK should be ~ 25-40 MB. Tag as v0.1.0.

Reference: https://github.com/google/litert-lm

Acceptance: install both APKs; typing a word in any editor shows
gray ghost-text continuation that auto-accepts on space.
```

---

### B2 — L2.1a: Bergamot WASM inline-translator addon

```
SwiftFloris at `~/repos/SwiftFloris` has an inline-translator facade
(`ime/translate/InlineTranslator`) waiting for a real on-device NMT
backend. Mozilla's Bergamot project (MPL-2.0; the same translator
Firefox ships locally) is the target.

Your task: build a sibling addon at `~/repos/SwiftFloris-
addons/translator-bergamot/`:
1. Embed the Bergamot Marian WASM runtime (https://github.com/
   browsermt/bergamot-translator) — Android WebView host or
   wasm-mobile JNI.
2. Bundle these initial model pairs (compressed to ~17 MB each):
   en→es, es→en, en→fr, fr→en, en→de, de→en. Models from
   https://huggingface.co/Mozilla/translations-models.
3. `BergamotInlineTranslator implements InlineTranslator`. Register
   via `InlineTranslatorRegistry.setActive(...)`.
4. Build a Settings → Translation screen in the IME (separate small
   commit in the IME repo) that lists installed pairs + shows
   download stubs that route to the addon's per-pair download UI
   (addon handles network, never the base APK).
5. Add a smartbar quick-action "Translate selection" that calls
   `translate(selection, sourceLocale, targetLocale)` and surfaces
   the result in a preview row.

Acceptance: install the addon; selecting English text + tapping the
translate action shows the Spanish translation; works offline after
the per-pair download completes.
```

---

### B3 — L3.1 + L3.2: librime CJK addon + candidate-row UI

```
SwiftFloris at `~/repos/SwiftFloris` has a CJK input facade
(`ime/cjk/CjkInputProvider`) with 9 schema slots (Pinyin Simplified +
Traditional, Jyutping, Zhuyin, Cangjie 5, Wubi 86, Quick / double-
pinyin Xiaohe, Japanese Mozc, Korean Jamo). librime is the de-facto
open IM engine (BSD-3).

Your task — two parallel parts:

**Part 1 (addon):** Build `~/repos/SwiftFloris-addons/cjk-librime/`:
1. Cross-compile librime for Android (arm64-v8a + x86_64) using the
   NDK 29 toolchain. Reference fcitx5-android's wrapper:
   https://github.com/fcitx5-android/fcitx5-android (Apache-2.0 —
   conceptual borrow only).
2. Bundle the canonical schema YAMLs (luna_pinyin, luna_pinyin_tw,
   jyutping, bopomofo, cangjie5, wubi86) + the corresponding
   dictionary files from https://github.com/rime/plum.
3. Implement `LibrimeCjkInputProvider : CjkInputProvider` calling
   into librime's JNI bridge.

**Part 2 (IME repo):** Implement the candidate-row Compose UI:
1. `app/src/main/kotlin/dev/patrickgold/florisboard/ime/cjk/
   CjkCandidateRow.kt` — horizontal LazyRow of `CjkCandidate`s
   rendered with annotation underneath, selectable by tap or
   number-row key (`1`..`9`).
2. Wire into `Smartbar` via a new `SmartbarPrimaryActionType.CJK_CANDIDATES`.
3. Routing: when the active subtype's primary script is CJK, the
   smartbar switches to `CjkCandidateRow` instead of the usual
   suggestion strip.

Acceptance: typing `nihao` on a Pinyin Simplified subtype shows
`你好` `nǐ hǎo` annotated in the candidate row; tap to commit.
```

---

### B4 — L3.3: Japanese (mozc) + Korean (jamo) addons

```
SwiftFloris at `~/repos/SwiftFloris` has CJK facade slots for
`JAPANESE_MOZC` and `KOREAN_JAMO` schemas. FUTO ships both — that's
the design reference, not a code donor.

Build two sibling addons:

**~/repos/SwiftFloris-addons/cjk-mozc-japan/** —
1. Cross-compile mozc (BSD-3) for Android.
2. Bundle the standard Mozc Japanese dictionary.
3. Implement `MozcCjkInputProvider : CjkInputProvider` supporting
   `CjkSchema.JAPANESE_MOZC` only.

**~/repos/SwiftFloris-addons/cjk-hangul-korean/** —
1. Pure-Kotlin Hangul Jamo 2-bul converter — no native dep needed for
   Korean (the orthography is fully algorithmic from initial /
   medial / final phonemes). Use the Unicode Hangul composition
   algorithm directly.
2. Implement `JamoCjkInputProvider : CjkInputProvider` supporting
   `CjkSchema.KOREAN_JAMO`.

Both addons register via the same enrolment receiver pattern as the
existing addons.

Acceptance: typing `konnitiha` on a Japanese subtype produces
`こんにちは`; typing `dkssudgktpdy` on a Korean subtype produces
`안녕하세요`.
```

---

### B5 — L10: Credential Manager passkey addon

```
SwiftFloris at `~/repos/SwiftFloris` has a passkey facade
(`ime/passkey/PasskeyAdapter`) + a detector
(`PasskeyFieldDetector.detect`) that fires when `EditorInfo.extras`
carries WebAuthn `rpId` + `challenge`. The Android Credential Manager
API is Activity-bound — can't live in the IME service.

Build `~/repos/SwiftFloris-addons/passkey-adapter/`:
1. Standalone APK with a `PasskeyCeremonyActivity` (no UI; finishes
   immediately after ceremony) + a `PasskeyAdapterImpl :
   PasskeyAdapter`.
2. `hasPasskeyFor(rpId)` queries Credential Manager via
   `androidx.credentials:credentials` 1.4.0+ for a credential matching
   the rpId.
3. `requestAssertion(rpId, challenge)` launches the activity, calls
   `CredentialManager.getCredential(this, GetCredentialRequest(
   PublicKeyCredentialOption(...)))`, parses the resulting
   `PublicKeyCredential` JSON, returns a `PasskeyAssertionRequest`.
4. Addon registers itself with the IME's
   `PasskeyAdapterRegistry.setActive(...)` via the standard addon
   enrolment receiver pattern.

In the IME repo, also:
5. Add a `Smartbar` chip slot for "Use passkey" that becomes visible
   only when `PasskeyFieldDetector.detect(...)` returns non-null AND
   `PasskeyAdapterRegistry.active.hasPasskeyFor(rpId)` is true.
6. Tap on the chip → call `requestAssertion` → on success, commit
   the assertion via `commitContent(InputContentInfoCompat(...))`.

Acceptance: focus a WebAuthn-enabled field (Bitwarden, GitHub) on
a passkey-registered device; the "Use passkey" chip appears; tap it
to drive the ceremony.
```

---

### B6 — Next-4.2a: ML Kit Digital Ink stylus-handwriting addon

```
SwiftFloris at `~/repos/SwiftFloris` has a stroke-recogniser facade
(`ime/handwriting/StrokeRecognizer`) waiting for a real recogniser.
Google ML Kit Digital Ink Recognition is the standard target, but
its `RemoteModelManager.download(...)` requires INTERNET which the
base APK can't take.

Build `~/repos/SwiftFloris-addons/handwriting-mlkit/`:
1. Standalone APK with `com.google.mlkit:digital-ink-recognition`
   1.1.0+ dependency.
2. `MlKitStrokeRecognizer : StrokeRecognizer`. Per-locale model
   downloads via `RemoteModelManager.getInstance().download(model,
   DownloadConditions.Builder().build())`.
3. **Model download routing:** open a Settings → Handwriting screen
   in the addon that lists installed models + allows new-language
   download. NEVER auto-download. NEVER touch network without user
   tap.
4. Register via `StrokeRecognizerRegistry.setActive(...)`.

In the IME repo:
5. Flip `prefs.keyboard.stylusHandwritingEnabled` default to **on**
   when the addon is enrolled (otherwise stay off).
6. Add Next-4.3a per-subtype refinement: only enable handwriting on
   subtypes whose primary locale has an installed model.

Acceptance: stylus stroke on a Pixel Tablet produces recognised text
in the smartbar candidate row; falls back gracefully when no addon.
```

---

## C. Dataset extractions + dictionary packs

### C1 — Next-3.2 full SUBTLEX overlay

```
SwiftFloris at `~/repos/SwiftFloris` ships ~1,000 Zipf frequencies each
at `app/src/main/assets/freq/{en,cs,de,es,fr,it,pt}.tsv` as seed tables.
Build the full SUBTLEX-extracted overlays for the top release languages.

Your task:
1. Clone https://github.com/rspeer/wordfreq locally.
2. For each of EN / DE / ES / FR / PT / IT (and CS if a compatible
   source table is available), extract the SUBTLEX Zipf
   table (rspeer/wordfreq calls these `subtlex_<lang>.large`) to a
   TSV file matching the `word<TAB>zipf` format the existing
   `ZipfFrequencyTable.parse` consumes.
3. Each table targets ~75k entries — too large to bundle in the base
   APK. Move them to a new dictionary-pack addon at
   `~/repos/SwiftFloris-addons/dictionary-pack-zipf/` following the
   spec at `docs/addons/dictionary-pack-spec.md`.
4. The addon's `DictionaryPackDescriptor` lists one language entry per
   full table.
5. Verify by running `:app:testDebugUnitTest --tests
   "*LatinDictionaryStore*"` against a synthetic asset reader that
   returns the new tables: `frequencyFor("okay")` should be > 0
   (Zipf-only fallback) and `frequencyFor("the")` should blend to
   ~ 0.6*scowl + 0.4*1.0 = > 0.9.

Acceptance: tests pass; addon APK size stays small enough for optional
dictionary-pack distribution (plain text TSV gzip-compresses well).
```

---

### C2 — Next-3.1b: KenLM trie body parser

```
SwiftFloris at `~/repos/SwiftFloris` has a KenLM header reader
(`ime/nlp/kenlm/KenLmBinaryReader`) + a mmap trie reader
(`KenLmTrieReader`) that opens a `.litertlm` / KenLM binary file but
doesn't yet decode the trie *body*.

Your task: implement Bhiksha-encoded next-pointer decoding + quantised
probability/backoff table reads.

Reference implementation: https://github.com/kpu/kenlm — specifically
`lm/trie.hh`, `lm/quantize.hh`, and `lm/bhiksha.hh`. The
quantization-table layout and the Bhiksha (compressed integer)
encoding are documented in the KenLM paper:
https://kheafield.com/professional/avenue/kenlm.pdf §4.

Implement:
1. `KenLmTrieReader.score(words: List<String>): Float` returns the
   log-probability of the n-gram in the model's units.
2. `KenLmTrieReader.predict(prefix: List<String>, topK: Int): List<
   Pair<String, Float>>` returns the most likely next tokens.
3. Validate output matches the upstream `query` reference tool to
   within 1e-3 on a small Hungarian model from
   https://huggingface.co/edugp/kenlm.
4. Add 12+ unit tests pinning the trie navigation invariants.
5. Wire `LatinLanguageProvider` to consult KenLM scores as the
   secondary signal after the existing bigram chain when a KenLM
   model is enrolled.

Alternative path: JNI to the upstream KenLM C++ library. Faster to
ship, larger APK delta. Document the tradeoff in the commit message.

Acceptance: `KenLmReader.score("the cat sat on the mat".split(" "))`
returns a sensible log-prob; predictions for `["the", "cat"]` rank
common continuations on top.
```

---

### C3 — Next-10.3 Polish dictionary addon

```
SwiftFloris at `~/repos/SwiftFloris` has the dictionary-pack addon
spec at `docs/addons/dictionary-pack-spec.md` and the descriptor
parser at `ime/addon/DictionaryPackDescriptor`. The Polish dataset
itself needs to ship in a sibling addon.

Your task: build `~/repos/SwiftFloris-addons/dictionary-pack-polish/`:
1. Standalone Android APK with `hasCode=false` (data-only addon).
2. Extract a Polish word-frequency list from:
   - OpenSubtitles 2024 PL corpus
     (https://opus.nlpl.eu/OpenSubtitles.php).
   - Wiktionary PL frequency list
     (https://pl.wiktionary.org/wiki/Wikipedia:Listy_frekwencyjne).
3. Merge into a single `.fldic` file at
   `assets/ime/dict/pl.fldic` (FlorisBoard frequency-dict format —
   see existing en.fldic shape; `[words]` section with `word<TAB>
   score` lines + optional `[ngrams]` section).
4. Generate a Zipf `.tsv` overlay at `assets/freq/pl.tsv`.
5. Write a `dict_descriptor.json` matching the spec at
   `docs/addons/dictionary-pack-spec.md` (language: "pl",
   wordCount: ~320k, fldicAssetPath, zipfAssetPath, source =
   "OpenSubtitles 2024 + Wiktionary", license = "CC-BY-SA-4.0").
6. AndroidManifest declares the
   `dev.patrickgold.florisboard.action.REGISTER_DICTIONARY_PACK`
   receiver with the descriptor in `meta-data`.

Acceptance: install the addon; `LatinDictionaryStore.dictionaryFor
Language("pl")` loads > 250k words; typing in a Polish-subtype editor
produces sensible auto-correct + completion candidates.
```

---

### C4 — L4.2 Nastaliq font bundle

```
SwiftFloris at `~/repos/SwiftFloris` has the Persian/Urdu normaliser
(`ime/bidi/PersianUrduNormalizer`) but no Nastaliq font asset, so
Urdu text renders in fallback Naskh.

Your task:
1. Download Noto Nastaliq Urdu from
   https://fonts.google.com/noto/specimen/Noto+Nastaliq+Urdu
   (OFL-1.1 license — Apache-compatible attribution).
2. Bundle as `app/src/main/assets/fonts/NotoNastaliqUrdu-Regular.ttf`
   (~480 KB).
3. In the Snygg theme stylesheets (every `*.json` under
   `assets/ime/theme/.../stylesheets/`), add a Urdu-locale-specific
   `key[locale="ur"]` selector that sets `font-family:
   "Noto Nastaliq Urdu"`.
4. Register the bundled font with `Typeface.createFromAsset` at app
   boot via `FlorisApplication.onCreate`.
5. Add a unit test asserting the font asset is present + non-empty.
6. Document in `docs/RTL.md` (new file).

Acceptance: switching to an Urdu subtype + typing Urdu text renders
in Nastaliq positional shapes; English text in the same paragraph
stays in the default sans-serif.
```

---

## D. In-IME integration work (no external dep, just heavy)

### D1 — Next-2.5: Rambler streaming-voice cleanup

```
SwiftFloris at `~/repos/SwiftFloris` has streaming voice transcription
plumbing (`ime/voice/StreamingVoiceTranscriptBuffer`) and a
smart-compose facade (`ime/smartcompose/SmartComposeProvider`).
Gboard's "Rambler" (Android 17) lets you hold the mic, ramble freely,
and emits cleaned polished text on release.

Gated on: a real `SmartComposeProvider` being registered (L1.1a addon
shipped).

Your task:
1. New `prefs.voice.ramblerCleanupEnabled` boolean (default off,
   surfaced under Settings → Voice).
2. New `StreamingVoiceTranscriptBuffer.cleanUpOnRelease(text: String):
   String` method that:
   a. Passes [text] through
      `SmartComposeProviderRegistry.active.predictNextTokens(...)`
      with a "rewrite-this-rambling-transcript" instruction prompt.
   b. Falls back to identity (returning the raw transcript) when no
      provider is bound or the provider returns NoSuggestion.
3. Wire into `VoiceInputManager.consumeStreamingChunk` so the
   final-chunk path runs cleanup before committing to the editor.
4. Add property-based tests: cleanup must preserve named entities,
   must not lengthen the text by more than 1.5×, must produce a
   string ending in `.` `!` or `?`.
5. Document in `docs/VOICE_INPUT.md`.

Acceptance: holding the mic + saying "ok so um like the meeting is at
three uh thirty actually four pm tomorrow" → output is
"The meeting is at 4 PM tomorrow." (or similar polished form).
```

---

### D2 — Next-5.1a: Automerge-rs JNI bring-up

```
SwiftFloris at `~/repos/SwiftFloris` has a personal-dictionary CRDT
(`ime/sync/PersonalDictionaryCrdt`) using a hand-rolled observed-add
/ LWW-delete merger (good enough for typed words). Real Automerge
gives full CRDT semantics + the standard wire format.

Your task:
1. Add an Automerge-rs JNI binding under `lib/automerge/` (or as a
   prebuilt artifact from https://github.com/automerge/automerge-rs).
2. Replace `PersonalDictionaryCrdtMerger.merge` with the Automerge
   JSON-CRDT merge.
3. Keep the existing data class API; only the merge algorithm
   changes.
4. Re-run all existing `PersonalDictionaryCrdtTest` invariants
   (commutativity, idempotency, tombstone-vs-entry resolution); they
   should still pass.
5. Add three-device convergence test using property-based generators
   from kotest-property.
6. Document in `docs/CRDT_SYNC.md`.

Acceptance: existing tests still green; three-device cluster
converges to the same `Set<EntryKey>` regardless of merge order.
```

---

### D3 — Next-5.3a: Settings → Sync Compose screen

**Status:** Shipped 2026-05-15. Preserved below as the historical implementation prompt.

```
SwiftFloris at `~/repos/SwiftFloris` has a sync-channel taxonomy
(`ime/sync/SyncChannel`) + the QR-pairing payload
(`ime/sync/PairingPayload`). What's missing is the Compose UI.

Your task:
1. Create `app/src/main/kotlin/dev/patrickgold/florisboard/app/
   settings/sync/SyncSettingsScreen.kt` rendering:
   - A SyncChannel picker (Syncthing folder name input / LocalFolder
     SAF document-tree picker / ManualExport file picker /
     Disabled).
   - A "Pair a new device" button that calls
     `PairingPayloadGenerator.generate()` and renders the result as a
     QR code via `zxing-android-embedded`.
   - A "Receive pairing" button that opens the camera scanner +
     parses the scanned QR back through `PairingPayload.parse`.
   - A "Paired devices" LazyColumn showing every device the user is
     synced with.
2. Wire into `Routes.Settings.Sync` and add to the Settings nav graph.
3. Strings live in `app/src/main/res/values/strings.xml` (`settings__
   sync__*` namespace).
4. **NO network code** — the IME never directly hits a sync server.
   The user is expected to install Syncthing / Nextcloud / etc. and
   point them at the shared folder.

Acceptance: opening Settings → Sync shows the channel picker; a
two-device pairing can be completed end-to-end without typing the
recipient's pubkey by hand.
```

---

### D4 — Next-7.1a: Floating-mode onboarding tooltip

```
SwiftFloris at `~/repos/SwiftFloris` has the `prefs.keyboard.
startInFloatingMode` toggle (Settings → Keyboard) but no first-launch
tooltip to teach users about the drag-handle + pinch-resize controls.

Your task:
1. New `prefs.keyboard.floatingOnboardingShown` boolean (default
   false).
2. When `ImeWindowController.startSession` transitions into
   FLOATING mode AND `floatingOnboardingShown == false`, overlay a
   Compose tooltip ("Drag the handle to move; pinch the corner to
   resize.") using the existing `patrickgold-compose-tooltip`
   dependency.
3. Tooltip auto-dismisses after 4 s OR on user tap.
4. After first display, set `floatingOnboardingShown = true` so it
   never reappears.
5. Add a Reset-onboarding button at Settings → Keyboard for testing.

Acceptance: first-ever floating-mode entry on a fresh install shows
the tooltip; subsequent entries don't.
```

---

### D5 — Next-7.2: Split-keyboard renderer wire-up

```
SwiftFloris at `~/repos/SwiftFloris` has the split-keyboard
preference (`prefs.keyboard.splitKeyboardEnabled`), the window-mode
sub-mode (`ImeWindowMode.Fixed.SPLIT`), the constraints class
(`ImeWindowConstraints.Fixed.Split`), and the layout calculator
(`SplitKeyboardLayoutCalculator.calculateRow`). What's missing is the
actual renderer + touch-hit integration.

Your task:
1. In `ime/text/keyboard/TextKeyboardLayout.kt`, when the active
   window mode is `Fixed.SPLIT` AND `ImeWindowConstraints.Fixed.Split
   .isViable` returns true, emit per-key rectangles using the layout
   calculator's output.
2. Insert a fixed-width gutter spacer in each row between the left
   half and the right half.
3. In `ime/text/keyboard/KeyboardManager.handleKeyEvent`, ensure
   touch hit-testing uses the new per-side rectangles (route via the
   same row's geometry).
4. Add a unit test that the split layout's total width equals the
   non-split layout's total width on the same constraints.
5. Document in `docs/SPLIT_KEYBOARD.md`.

Acceptance: enabling `prefs.keyboard.splitKeyboardEnabled` on a
tablet with width ≥ 600dp renders the split layout; touches in the
gutter region don't fire any key.
```

---

### D6 — Next-9.4a: Pinned-groups palette row

```
SwiftFloris at `~/repos/SwiftFloris` has the emoji pin-group store
(`ime/media/emoji/EmojiPinGroupStore`) but no palette UI for it.

Your task:
1. Add a new `EmojiCategory.PINNED_GROUPS` enum value (at the top of
   `ime/media/emoji/EmojiCategory.kt`) — wired into the palette like
   the existing RECENTLY_USED category.
2. In `EmojiPaletteView`, when the user selects PINNED_GROUPS, render
   a LazyColumn of named group rows, each row a horizontal LazyRow of
   the pinned emoji.
3. Long-press on any emoji in any category → bottom-sheet "Pin to
   group…" picker. New group name input creates a fresh group.
4. Long-press on a group name → "Rename group / Remove group" sheet.
5. Tap-to-insert behaves like any other emoji palette cell.
6. Strings under `emoji__pinned_groups__*` in `strings.xml`.

Acceptance: user can create a "Birthday" group with 🎂 🎉 🎁, switch
to the Pinned Groups tab, and tap any pinned emoji to commit.
```

---

### D7 — Next-12.1: Macrobenchmark numbers

```
SwiftFloris at `~/repos/SwiftFloris` has Macrobenchmark wiring at
`benchmark/src/main/kotlin/dev/patrickgold/florisboard/benchmark/
KeyboardLatencyBenchmark.kt`. The harness defines four benchmarks
(`imeFirstRender`, `suggestionStripRecomposition`, `dictionaryColdLoad`,
`themeSwitch`) but has never been run on a clocks-locked device.

Your task:
1. On a Pixel 6 (or a Pixel 6+ if available) with clocks locked
   (`adb shell cmd device_config put activity_manager
   max_phantom_processes 2147483647 && ... ` etc; reference
   https://developer.android.com/topic/performance/benchmarking/
   macrobenchmark-overview), run:
   `./gradlew :benchmark:connectedBenchmarkAndroidTest`.
2. Collect the JSON output from `benchmark/build/outputs/connected_
   android_test_additional_output/.../KeyboardLatencyBenchmark.json`.
3. Write `docs/BENCHMARKS.md` with a markdown table of the four
   benchmarks × `medianFrameDurationCpuMs`,
   `medianFrameOverrunMs`, `traceSection.swiftfloris.*.minMs/medMs/
   p95Ms`.
4. Commit the JSON as `docs/benchmark-results/baseline-2026-05-15.json`
   so subsequent runs can compare against the baseline.
5. Add a section to release notes: "Latency baseline established".

Acceptance: `docs/BENCHMARKS.md` exists with real numbers; future
ROADMAP entries can reference + improve them.
```

---

### D8 — L7.1-7.4: AIDL MCP daemon + IME client

```
SwiftFloris at `~/repos/SwiftFloris` has the MCP bridge contract
(`ime/mcp/McpBridgeContract`) — Intent action, signature-protected
permission, meta-data keys, payload cap, tool-descriptor + tool-result
data classes. What's missing is the AIDL surface + the IME-side
client.

Your task:
1. Define the AIDL interface at `app/src/main/aidl/dev/patrickgold/
   florisboard/mcp/IMcpDaemon.aidl` with methods:
   - `List<McpToolDescriptor> listTools()`
   - `McpToolResult callTool(String name, String paramsJson)`
2. Implement `ime/mcp/McpClient.kt` that binds via `bindService` to a
   daemon advertising `ACTION_BIND_MCP_DAEMON`, holds the binder, and
   exposes a Flow<List<McpToolDescriptor>>.
3. Extend `AddonEnumerator` to discover MCP daemons (new
   `AddonType.MCP_DAEMON` enum value).
4. Build a reference daemon at `~/repos/SwiftFloris-addons/mcp-
   daemon-sample/` that exposes one trivial tool ("echo") so the
   client surface can be end-to-end tested.
5. Add UI: Settings → Tools showing every enrolled daemon's tool
   catalog + an "Invoke" button.

Acceptance: install the sample daemon; Settings → Tools lists the
"echo" tool; tapping Invoke + entering a payload returns the
echoed text via the IME's preview row.
```

---

### D9 — L8.2: LDML `<displays>` parser extension

**Status:** ✅ Shipped 2026-05-15. Preserved below as historical implementation prompt.

```
SwiftFloris at `~/repos/SwiftFloris` has a Keyman LDML parser at
`ime/hardware/KeymanLdmlParser` covering `<keys>`. Many real Keyman
keyboards (Khmer, Lao, Tibetan, Burmese) also use the LDML
`<displays>` element to override the visual glyph hint on a key
while keeping a different output character.

Reference: https://www.unicode.org/reports/tr35/tr35-keyboards.html#displays

Your task:
1. Extend `HardwareKeyEntry` with a new `displayLabel: String?` field
   (default null).
2. Extend `KeymanLdmlParser.parseInternal` to read `<displays>` →
   `<display>` elements and populate `displayLabel` on the matching
   key's entry.
3. The display value uses `to=` / `display=` per the LDML spec.
4. Add 5+ unit test cases including Burmese (Myanmar) test fixture.
5. Make sure the existing `KeymanLdmlParserTest` still passes
   unchanged.

Acceptance: a test Khmer LDML keyboard's display strings round-trip
correctly through the parser into `HardwareKeyEntry.displayLabel`.
```

---

### D10 — L9.2: Honeycomb hex layout + new renderer

```
SwiftFloris at `~/repos/SwiftFloris` ships rectangular-key layouts.
Typewise's CES-winning honeycomb hex layout uses tessellated hexagons
for shorter thumb travel between common letters. T9 (4×3 already
shipped at v1.8.2) covers the small-screen / phone-pad use case;
honeycomb covers the ergonomics axis.

Your task:
1. Design a 7×4 (or similar) hexagonal letter layout — start from the
   public Typewise paper or invent a layout optimised for English
   bigram frequency.
2. Ship the layout JSON at
   `app/src/main/assets/ime/keyboard/.../characters/honeycomb.json`
   with a new key shape attribute (`shape: "hexagonal"`).
3. Extend the Snygg stylesheet selectors to handle the new shape
   variant (the `key { shape: "..." }` declaration today supports
   `rounded-corner(...)` and `circle()` — add `hexagon()`).
4. In `ime/text/keyboard/TextKeyboardLayout`, when a row contains
   hex-shape keys, emit a tessellated row offset by half a key
   width every other row.
5. Hit-testing in `KeyboardManager` uses point-in-hexagon math
   instead of point-in-rect.
6. Add a Kotest property-based test that every pixel inside any
   key's hex maps unambiguously to exactly one key.

Acceptance: honeycomb selectable in subtype settings; typing on it
produces correct output across the tessellation pattern.
```

---

### D11 — L11.1: Tasker receiver wire-up

**Status:** Shipped 2026-05-15. Preserved below as the historical implementation prompt.

```
SwiftFloris at `~/repos/SwiftFloris` has the Tasker intent contract
(`ime/tasker/TaskerIntentContract`) with four actions
(INSERT_TEXT / INSERT_CLIP / SWITCH_LAYOUT / TRIGGER_VOICE) +
extras-schema validators. What's missing is the actual
BroadcastReceiver wired into the AndroidManifest.

Your task:
1. Create `ime/tasker/TaskerActionReceiver.kt` extending
   `BroadcastReceiver`. `onReceive`:
   - Calls `TaskerIntentContract.validate(action, extras)`.
   - Rejects with `flogError` + early return on ValidationResult.Reject.
   - Dispatches the accepted action to `KeyboardManager` actions
     (insert text / paste clip / switch layout / trigger voice).
2. Declare the receiver in `app/src/main/AndroidManifest.xml` with:
   ```xml
   <receiver android:name=".ime.tasker.TaskerActionReceiver"
             android:enabled="true"
             android:exported="true"
             android:permission="dev.patrickgold.florisboard.permission.REGISTER_ADDON">
       <intent-filter>
           <action android:name="swiftfloris.action.INSERT_TEXT"/>
           <action android:name="swiftfloris.action.INSERT_CLIP"/>
           <action android:name="swiftfloris.action.SWITCH_LAYOUT"/>
           <action android:name="swiftfloris.action.TRIGGER_VOICE"/>
       </intent-filter>
   </receiver>
   ```
3. Document in `docs/TASKER_INTEGRATION.md` — example Tasker scenes
   (e.g. "switch to Dvorak when at home", "trigger voice input on
   shake gesture").
4. Add a Robolectric test (now that the launcher-Activity manifest
   fix is in) that dispatches each intent type and verifies the
   corresponding `KeyboardManager` action fires.

Acceptance: from an `adb shell` on the connected device:
`adb shell am broadcast -a swiftfloris.action.INSERT_TEXT --es text
"Hello"` inserts "Hello" into the focused field (when SwiftFloris is
the active IME).
```

---

## Operating notes

- **Pick one item at a time.** Each prompt is self-contained — don't
  paste multiple into the same chat unless they share a sub-system
  (e.g. all B-tier addon prompts).
- **Addons live in sibling repos.** The base `SwiftFloris` repo stays
  lean; addons live at `~/repos/SwiftFloris-addons/<addon-name>/` and
  are each their own Android project.
- **Update the ROADMAP entry** in the same PR that closes an item —
  swap "scaffold" / blocker text for the ✅ shipped line + date.
- **Tag a new release** after a meaningful batch lands (single item
  releases are fine — see how v1.8.0 / 1.8.1 / 1.8.2 are organised).
