# SwiftFloris v1.7.0 — Correctness floor + privacy + SwiftKey-parity polish

**Released:** 2026-05-09
**Versioning:** 1.6.0 → **1.7.0** (versionCode 160 → 170)
**Roadmap §:** 6 (NOW). Closes ten Now-tier items in one release.

This is the first ROADMAP v4.0 ship. It hits three themes simultaneously:
correctness floor (no more TODO() crashes), privacy hardening (no-network
contract pinned in CI, password-field protections, signing fingerprint), and
SwiftKey-parity polish (word-delete gestures, shortcut auto-replace, sans-serif-medium
glyphs).

---

## Correctness floor

- **N11. Runtime TODO() stubs resolved.** `KeyboardExtension.edit()` and
  `LanguagePackExtension.edit()` were both `TODO("...")` calls that would have
  crashed the IME if any code path traversed them. Both now return real
  `ExtensionEditor` implementations modeled on `ThemeExtensionEditor`. F-Droid
  acceptance review can no longer flag them. (`FlorisSpellCheckerService` TODO
  documented as an intentional delegate to AOSP's default sentence-aggregation,
  which is already SwiftFloris-backed via `NlpManager`.)

## Privacy hardening (the moat verbatim)

- **N7.1. No-INTERNET-permission build gate.** New
  `:app:verifyNoInternetPermission` Gradle task scans every `AndroidManifest.xml`
  on every variant build. The build fails with a contract-violation message if
  any of `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
  `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE` is declared. Pins SwiftFloris's
  no-network promise in CI, not just in marketing.

- **N7.2. Password-field hardening.**
  Personal-dictionary auto-learn (`learnIfAllowed`) now also gates on
  `keyVariation == PASSWORD`, in addition to the existing
  `IME_FLAG_NO_PERSONALIZED_LEARNING` incognito gate — many host apps forget to
  set the no-personalized-learning flag, so the variation check is a defense-in-depth
  layer. `EditorInstance.performClipboardCut` and `performClipboardCopy` now skip
  the IME-local clipboard history when the active field is a password variation.

- **N7.5. APK signing fingerprint pin.** New `Settings → About → APK signing
  fingerprint` shows the SHA-256 of the running install's signing certificate,
  formatted to match `apksigner verify --print-certs`. Tap to copy. Compare
  against the value in README to detect supply-chain APK swaps.

## CI + release engineering

- **N6.1. PR gates.** GitHub Actions now sequences
  `verifyNoInternetPermission` → `:app:testDebugUnitTest` →
  `:app:lintDebug` → `:app:assembleDebug`. Lint and test reports upload as
  artifacts on every run. Gradle cache wired via
  `gradle/actions/setup-gradle@v4`.

- **N6.5. Obtainium one-tap install URL.** README now leads with an
  `obtainium://app/{...}` URL that auto-subscribes to GitHub Releases for
  hands-free updates without polling.

## Word-edit ergonomics

- **N5.1. Hold/swipe-backspace = delete word.** Default
  `deleteKeyLongPress` and `deleteKeySwipeLeft` flipped to
  `SwipeAction.DELETE_WORD`. SwiftKey/Gboard parity. User overrides preserved
  via jetpref's fall-back-only-when-unset semantics.

- **N5.4. Auto-replace shortcuts in the personal dictionary.** Settings UI was
  already present (Personal dictionary → Add → Word + Shortcut + Locale); wired
  the auto-replace half via
  `DictionaryManager.queryUserDictionaryShortcutExact` + a new
  `userDictionaryShortcutAutoCommitCandidate` step in
  `NlpManager.getAutoCommitCandidate` that runs *before* in-strip suggestions
  and the English contraction fallback. Add `omw → on my way`, type `omw `,
  watch it expand.

- **N10.3. Surrogate-pair-safe backspace.** `AbstractEditorInstance.deleteText`
  now uses `InputConnection.deleteSurroundingTextInCodePoints` (API 24+, always
  available since `minSdk = 26`) for both BEFORE_CURSOR and AFTER_CURSOR
  scopes. ICU break-iterator already returned grapheme-aligned char offsets;
  the conversion via `String.codePointCount` makes the call code-point-safe
  even if the editor has drifted from our expected text. Backspace now never
  splits a surrogate pair in Unicode 16/17 emoji or Indic conjuncts.

## SwiftKey-parity polish

- **N3.2. sans-serif-medium key glyph weight.**
  `FlorisImeUi.Key.elementName` base style sets
  `fontWeight = fontWeight(FontWeight.Medium)` (weight 500). Propagates to
  every theme. Closes the SwiftKey perceived-quality gap without changing
  dimensions or layouts.

---

## Unchanged from v1.6.0
- 117,022-word SCOWL-merged English dictionary
- 130-entry contraction autocorrect table (SAFE / DICTIONARY_GATED)
- Auto-cap with sentence-end context detection
- 6-language gesture typing (EN/DE/ES/FR/IT/PT)
- FUTO Voice Input integration + voice commands
- Encrypted clipboard (AES-256-GCM, max 50 items)
- Themes: Nord, Tokyo Night, Dracula, Catppuccin Mocha (+ SwiftKey Pure tokens, picker entry pending — N3.1)

## Verification (DoD checklist)

- [x] `./gradlew :app:compileDebugKotlin` clean (warnings only, all pre-existing)
- [x] `:app:verifyNoInternetPermission` passes; manual injection of INTERNET fails the build with the expected message
- [x] All version strings updated: `gradle.properties`, README badge, `RELEASE_NOTES_v1.7.0.md`, ROADMAP §2 + §3 (this release)
- [x] No new `TODO()` runtime stubs introduced
- [x] No new third-party dependencies (no `NOTICE` / `LICENSES/` updates needed)

## What's next (v1.7.x → v1.8.0)

Next picks from ROADMAP §6:
- **N3.1** wire SwiftKey Pure Light/Dark theme presets into the picker
- **N3.3 / N3.4** SwiftKey haptic profile + envelope haptics + pressed-key flash
- **N5.2** cursor-mode polish (hold space → drag)
- **N7.3** personal-dictionary isolation regression test
- **N8** accessibility scoped pass (TalkBack labels, 48dp targets, contrast audit)
- **N9** `commitContent()` for sticker / GIF / image insertion
