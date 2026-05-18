# SwiftFloris Repo Hygiene

Last updated: 2026-05-18 for v1.8.166.

This repository uses one commit per release slice. Keep code, roadmap state,
release notes, and verification evidence together so the next maintainer can
resume from `git log`, `PROJECT_CONTEXT.md`, and `IMPROVEMENT_PLAN.md` without
guessing what happened.

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

The script fails if generated build/report directories are tracked or if a local
working tree contains deleted Markdown files that still need classification.

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
