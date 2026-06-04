# SwiftFloris Repo Hygiene

Last updated: 2026-06-04 for v1.8.220.

This repository uses one commit per release slice. Keep code, roadmap state,
release notes, and verification evidence together so the next maintainer can
resume from `git log`, `ROADMAP.md`, `COMPLETED.md`, `CHANGELOG.md`, and
`PROJECT_CONTEXT.md` without guessing what happened.

## Current Legacy Markdown Decision

The deleted root markdown files listed in `.gitignore` are intentional legacy
or moved documents. They should stay absent from commits unless a maintainer
explicitly decides to restore one of them as current documentation.

If `git status --short` shows a deleted `*.md` file, stop and classify it
before pushing:

- Restore it if the deletion is unrelated to the current slice.
- Move it under `docs/` and update links if the content is still current.
- Add a release-note and roadmap entry if the deletion is an intentional docs
  consolidation.

## Generated Output Rule

Build outputs, lint reports, APKs, emulator logs, benchmark scratch files, and
Gradle caches stay out of commits. They belong in local `build/` directories,
workflow artifacts, or committed benchmark-result JSON only when a release note
explicitly records a new baseline.

CI runs:

```bash
bash scripts/check-repo-hygiene.sh
```

The script fails on any of the following conditions:

1. Generated build/report directories are tracked (matches `app/build/`,
   `benchmark/build/`, `lib/*/build/`, `.gradle/`, `.kotlin/`, `out/`, `release/`,
   `captures/`).
2. A local working tree contains deleted Markdown files that still need
   classification (so an accidental `git rm` does not slip through staging).
3. Root-level forbidden artefacts are tracked: `*.apk`, `*.aab`, `*.jks`,
   `*.keystore`, `local.properties`, `*.backup*`, `*.bak`. Release APKs and AABs
   live as GitHub Releases artefacts; keystores never enter the tree;
   `local.properties` is per-machine SDK configuration; `*.backup*` belongs
   under `docs/archive/` if kept at all.
4. Any root-level PNG larger than 200 KB is tracked. Large branding assets
   belong under `fastlane/metadata/android/en-US/images/` (for the F-Droid
   listing) or under `app/src/main/res/` (for the app), not the repo root.

The historical rationale: through v1.8.173 the repo carried a 9.7 MB
`app-release-v1.5.2.apk`, a 787 KB `SwiftFloris_icon.png`, and a
`ROADMAP.md.backup-v2` file at the root. Every fresh clone paid the bandwidth
cost; F-Droid reviewers saw a sloppy tree. v1.8.174 untracked the surviving
two (`SwiftFloris_icon.png`, `ROADMAP.md.backup-v2`) and extended this script
to keep them out.

## Commit Scope Rule

Each commit should map to one of these:

- One roadmap or improvement-plan checklist item.
- One tightly related batch where the docs already group the items together.
- One release/version sync after the implementation and verification are done.

Do not mix dependency bumps, product behavior, broad cleanup, and visual polish
unless the roadmap explicitly groups them.

## Handoff Rule

Every final handoff or release note should include the commands that actually
ran. Prefer the exact command line, not a generic "tests passed" line. The
standard command set lives in [`docs/LOCAL_VERIFICATION.md`](LOCAL_VERIFICATION.md).
