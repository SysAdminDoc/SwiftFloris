# SwiftFloris v1.8.34 — 2026-05-15

Next-12.1 Macrobenchmark trace instrumentation — the six trace
sections the existing `KeyboardLatencyBenchmark` already measures
are now wired into the production hot paths they're meant to
record. **945 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing. The instrumentation is no-op at runtime when systrace
isn't capturing (`android.os.Trace.beginSection` returns
immediately when the system tracer is disabled, per the Android
contract).

## What changed (internal)

### Next-12.1 — production trace sections wired in

Before this release, the `KeyboardLatencyBenchmark` module in
`benchmark/` already declared `TraceSectionMetric` entries for six
expected section names, but the production code didn't emit them.
The benchmark would record zero or one-frame durations across the
board — useless data.

Six call-sites now emit the matching `swiftfloris.<subsystem>.<action>`
sections:

| Section name | Production call-site |
|---|---|
| `swiftfloris.ime.firstRender` | `FlorisImeService.onCreateInputView()` |
| `swiftfloris.nlp.suggest` | `LatinLanguageProvider.suggest()` (split into a `suggestImpl` body so the suspend signature stays clean) |
| `swiftfloris.smartbar.candidates.recompose` | `CandidatesRow()` Composable (sequential `beginSection`/`endSection` flanking the body — Compose forbids try/finally around composable calls) |
| `swiftfloris.theme.switch` | `ThemeManager.updateActiveTheme()` (split into a `updateActiveThemeLocked` body so the existing `return@withLock` semantics stay intact) |
| `swiftfloris.dict.load` | `LatinLanguageProvider.loadSpecificDictionary()` (split into a `loadSpecificDictionaryImpl` body) |
| `swiftfloris.nlp.symspell.build` | Both `symSpellIndex` and `symSpellDistance2Index` lazy initialisers in `LatinLanguageProvider` |

All instrumentation uses `android.os.Trace` (Android stdlib, zero
new dependency). The same section names appear in
`KeyboardLatencyBenchmark.kt`'s `TraceSectionMetric(...)` rows so
when the benchmark runs on a clocks-locked device the metrics fire
correctly.

### Unit-test compatibility

`android.os.Trace` is part of the Android JVM stub that throws
"Method not mocked" by default during unit tests. Flipped
`testOptions.unitTests.isReturnDefaultValues = true` in
`app/build.gradle.kts` so the stubs return their defaults instead
of throwing — the existing 945 tests pass through the
SymSpell / suggest / dict-load paths without tripping on tracing.

## Versioning

- `gradle.properties`: `projectVersionCode=1834`,
  `projectVersionName=1.8.34`.
- README badge bumped to `v1.8.34`.

## What's next

- Run `KeyboardLatencyBenchmark` on a clocks-locked Pixel 6 /
  Galaxy S25 Ultra and commit the before/after numbers to
  `docs/BENCHMARKS.md` per the Next-12.1 acceptance bar.
- The `swiftfloris.nlp.suggest` section is currently in the
  `LatinLanguageProvider.suggest()` entry point; if the
  multilingual path becomes the dominant case, drop a nested
  section in `suggestMultilingual()` for finer-grained data.
