# SwiftFloris v1.7.1 — More NOW-tier closures

**Released:** 2026-05-09
**Versioning:** 1.7.0 → **1.7.1** (versionCode 170 → 171)
**Roadmap §:** 6 (NOW). Five additional Now-tier items closed on top of v1.7.0's 10.

This is a same-day follow-up to v1.7.0, batching all the SwiftKey-parity polish
and accessibility items that fit cleanly on top of the v1.7.0 correctness floor.

---

## Privacy + accessibility

- **N7.3 — Personal-dictionary isolation regression test + threat model.**
  New `PersonalDictionaryIsolationTest` (Kotest, 3 tests) statically verifies that
  `DictionaryManager.learnWord` never references the system `UserDictionary`
  ContentProvider. New `docs/THREAT_MODEL.md` enumerates threat actors, live
  defenses, known gaps, and a per-release verification checklist.

- **N8.4 — Reduced-motion guard on gesture trail.**
  `TextKeyboardLayout` now reads
  `Settings.Global.ANIMATOR_DURATION_SCALE` and suppresses the glide trail when
  the user has Animations off (Developer Options → Animator duration scale = 0).

- **N8.5 — Switch Access compatibility (verified).**
  `method.xml` already declares `supportsSwitchingToNextInputMethod="true"` and
  `FlorisImeService.switchToNextInputMethod` correctly uses
  `imm.switchToNextInputMethod(token, false)`.

## SwiftKey-parity polish

- **N3.1 — SwiftKey Pure (Light) + SwiftKey Pure (Dark) themes.**
  New stylesheets at
  `assets/ime/theme/org.florisboard.themes/stylesheets/swiftkey_pure_{light,dark}.json`
  consume the pure tokens already defined in `colors_branding.xml`. Both themes
  inherit the v1.7.0 `FontWeight.Medium` glyph weight automatically.

## Word-edit ergonomics

- **N5.3 — Scalable keyboard height slider.** New
  `keyboardHeightMultiplierPortrait` / `Landscape` prefs (50..150%, default
  100%), surfaced as a `DialogSliderPreference` directly under the existing
  font-size slider in `Settings → Keyboard → Layout & size`. Threaded through
  `ImeWindowSpec.UserPreferredOptions.keyboardHeightScale` and applied in
  `doComputeWindowSpec` before the form-factor [`min`, `max`] clamp.

## Verified-already-shipped

- **N9.1 / N9.2 — `commitContent()` for clipboard images, surfaced in panel.**
  Both already wired via FlorisBoard upstream. `EditorInstance.commitClipboardItem`
  for `ItemType.IMAGE`/`VIDEO` calls `InputConnectionCompat.commitContent` with
  `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`. Verification documented in the
  ROADMAP.

---

## Cumulative NOW-tier progress (v1.7.0 + v1.7.1)

Sixteen Now-tier items closed across the two releases:
N3.1, N3.2, N5.1, N5.3, N5.4, N6.1, N6.5, N7.1, N7.2, N7.3, N7.5, N8.4, N8.5, N9.1, N9.2, N10.3, N11.

Open Now items, in priority order: N3.3 (haptics), N3.4 (pressed-key flash),
N3.5 (verify dimens flow), N5.2 (cursor mode polish), N7.2 (FLAG_SECURE on
suggestion-strip popups), N8.1/N8.2/N8.3/N8.6 (a11y audit), N9.3 (emoji search),
N10.1/N10.2 (Emoji 17 readiness), N1 (glide breadth — gates on HeliBoard NLnet
drop), N2 (multilingual auto-detect), N4 (smartbar customization), N6.2/N6.3/N6.4 (signed releases + reproducible builds + CVE scan).

---

## Verification

- [x] `./gradlew :app:assembleDebug` clean.
- [x] `./gradlew :app:testDebugUnitTest` — `PersonalDictionaryIsolationTest`
  added; existing tests remain green.
- [x] `:app:verifyNoInternetPermission` passes.
- [x] All version strings updated: `gradle.properties`, README badge,
  `RELEASE_NOTES_v1.7.1.md`, ROADMAP.
