# SwiftFloris v1.7.9

Released: 2026-05-14

A multi-item ROADMAP pass that closes NOW-tier polish (popup animation,
a11y labels) and lights up a wide NEXT-tier slice: capitalization-aware
suggestions with explicit tests, an in-strip "Remove from predictions"
prompt with a springy entry/exit, dictation-stream voice command wiring,
JVM importers for Gboard / FlorisBoard backups, a programmer-mode
smartbar profile, an addon manifest schema + enumerator, a per-app
adaptive-accent foundation, and property-based autocorrect invariants.
Every change is unit-tested and the full `:app:testDebugUnitTest` suite
passes (356 tests). Built against the same JDK 17 / AGP 9.0.0 /
Kotlin 2.3.20 / Compose BOM 2026.03.01 toolchain as v1.7.7.

## Changes

### NOW-tier finishes

- **N3.4 finish — popup polish.** Pressed-key 1.03× scale-up over 60ms
  with an 80ms spring-back on release (graphicsLayer-only, no
  touch-target geometry change). Long-press popup variant now carries
  a 1.5dp accent-ring stroke via `--primary`, so per-theme overrides
  (SwiftKey Pure, Tokyo Night, …) retint it automatically. Reduced-
  motion (Developer Options → Animator duration scale = 0) suppresses
  the scale; the static PRESSED Snygg color flip still reads.
- **N8.3 finish — accessibility labels.** Smartbar quick actions now
  carry a TalkBack-readable `contentDescription` (action's display
  name → tooltip → "Action" fallback). Suggestion-strip slots
  announce candidate text + role + a "Remove from predictions"
  custom accessibility action for eligible candidates. Long-press hint
  on keys with an alt-glyph now appends "alternative: <hint>" to the
  TalkBack readout so screen-reader users know extra characters are
  available.

### NEXT-tier shipped

- **Next-3.3 — capitalization-aware suggestions.** Existing
  `applyTypedCase` contract is now explicitly tested across prefix
  completion, distance-1 correction, distance-2 correction, and the
  Title-Case / ALL_CAPS / lowercase branches. Closes FlorisBoard
  #1007 (`Foo` if `F`, `foo` if `f`).
- **Next-3.4 — long-press to forget, with confirmation.** Long-pressing
  a removable suggestion now surfaces an in-strip
  "Remove '<word>' from predictions" prompt instead of silently
  deleting. Tap "Remove" → forget across personal dict + bigram +
  trigram. Tap "Cancel" or anywhere on the strip backdrop → dismiss.
  Closes COMM-A FR-22 / FlorisBoard #737 / AnySoftKeyboard #1399.
- **Next-9.3 — password-manager compatibility doc.** Live
  `docs/INLINE_AUTOFILL.md` matrix of verified Bitwarden / KeePassDX /
  Proton Pass / 1Password / Aegis versions per Android version, plus
  the verification recipe to refresh on every release that touches
  `FlorisImeService.onCreateInlineSuggestionsRequest`. Next-9.1
  (`supportsInlineSuggestions=true`) and Next-9.2 (smartbar slot
  rendering) verified-already-shipped.
- **Next-6.3 — SwiftKey migration doc.** Honest writeup of the three
  available paths (retrain, MS-account redownload, root extraction)
  plus an explicit refusal to ship a SwiftKey-cloud OAuth helper.
  Matches the §1 no-network philosophy.
- **Next-6.1 + Next-6.2 — Gboard + FlorisBoard backup importers.**
  New `DictionaryImporter` parses Gboard `PersonalDictionary.zip`
  (XML inside zip) and generic CSV (`word,frequency,shortcut,locale`)
  shapes, with explicit schema-detection, entity decoding, header-row
  tolerance, frequency clamping, and clear errors. FlorisBoard
  `.flbackup` SQLite snapshots are explicitly routed to the in-app
  importer path. Test fixtures cover Gboard XML, escaped entities,
  CSV with and without header, zip end-to-end, and clear errors for
  every unsupported shape.
- **Next-2.4 — voice-commands on streaming.** `VoiceInputManager`
  now exposes `consumeStreamingChunk(chunk, actions, customCommands)`
  that pipes per-chunk transcripts through the existing
  `StreamingVoiceTranscriptBuffer` and fires `VoiceCommandExecutor`
  on final-chunk command matches, so "change dog to cat"-style voice
  edits fire the moment the user finishes the utterance. Returns a
  `VoiceStreamingCommandUpdate` carrying both the transcript and the
  optional execution result.
- **Next-7.3 — one-handed mode UX surface verified.** Audit confirmed
  the smartbar `TOGGLE_COMPACT_LAYOUT` quick-action, `SwipeAction`
  binding, and in-window flip / dismiss controls (chevron + zoom) are
  all already wired into `ImeWindowController` and `OneHandedPanel`.
  No new code; explicitly cited so future contributors don't re-derive.
