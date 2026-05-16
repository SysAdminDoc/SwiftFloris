# SwiftFloris v1.8.33 — 2026-05-15

L9.2 honeycomb layout loader — pure-JVM bridge from the shipped
`honeycomb.json` to the `HoneycombKeyboardRow` renderer.
**945 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing yet. The honeycomb layout still isn't registered in
`extension.json` (selectability + working touch-hit through
`TextKeyboardLayout` is the final L9.2 slice). This release closes
the renderer→layout-JSON bridge so when the integration lands the
loader is already proven against the shipped layout shape.

## What changed (internal)

### Next-9.2c — `HoneycombLayoutLoader`

New `ime/text/keyboard/HoneycombLayoutLoader`:

- Pure-JVM object — uses `kotlinx.serialization.json` (already
  on the implementation classpath) so it runs in the unit-test
  JVM without Robolectric.
- `parse(json: String): List<List<String>>` — converts the
  FlorisBoard character-layout JSON shape into the exact
  `List<List<String>>` the `HoneycombKeyboardRow` renderer
  consumes.
- **Filter heuristic**: drops any key whose `type` field is
  set (`modifier` / `system_gui` / `enter_editing`) and any key
  whose label matches the known modifier-word set (`shift`,
  `delete`, `space`, `enter`, `view_symbols`, etc.). Character
  keys including punctuation (`,`, `.`) pass through.
- **Failure mode**: returns an empty list on malformed JSON
  rather than throwing — keeps the renderer fail-safe against
  disk corruption or bad addon-supplied layouts.
- `Json` config: `ignoreUnknownKeys = true`, `isLenient = true`,
  `allowTrailingComma = true` — tolerates future schema additions
  and the trailing-comma flavour some hand-edited layouts ship
  with.

### Tests — `HoneycombLayoutLoaderTest`

Eight new Kotest tests pinning the loader contract:

1. Parses the exact 5-row shape shipped at
   `assets/.../layouts/characters/honeycomb.json` into the
   expected character-label rows, with `shift` / `delete` /
   `space` / `enter` / `view_symbols` filtered.
2. Filters cells with no label.
3. Filters cells with non-empty `type` field.
4. Skips rows that contain only modifier keys.
5. Trims whitespace inside labels.
6. Returns empty list on malformed JSON / empty string.
7. Returns empty list on empty array.
8. Ignores unknown fields on key objects (forward compat).

## Versioning

- `gradle.properties`: `projectVersionCode=1833`,
  `projectVersionName=1.8.33`.
- README badge bumped to `v1.8.33`.

## What's next

- Asset-reader glue: feed `honeycomb.json` from `assets/` into
  the loader at runtime (the IME's existing asset-loader pattern,
  used by `KeyboardManager` for the QWERTY family, plugs in
  cleanly here).
- `TextKeyboardLayout` integration: touch routing through the
  existing pointer-event pipeline, Snygg theming, popup support
  — registers the layout in `extension.json` once selectability
  + working touch-hit are both verified.
