# SwiftFloris Local Verification

Last updated: 2026-05-18 for v1.8.166.

Run these checks before committing code that changes app behavior, build logic,
resources, or docs that describe shipped behavior.

## Standard Local Gate

```powershell
git diff --check
bash scripts/check-repo-hygiene.sh
.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug
```

Expected result:

- `verifyNoInternetPermission` fails if any app manifest adds `INTERNET` or
  equivalent network permissions.
- `check-repo-hygiene.sh` fails if generated build/report output is tracked or
  local Markdown deletions still need classification.
- `testDebugUnitTest` passes the JVM policy, parser, trust-state, accessibility,
  and screenshot-host tests.
- `verifyRoborazziDebug` hard-fails if committed screenshot baselines drift.
- `lintDebug` writes `app/build/reports/lint-results-debug.*`. Any lint-baseline
  drift must be resolved in the same change that fixes the underlying warning.
- `assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`.

## CI Lint Drift Wrapper

GitHub Actions runs:

```bash
bash scripts/run-lint-debug-with-baseline-check.sh
```

The wrapper runs `:app:lintDebug`, saves the console log to
`app/build/reports/lintDebug-console.log`, and fails if Android Lint reports
stale baseline entries. Use it locally from Git Bash or WSL when touching
`app/lint.xml`, lint configuration, or warning cleanup.

## Device Smoke

When an Android device or emulator is connected:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p dev.patrickgold.florisboard.debug -c android.intent.category.LAUNCHER 1
adb shell logcat -d -t 2000 | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime"
```

The app should open the settings surface without a crash. CI also exposes the
manual `Android Emulator Smoke` workflow for this settings-launch check.

## Performance Baselines

Benchmark-only APKs are opt-in and require a connected device:

```powershell
.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-first-render.ps1 -Iterations 5
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-suggestion-latency.ps1 -Iterations 5
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-dictionary-load.ps1 -Iterations 5
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-candidate-row.ps1 -Iterations 5
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-theme-switch.ps1 -Iterations 5
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-backup-restore.ps1 -Iterations 5
```

Commit new JSON baselines under `docs/benchmark-results/` only when the
roadmap or release notes explicitly call for new performance evidence.
