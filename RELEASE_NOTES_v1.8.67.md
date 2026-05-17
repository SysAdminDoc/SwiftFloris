# SwiftFloris v1.8.67 — 2026-05-17

N12.5 — reproducible-build self-verification CI.

## Why ship this now

SwiftFloris already pins the reproducible-build toolchain and targets F-Droid's
verified reproducibility tier, but the repository did not yet have a first-party
"build twice, compare" guard. This release adds that guard so deterministic-build
regressions are caught before an F-Droid rebuild attempt.

## What changed

### Reproducible APK workflow

Added `.github/workflows/reproducible-build.yml`, a standalone workflow that
runs on `workflow_dispatch`, and on pushes / pull requests that touch build,
workflow, app, Gradle, or reproducible-build documentation surfaces.

The job checks out full history, validates the Gradle wrapper, installs JDK 17
and CMake/Ninja like the main Android workflow, then runs the new verifier
script.

### Build-twice verifier script

Added `scripts/verify-reproducible-apk.sh`.

The script:

1. Creates two detached Git worktrees at the same commit.
2. Updates submodules in each worktree.
3. Runs `./gradlew --no-daemon --no-build-cache --rerun-tasks clean :app:assembleRelease` in each clean tree.
4. Copies both release APKs to an artifact directory.
5. Requires byte-for-byte equality with `cmp`.
6. On drift, writes per-entry SHA-256 manifests excluding `META-INF/` so the
   workflow can distinguish payload drift from signing / ZIP metadata drift.

## Versioning

- `gradle.properties`: `projectVersionCode=1867`,
  `projectVersionName=1.8.67`.

## Verification

Local checks performed on this Windows VM:

```powershell
git diff --check
python -c "import yaml, pathlib; yaml.safe_load(pathlib.Path('.github/workflows/reproducible-build.yml').read_text())"
rg -n "reproducible-build|verify-reproducible-apk|Reproducible APK Check" .github/workflows scripts docs ROADMAP.md
```

The new workflow and shell script were also checked for LF-only line endings.
This VM still has no Bash, JDK, or Android SDK on the path; Gradle fails with
`JAVA_HOME is not set and no 'java' command could be found in your PATH`, and
`bash -n scripts/verify-reproducible-apk.sh` must run on a Linux host or CI.
Run before merge on the main Android build host or GitHub Actions:

```powershell
.\gradlew.bat :app:assembleRelease
```

The full self-check runs on Ubuntu through `.github/workflows/reproducible-build.yml`.
