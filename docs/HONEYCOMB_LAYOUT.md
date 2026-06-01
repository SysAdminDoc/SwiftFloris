# Honeycomb Hex Layout

**Status:** shipped v1.8.79 (renderer wire-up); foundation v1.8.31–v1.8.33.
**Asset:** `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/honeycomb.json`.
**Selectable from:** Settings → Localization → Subtype → Character layout → `Honeycomb`.

SwiftFloris ships a production hexagonal keyboard layout as a first-class alternative to the QWERTY tessellation. The implementation lays out real `TextKey` instances in a hex grid, clips production Snygg key surfaces to `HoneycombHexShape`, and hit-tests against the hex geometry instead of the rectangular bounding box that nearest-key rescue normally uses.

This document records the design decisions, the contributor surface, the strategic positioning, and the open follow-ups.

---

## Background

[Typewise](https://www.typewise.app/) shipped a proprietary honeycomb-AI keyboard from 2018, won CES Innovation Awards in 2021 and 2022, and built consumer demand around the claim that the hex tessellation gives every key 60% larger touch target than the staggered-row QWERTY at the same total keyboard height. The app was pulled from the consumer market in early 2026 when Typewise pivoted to enterprise AI, leaving the niche genuinely vacated. SwiftFloris's honeycomb is the first FOSS production implementation in the FlorisBoard / HeliBoard / OpenBoard family.

### Why the hex tessellation matters for typing

A QWERTY keyboard arranges keys in 3 staggered rows of 10. Each key is roughly a rectangle. Honeycomb arranges the same 26 letters into a hex grid where each key is a regular hexagon and every interior key has 6 equidistant neighbours instead of QWERTY's mix of 2-4. The practical effects:

- Per-key touch area is larger at the same total keyboard height (the hex packs more tightly than rectangles).
- Mistype recovery is more predictable because every adjacent key sits at the same radial distance from the centre of the source key — adaptive touch correction (`AdaptiveTouchModel`, see N12.1) needs fewer samples to converge.
- The visual rhythm is novel enough to be noticeable but familiar enough that the underlying keymap stays QWERTY-ordered, so muscle memory transfers within a few minutes.

The tradeoff: hex hit testing is geometrically harder than rectangular hit testing. SwiftFloris's `TextKeyboard.layoutHoneycomb(...)` and `HoneycombHexShape` solve this by clipping every key surface to the actual hex polygon and rejecting taps that land in the corner gaps between three hexes (where rectangular nearest-key rescue would have grabbed the wrong key).

---

## Implementation surface

| File | Role |
|---|---|
| [`HoneycombHexShape.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombHexShape.kt) | Compose `Shape` returning the regular-hexagon outline for a key's bounds; used to clip key surfaces. |
| [`HoneycombHexButton.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombHexButton.kt) | A single hex key composable building block. |
| [`HoneycombKeyboardRow.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombKeyboardRow.kt) | Lays out one row of hex keys with the half-offset that alternating rows require. |
| [`HoneycombTessellation.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombTessellation.kt) | Pure geometry: row/column → centre point + neighbour resolution. |
| [`HoneycombLayoutLoader.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombLayoutLoader.kt) | Bridges the `honeycomb.json` layout asset to the renderer (reads key codes + glyphs + popup mappings). |
| [`TextKeyboard.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboard.kt) `layoutHoneycomb(...)` | Production layout function: positions real `TextKey` instances at hex centres, applies the row stagger, and writes `touchBounds` as the hex polygon (not the rectangular bounding box). |
| [`TextKeyboardLayout.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt) | Wraps `HoneycombHexShape` around the Snygg-themed key surface when the active layout style is `TextKeyboardLayoutStyle.Honeycomb`. |
| [`LayoutManager.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt) | Marks the bundled `honeycomb` character layout as `TextKeyboardLayoutStyle.Honeycomb` so the renderer routes through `layoutHoneycomb(...)` instead of the standard row layout. |

The popup-mapping system (long-press alt-glyphs) works identically to QWERTY — `honeycomb.json` carries the same `popup` arrays. Glide typing, autocorrect, adaptive touch, and multilingual ranking all work unchanged because they consume `keyboard.getKeyForPos(...)` results which now return the hex-aware key.

---

## How to enable it

1. Open Settings → Localization → Subtypes.
2. Tap an existing subtype (or add a new one).
3. Under "Character layout" pick **Honeycomb**.
4. Save the subtype. Open any text field; the active subtype now renders the hex tessellation.

Honeycomb is **not** the default character layout — that is intentional. QWERTY is what 95%+ of users expect when they install a keyboard, and the migration audience (SwiftKey refugees per `docs/MIGRATE_FROM_SWIFTKEY.md`) needs zero learning curve to start typing.

---

## Strategic positioning

SwiftFloris is the only FOSS Android keyboard that ships a production honeycomb layout as of 2026-05-25 (cross-checked against the May 2026 `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` for FlorisBoard upstream / HeliBoard / OpenBoard / AnySoftKeyboard / Unexpected Keyboard / FUTO / Sayboard / Simple Keyboard / Fossify). Typewise vacated the consumer market in early 2026.

### What this is not

- **Not a copy of Typewise.** Typewise's app is closed-source. SwiftFloris's implementation is original Kotlin built against `Shape`/`TextKey`/`TextKeyboardLayout` primitives that already existed in the FlorisBoard codebase. No Typewise code was inspected; the visual tessellation is a public geometric pattern.
- **Not an AI feature.** Honeycomb is pure layout. SwiftFloris's autocorrect / glide / suggestion ranker runs against any character layout, including hex. There is no neural model "trained for hex"; the same `StatisticalGlideTypingClassifier`, `SymSpellIndex`, `PersonalBigramStore`, and `PersonalTrigramStore` apply.
- **Not promoted as the default.** SwiftKey-refugee onboarding stays QWERTY-first per the migration walkthrough; honeycomb is an opt-in alt for users who want it.

---

## Open follow-ups

- **Roborazzi visual baseline.** As of v1.8.180 there is no committed snapshot under `app/src/test/snapshots/` for the honeycomb layout. A regression to `HoneycombHexShape` or `HoneycombKeyboardRow` would not slip through the `:app:verifyRoborazziDebug` gate because no baseline exists to diff against. Tracked as `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md` F40 (Roborazzi screen-level baseline expansion).
- **Theme-coverage matrix.** Verify all 21 bundled themes render the hex key surface correctly (Snygg `border-radius` tokens applied to a hex shape are non-obvious — the corner-radius should be honoured only on the visual outline, not the touch hitbox). Spot-check across SwiftKey Pure Light/Dark, SwiftKey High Contrast (AAA), Aurora Animated, M3E Nord, Tokyo Night, Dracula, Catppuccin Mocha.
- **Tablet split-mode honeycomb.** Next-7.2's split keyboard renderer (shipped v1.8.62) currently passes through to standard QWERTY layout when split mode is active. Wiring honeycomb-in-split is a separate slice (the gutter math is more complex with hex rows because the stagger pattern interacts with the gutter post-shift). Not blocking; users can flip back to standard layout for split.
- **Performance baseline.** No Macrobenchmark entry for honeycomb hit testing vs rectangular hit testing. The geometry is O(1) (single hex containment test per finger position) so worst-case latency should match QWERTY, but evidence > theory. Track as a `docs/BENCHMARKS.md` follow-up.
- **External communication.** README `Highlights` table now mentions honeycomb; the fastlane `full_description.txt` rewrite in v1.8.175 also names it. A `docs/HONEYCOMB_LAYOUT.md`-style longform (this file) was missing through v1.8.180 and is the present slice.

---

## Files mentioned

- `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/honeycomb.json`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombHexShape.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombHexButton.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombKeyboardRow.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombTessellation.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombLayoutLoader.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboard.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt`
- [`CHANGELOG.md#v1.8.79`](../CHANGELOG.md#v1.8.79) — renderer wire-up
- [`CHANGELOG.md#v1.8.31`](../CHANGELOG.md#v1.8.31), `#v1.8.32`, `#v1.8.33` — foundation building blocks
- [`CHANGELOG.md#v1.8.181`](../CHANGELOG.md#v1.8.181) — this doc