- **Next-8.1 + Next-8.2 — programmer-mode smartbar profile.** New
  `SmartbarActionProfile.CODE` surfaces Tab, Esc, arrow keys, and
  start/end-of-line jumps when the editor's package matches a curated
  set (Termux, JuiceSSH, Acode, Spck, ConnectBot, Termius, JetBrains
  family, …). `TextKeyData.TAB` and `TextKeyData.ESCAPE` are now
  predefined. Code-mode wins the matcher over CHAT when both could
  match, so terminal users don't get a chat smartbar.
- **Next-10.1 + Next-10.2 — addon manifest schema + enumerator.**
  New `dev.patrickgold.florisboard.ime.addon` package defines the
  intent-action surface (`REGISTER_ADDON`, `REGISTER_LANGUAGE_PACK`,
  `REGISTER_THEME_PACK`, `REGISTER_DICTIONARY_PACK`,
  `REGISTER_LAYOUT_PACK`, `REGISTER_POPUP_MAPPING_PACK`), the
  `<meta-data>` schema, and a signature-protected
  `permission.REGISTER_ADDON`. `AddonEnumerator.snapshot()` discovers
  installed addon packages via `PackageManager`, validates each
  against the no-network invariant (any addon declaring INTERNET /
  ACCESS_NETWORK_STATE / etc. is hard-rejected), reads the addon's
  signing fingerprint via the existing N7.5 `SigningFingerprint`
  helper, and returns a list of `AddonManifest` records ready for
  registration. Forward-compat: unknown addon types skip silently.
  `AndroidManifest.xml` now declares the permission and adds the
  required Android 11+ `<queries>` entries.
- **Next-11.2 — springy dismiss.** Next-3.4's confirm overlay
  enters with `scaleIn(initialScale = 0.85f) + fadeIn` at
  DampingRatioMediumBouncy / StiffnessMedium and exits with
  `scaleOut + fadeOut` at StiffnessHigh. Reads as a deliberate action
  rather than a flash; cancel gets immediate feedback.
- **Next-11.3 — per-app adaptive accent (foundation).** New
  `PerAppAccentResolver` extracts a dominant-saturated color from
  the foreground editor's app icon (32×32 raster, HSV scan, reject
  near-grey / near-white / near-black, highest-saturation wins).
  In-memory LRU cache, 64-entry capacity. Hue / saturation /
  classification helpers exposed for testing. No `PACKAGE_USAGE_STATS`
  / `UsageStatsManager` required — the IME already knows the
  editor's package via the system contract. Application of the
  resolved color to keyboard tokens is intentionally deferred to a
  follow-up so the foundation can ship audit-clean.
- **Next-12.3 — property-based autocorrect invariants.**
  Eleven Kotest checkAll cases pin: normalizeWord idempotency,
  null-on-non-letter input, no typed-literal autocommit, candidate
  cap, dedup-by-lowercase, Damerau-Levenshtein ≤ 2 on corrections,
  delete-and-retype identity, Title Case / ALL_CAPS case-preserve,
  and crash-resistance on repeated-character substrings. Independent
  Damerau-Levenshtein oracle so a bug in the suggester can't silently
  match a bug in the test.

### Build / repo hygiene

- Debug-variant labelled "SwiftFloris Debug" via a debug-only
  strings.xml overlay; FlorisBoard's leftover chef-hat debug icon
  drawables deleted so debug builds use the main launcher.
- New unit test files: `LatinSuggesterPropertyTest`,
  `AddonManifestTest`, `PerAppAccentResolverTest`,
  `DictionaryImporterTest`, expanded `LatinDictionarySuggesterTest`
  and `SmartbarActionProfilesTest`. All tests green
  (`:app:testDebugUnitTest` — 356 tests).
- `:app:compileDebugKotlin` clean against AGP 9.0.0, Kotlin 2.3.20,
  Compose BOM 2026.03.01 (warnings unchanged from v1.7.7).

## Open follow-ups for v1.8.x

- **Next-7.1 — floating window mode.** Drag-handle + resize-anchor +
  per-corner snap geometry. Heavy Compose surgery; intentionally not
  in this drop.
- **Next-7.2 — split keyboard for tablet landscape.** Same caveat.
- **Next-11.3 surface wiring.** The accent resolver foundation is
  shipped; routing the resolved color into theme tokens / smartbar
  accent / keyboard tint is the next slice.
- **Next-11.1 — M3 Expressive theme regen** against the new accent
  resolver.
- **L1 — Gemma 3 270M smart-compose** — still upstream-gated on
  LiteRT-LM. Tracking.

## Verification

- `:app:compileDebugKotlin` — green.
- `:app:testDebugUnitTest` — 356 tests, all passing.
- `:app:verifyNoInternetPermission` — green (the privacy gate stays
  green; addons declaring network permissions are rejected at
  enumeration time).
- Manual install on Galaxy R5CY34G070L pending — UI animations
  (Next-3.4 confirm overlay, Next-11.2 springy dismiss) and
  per-package accent extraction need device verification before the
  GitHub release artifact is signed.
