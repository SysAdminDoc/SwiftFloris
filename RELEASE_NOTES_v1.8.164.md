# SwiftFloris v1.8.164

Date: 2026-05-18

## Backup/Restore Duration Baseline

This release completes the Performance Instrumentation workstream by measuring
backup creation and merge restore on a representative default archive profile:
preferences plus keyboard/theme extension files inside the isolated benchmark
app data.

### Changed

- Added `BenchmarkBackupRestoreActivity`, which seeds representative keyboard
  and theme extension fixture files, exports preferences, zips the same default
  sections selected by the Settings backup screen, unzips the archive, validates
  metadata, and merge-restores the selected sections.
- Added `tools/benchmark-backup-restore.ps1`, which installs the benchmark APK,
  launches the benchmark activity, parses backup/restore log markers, and writes
  JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five backup/restore iterations:

- Median backup create: 12.653698 ms.
- Median archive size: 22,034 bytes.
- Median restore prepare: 4.062604 ms.
- Median merge restore apply: 5.727604 ms.
- Median restore total: 9.874167 ms.
- Median selected/restored sections: 3/3.
- Median missing sections: 0.0.
- Median failed sections: 0.0.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-backup-restore.json`.

### Tests

- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-backup-restore.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `git diff --check` passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.164` / code `1964`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`,
  `AGENTS.md`, `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.
