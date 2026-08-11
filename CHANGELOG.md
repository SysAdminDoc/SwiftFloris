# Changelog

## Unreleased

- Screen addon and MCP daemon enrolment against a permission allowlist so
  transports that need no `INTERNET` permission — SMS, Bluetooth,
  nearby-devices, shared storage — are rejected alongside the network ones.
- Clear leftover cache off the main thread at startup, so a cold keyboard
  start no longer blocks on a recursive delete.
- Replace a typed word by selecting it and committing over the selection
  instead of marking a composing region, so rich-text and web editors that
  ignore composing regions no longer duplicate the word.
- Move the build to Android Gradle Plugin 9.3.0.
- Route all 42 shipped locales through the translation pipeline; eight,
  including Simplified Chinese and Urdu, previously had no mapping and could
  never round-trip. A gate now fails when a locale has no route.
- Declare the bundled emoji data as Unicode Emoji 17.0, which is what CLDR 48
  generated; the assets already carried every Emoji 17.0 character.

## v1.9.58 — 2026-08-02

- Keep trust-capability evidence aligned with SQLCipher clipboard fallback and
  manifest permission-removal directives, and make the release-front-door
  locale gate portable across `python` and `python3` environments.
- Retain bounded, privacy-gated glide alternatives for unchanged committed words and restore them when the cursor returns to the word.
- Search emoji across the active and enrolled subtype locales with ordered fallback matching and bounded deduplication.
- Expand headless settings screenshot coverage across compact, wide-landscape, RTL, and 200% font-scale states, with shared loading/error/empty semantics and production color contrast checks.
- Refresh Tink to 1.23.0, Roborazzi to 1.70.0, and Kotest to 6.2.3 with the existing verification gates retained.
- Localize privacy-audit record labels, plural summaries, and timestamps while keeping the JSON export schema locale-independent.
- Centralize editor input-class/variation/flag compatibility, clear stale candidates for host-owned completion fields, and add headless restart and hardware-key contract coverage.
- Add a deterministic trust-critical locale coverage gate with translated-resource ratchets, explicit reviewed UI locale policy, typing-language separation, `en-XA`/`ar-XB` pseudolocale contracts, and hard-coded critical-copy detection.
- Centralize bounded keyboard-mode/context transitions so clipboard and media panels restore the prior symbols/numeric mode and clear stale history across editor, privacy, and window boundaries.
- Wire custom emoji tags through long-press palette actions, palette search, emoji suggestions, persisted settings management, locale-root normalization, and atomic file replacement.
- Preserve existing emoji pin-group files when an atomic replacement fails, including on Windows hosts that reject rename-over-existing.
- Surface snippet load and delete failures, move snippet file work to Dispatchers.IO, and expose per-file trigger counts from state.
- Route emoji and sticker empty-state copy through the keyboard theme text pipeline, including expanded-font-scale Roborazzi coverage.
- Resolve custom-layout labels and generated extension metadata through localized resources while preserving stable on-disk slugs.
- Add programmable Page Up/Page Down key codes, Android page-key dispatch, custom-layout editor support, localized labels, and Terminal/Navigation preset coverage.
- Import Unicode Keyboard3 XML as hardened local keyboard extensions with bounded XML parsing, versioned bundled-CLDR allowlists, deterministic compilation, source provenance, and security/conformance diagnostics.

## Roadmap archive — 2026-08-10 — ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P3
## Research-Driven Additions (2026-06-29)

### P1

### P3

## Research-Driven Additions

### P1

### P2

## Research-Driven Additions (2026-06-29 refresh)

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P0

### P1

### P2

### P3

## Research-Driven Additions

### P2

### P3

## Audit Findings — 2026-08-02

Baseline for this pass: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`
→ **BUILD SUCCESSFUL in 4m 34s**, no failing tests, no lint failures. Nothing
below is a pre-existing baseline failure; every item is a defect found by
reading and tracing the code against that green baseline.

### P2

### P3
```

</details>
