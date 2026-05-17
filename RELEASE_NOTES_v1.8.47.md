# SwiftFloris v1.8.47 — 2026-05-16

N1.4 — FUTO swipe-trace replay and benchmark harness.

## Why ship this now

The previous N1.4 slice added a durable JSON / JSON Lines schema for
MIT-licensed swipe traces, but there was no reusable way to replay
those traces through the existing glide classifier or report accuracy
numbers. This release adds the missing JVM-side harness pieces so the
remaining work is an evidence run, not more schema plumbing.

## What changed

### Replay

`SwipeTraceReplay.toPointerData(record, bounds)` converts normalized
`SwipeTraceRecord.samples` into the
`GlideTypingGesture.Detector.PointerData` shape already consumed by
glide classifiers. `SwipeTraceReplayBounds` validates the concrete
keyboard rectangle up front so a corpus runner cannot silently feed
NaN or zero-sized coordinates into the classifier.

### Benchmark reporting

`SwipeTraceBenchmark.evaluate(...)` accepts imported records plus any
`SwipeTracePredictor` adapter and reports:

1. Total / evaluated / failed record counts.
2. Top-1, top-3, and top-N hits and accuracy.
3. Total and average predictor latency.
4. Capped miss examples with expected word, layout, language, and
   suggestions.
5. Markdown summary output for pasting into `docs/BENCHMARKS.md`.

`docs/BENCHMARKS.md` now includes a pending glide-trace comparison
table for the FUTO MIT corpus and the existing
`StatisticalGlideTypingClassifier`.

## Test-gate cleanup

The full debug unit-test suite exposed two stale test-fixture issues
while verifying this slice:

- `DictionaryImporterTest` had raw multiline fixtures that triggered
  Kotlin 2.3.21 FIR failures around JSON array literals, plus one CSV
  raw string containing three consecutive quotes. The fixtures now use
  explicit strings while preserving the same parser inputs.
- `AddonAuditExportTest` expected `2026-05-16T16:00:00.000Z` but its
  millisecond fixture encoded `2025-05-16T13:20:00.000Z`. The fixture
  timestamp now matches the asserted ISO value.

## Versioning

- `gradle.properties`: `projectVersionCode=1847`,
  `projectVersionName=1.8.47`.

## Verification

Run from the local C: worktree because the VMware shared-folder
checkout hit long-path / generated-source write limits on `Z:`:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 `
  "-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -XX:ReservedCodeCacheSize=128m -XX:CICompilerCount=2 -XX:TieredStopAtLevel=1" `
  "-Dorg.gradle.parallel=false" `
  "-Dkotlin.compiler.execution.strategy=in-process" `
  :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`, 1,168 tests passed.

## What's next

- Download the MIT-licensed FUTO swipe corpus outside the repo.
- Wire imported records into a `StatisticalGlideTypingClassifier`
  adapter using a real keyboard layout.
- Publish corpus size, top-k accuracy, latency, and representative
  misses in `docs/BENCHMARKS.md`.
