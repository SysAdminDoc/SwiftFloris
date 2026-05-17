# SwiftFloris v1.8.60 — 2026-05-17

Phase B1 — multilingual sentence-position priors seed.

## Why ship this now

`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` calls out that the cold-start
next-word layer was still English-heavy. The parser and `ZipfFrequencyTable`
plumbing already existed; this slice widens the bundled seed data and
localized phrase priors for the Latin-script languages already shipping
as dictionaries.

No new permissions, runtime dependencies, network surfaces, or background
jobs are added.

## What changed

### Multilingual cold-start priors

`ColdStartNextWordPriors` now supports localized sentence-start,
one-word, two-word, and three-word priors for:

- English (`en`) — existing behavior preserved;
- Czech (`cs`);
- German (`de`);
- Spanish (`es`);
- French (`fr`);
- Italian (`it`);
- Portuguese (`pt`).

Examples covered by tests:

- `de-DE` sentence start → `ich`, `das`, `die`, `der`;
- `es-MX` after `Muchas gracias ` → `por`, `de`, `otra`;
- `fr` after `Merci beaucoup ` scores `pour`;
- `pt-BR` after `Bom dia ` scores `como`;
- unsupported languages still return no priors instead of leaking English.

### Zipf seed overlays

Added top-1,000 `rspeer/wordfreq` 3.1.1 Zipf seed overlays:

- `app/src/main/assets/freq/cs.tsv`
- `app/src/main/assets/freq/de.tsv`
- `app/src/main/assets/freq/es.tsv`
- `app/src/main/assets/freq/fr.tsv`
- `app/src/main/assets/freq/it.tsv`
- `app/src/main/assets/freq/pt.tsv`

These mirror the existing `freq/en.tsv` shape consumed by
`ZipfFrequencyTable.parse(...)`: one `word<TAB>zipf` row, UTF-8,
range `[1, 8]`. Full corpus-sized subtitle tables still belong in
dictionary-pack addons so the base APK stays lean.

### Attribution

`NOTICE` now records the bundled Zipf seed-table source:
`rspeer/wordfreq` 3.1.1, generated via `top_n_list()` and
`zipf_frequency()`.

## Tests

Added / updated unit coverage for:

- localized sentence-start priors;
- localized phrase-prior scoring;
- unsupported-language behavior;
- parsing all six new bundled Zipf seed tables and verifying each table
  has exactly 1,000 entries plus a representative common word.

## Versioning

- `gradle.properties`: `projectVersionCode=1860`,
  `projectVersionName=1.8.60`.

## Verification

Local non-Java checks:

```powershell
git diff --check
python -c "from pathlib import Path; [print(path.name, len([line for line in path.read_text(encoding='utf-8').splitlines() if line and not line.startswith('#')])) for path in sorted(Path('app/src/main/assets/freq').glob('*.tsv'))]"
```

The row-count check reported 1,000 entries each for `cs`, `de`, `en`,
`es`, `fr`, `it`, and `pt`.

This VM still has no JDK / Android SDK on the path, so run before merge
on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.latin.ColdStartNextWordPriorsTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.latin.ZipfFrequencyTableTest
```

## What's next

The remaining unblocked SwiftKey-parity work is B2 quick-prediction-insert
tuning, C1 split-keyboard renderer wire-up, C3 High-Contrast / animated
themes, and D1 calendar quick-insert. B5 still needs human-captured local
trace fixtures before decoder constants should move.
