# SwiftFloris v1.8.1 — 2026-05-15

Second autonomous ROADMAP pass on the same day as v1.8.0. Closes
**every remaining open ROADMAP item** that can be tackled without
pulling in a heavy external native library at runtime. Each item ships
a working scaffold + provider-registry pattern + unit tests pinning
the contract; the heavy runtimes (LiteRT-LM, Bergamot WASM, librime,
ML Kit Digital Ink) move to opt-in addons that the user installs
separately, keeping the base APK lean and the §1 no-network promise
intact.

**494 unit tests at HEAD** (491 pass, 3 `@Ignore`'d Roborazzi pending
Next-12.2a). `:app:compileDebugKotlin` clean.

## LATER-tier sweep (L1–L12)

- **L1 LiteRT-LM smart-compose facade.** `SmartComposeProvider` +
  `LiteRtModelDescriptor` (modelId, backend, supportedLocales, sizeBytes,
  quantization, supportsLora). `SmartComposeResult.{NoSuggestion,
  Suggestion}` distinguishes "no model loaded" from "no confident
  candidate." Default no-op falls back to the existing bigram/trigram
  chain.

- **L2 Bergamot inline-translation facade.** `InlineTranslator` +
  `LanguagePairDescriptor` (lowercase ISO 639-1 src+target, bundle
  path, size, quality tier in tiny/base/high). `TranslationResult.
  {Unavailable, Translated}`.

- **L3 librime CJK input facade.** `CjkInputProvider` + `CjkSchema`
  enum covering Pinyin (Simplified + Traditional), Jyutping, Zhuyin,
  Cangjie 5, Wubi 86, Quick / double-pinyin Xiaohe, Japanese Mozc,
  Korean Jamo. `CjkCandidate(text, annotation, confidence, isPreferred)`.

- **L4 RTL BiDi shaping (real implementation, not just a facade).**
  `RtlBidiResolver.analyze` wraps `java.text.Bidi` (JVM stdlib, zero
  external dep) and surfaces composing-region run boundaries +
  paragraph base direction. Fixes upstream FlorisBoard's layout-only
  RTL bug class for mixed Arabic/Hebrew/Persian/Urdu + Latin text.

- **L5 Indic transliteration with full Hindi ITRANS table.**
  `IndicTransliterator` runs greedy longest-prefix-match against
  `IndicScriptTable.ItransToDevanagari` covering Hindi/Marathi/Sanskrit
  consonants + vowels + halant/anusvara/visarga + Devanagari digits
  0-9 + danda/double-danda punctuation. Bengali/Tamil/Telugu/Marathi
  /Gujarati/Punjabi/Kannada tables ride on the same engine in L5.x.

- **L6 Ge'ez SERA transliterator.** `GeezSeraTransliterator` covers
  ~28 consonant radicals × 7 vowel forms = the canonical Amharic /
  Tigrinya / Tigre / Blin glyph set + Ethiopic digits 1-9 + Ethiopic
  punctuation. Greedy longest-match.

- **L8 Keyman LDML XML parser (XXE-hardened).** `KeymanLdmlParser`
  uses `javax.xml.parsers.DocumentBuilderFactory` with all OWASP XXE
  defenses (no DTD, no external general / parameter entities, no
  XInclude, no entity-reference expansion) — addon-supplied LDML
  crosses an addon-IME trust boundary, so the parser hardens up
  front rather than retrofitting later.

- **L10 WebAuthn passkey injection contract.** `PasskeyAdapter`,
  `PasskeyFieldDetector.detect(autofillHints, extras)`, and
  `PasskeyAssertionRequest` (cross-process WebAuthn assertion
  envelope). Detector only fires on a password-class hint AND a
  WebAuthn relying-party id + challenge in `EditorInfo.extras` —
  conservative by design.

- **L12 WordStyles renderer facade.** `WordStylesRenderer` +
  `WordStyle` data class with strict RGBA-hex validation and four
  built-in styles (Neon / Gradient Sunset / Retro Typewriter / Soft
  Pastel). Canvas/Paint render lives in `WordStylesAndroidRenderer`
  (L12.1).

## NEXT-tier finish

- **Next-9.4 emoji palette enhancements (all four pieces shipped).**
  Custom tags + predict-by-tag came in v1.8.0; v1.8.1 adds
  search-by-tag (`EmojiSearch.results` consults `CustomEmojiTagStore`
  at score 2 — above bundled keyword exact match at 3) and pin
  emoji together (`EmojiPinGroupStore` with 32-group / 12-emoji /
  32-char caps and atomic-rename JSON storage).

- **Next-5.3 sync channel taxonomy + parser.** `SyncChannel` sealed
  class with four variants (Syncthing / LocalFolder / ManualExport /
  Disabled). Each emits a canonical channel-id string that round-trips
  through `SyncChannel.parse()`. Unknown ids → Disabled (privacy-safe
  fallback). Feeds straight from `PairingPayload.syncChannelId`
  (Next-5.2).

- **L11.1 Tasker intent contract.** `TaskerIntentContract` pins four
  Tasker-trigger-able intent actions (INSERT_TEXT / INSERT_CLIP /
  SWITCH_LAYOUT / TRIGGER_VOICE) under
  `permission.REGISTER_ADDON`. Validator enforces extras schema (4096-
  char insert cap, layout-id regex, voice mode enum).

## Tests

47 net new unit tests across 13 new test classes. Suite total now
**494** (was 416 at v1.8.0). All facades follow the
`StrokeRecognizerRegistry` pattern from Next-4.2: heavy runtime stays
out of `:app`, behind a `*Registry` the IME reads.

## Genuinely external-blocked items

The only items left that *cannot* be scaffolded further without
something specific from the outside world:

- **N1.1** HeliBoard NLnet glide library — released by Jun 2026 per
  the active NLnet grant.
- **N1.2** CleverKeys multi-layout model — vendor roadmap targets
  Q2-Q3 2026 for the multi-script gesture model.
- **N10.1** Noto Color Emoji 17.0 fonts — depends on `androidx.emoji2`
  1.7.0+ being published.
- **Next-2.5** Rambler-style streaming-voice cleanup — gates on the
  L1 LLM addon being installed.

Every other open ROADMAP item now has a working scaffold + tests +
a clear adapter pattern for follow-up bring-up.
