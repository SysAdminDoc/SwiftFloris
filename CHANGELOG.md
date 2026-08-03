# Changelog

## Unreleased

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
