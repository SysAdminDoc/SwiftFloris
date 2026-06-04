# SwiftFloris Repo Hygiene

Last updated: 2026-06-04 for v1.8.246.

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

### Module Build Cache Survival

`git rm --cached` only removes tracked index entries. It does not delete the
ignored local cache directory that produced them, so `lib/<module>/build/`
directories can remain on disk after a cleanup commit and surprise the next
status check.

When untracking generated module output, verify both states before committing:

1. `git status --short` should no longer show tracked `lib/<module>/build/`
   paths.
2. The local `lib/<module>/build/` directory may still exist; remove it only as
   local generated cache after checking the resolved path is the intended module
   build directory.

Do not preserve module `build/` contents as documentation evidence. If a build
or cache file matters for review, copy a small textual summary into the release
note or a committed file under `docs/`.

## Fastlane Changelog Drafting Rule

The Fastlane changelog is the short store-facing summary for the current
`projectVersionCode`, not a second copy of `CHANGELOG.md`. Draft it after the
full changelog section so it summarizes verified release impact.

- Keep drafts at or below 480 characters even though
  `scripts/check-fastlane-metadata.sh` allows 500. The extra room prevents
  late copy edits from failing the gate.
- Use one plain sentence or two short fragments. Name the recognizable feature,
  guardrail, or maintenance path and the shipped benefit.
- Do not include test commands, file paths, internal-only issue IDs, or claims
  that are not backed by the release evidence.
- For docs-only or hygiene releases, say what contributor or release path is
  now clearer. Do not imply app behavior changed.

## Commit Scope Rule

Each commit should map to one of these:

- One roadmap or improvement-plan checklist item.
- One tightly related batch where the docs already group the items together.
- One release/version sync after the implementation and verification are done.

Do not mix dependency bumps, product behavior, broad cleanup, and visual polish
unless the roadmap explicitly groups them.

## Localization And Copy Cleanup Rule

Treat source-English string edits as product changes. Keep cleanup small enough
for translators to review the changed keys, and do not rewrite translated files
unless the change is a native-language fix or a lint-safe mechanical cleanup.

- Turkish repeated-word cleanup should not blindly delete valid idioms. Prefer
  equivalent Turkish wording that avoids adjacent duplicates, and ignore
  placeholder false positives such as `{url}` followed by "URL" only when the
  user-facing copy is already clear.
- Source labels should name the concrete source: bundled assets,
  app-private storage, external document providers, CSV dictionary files, ZIP
  dictionary archives, or legacy dictionary files. Avoid labels like "Provider",
  "File", or "Storage" when the UI has room for the actual source.
- Backup, restore, import, and export failures should say what stayed
  unchanged, then provide a recovery path or technical detail. Use the pattern
  "Could not ... . <unchanged state>. Details: {error_message}" for compact
  toast/status copy, and keep longer card summaries calm and retry-oriented.
- Destructive confirmations should name the local effect and whether saving is
  required before installed data changes. Generic delete dialogs should say the
  item is permanently removed from this device instead of relying only on
  "cannot be undone."

## Handoff Rule

Every final handoff or release note should include the commands that actually
ran. Prefer the exact command line, not a generic "tests passed" line. The
standard command set lives in [`docs/LOCAL_VERIFICATION.md`](LOCAL_VERIFICATION.md).
