# AGENTS.md — SwiftFloris

This is the canonical instruction file for **any AI coding agent** working
in this repository (Claude Code / Claude Agent SDK, OpenAI Codex,
Aider, OpenHands, Sourcegraph Cody, Replit Agent, Devin, Cursor agent
mode, etc.). Tool-specific files like [`CLAUDE.md`](CLAUDE.md) carry
**only** what is genuinely tool-specific; everything else lives here.

---

## Read this first

**The live open-work list is [`ROADMAP.md`](ROADMAP.md)** — the single source of
truth for "what needs doing", consolidated from the planning docs below.
Completed work lives in [`COMPLETED.md`](COMPLETED.md) and release-level detail
lives in [`CHANGELOG.md`](CHANGELOG.md). The files below are historical context
and sourced reasoning; mine them for *why*, but pick the next task from
`ROADMAP.md`.

1. [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) — single-page consolidated
   project context. Pins the load-bearing invariants, current v1.8.243
   stack, the module layout, roadmap-file routing, and current source-of-truth
   state.
2. [`ARCHITECTURE.md`](ARCHITECTURE.md) — contributor-facing module,
   package, runtime, privacy-boundary, and CI map.
3. [`CONTRIBUTING.md`](CONTRIBUTING.md) — contributor setup,
   verification, privacy, release-note, and PR expectations.
4. [`ROADMAP.md`](ROADMAP.md) — active open-work source of truth with
   retained historical context and sourced appendix.
5. [`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`](docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md)
   — archived sprint snapshot (Phase A -> E) timed to the 2026-05-31 SwiftKey
   account-retirement cutoff; active carry-forward items live in `ROADMAP.md`.
6. [`docs/archive/IMPROVEMENT_PLAN_2026-05-18.md`](docs/archive/IMPROVEMENT_PLAN_2026-05-18.md)
   — archived execution-focused quality / a11y / perf / test / build /
   release plan; active carry-forward items live in `ROADMAP.md`.
7. [`docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
   — most recent research-run additions and corrections, not yet folded
   into `ROADMAP.md`.
8. [`.ai/research/<YYYY-MM-DD>/`](.ai/research/) — full research-run
   artifacts; the most recent date is the right entry point.

If a change you're considering conflicts with `PROJECT_CONTEXT.md` §2
(load-bearing invariants), **the answer is "move that feature into an
addon"** — never "loosen the invariant."

---

## Hard rules

These are pinned by build gates, not just by marketing. Touching any of
them requires changing both the relevant code *and* the gate.

### 1. No `INTERNET` permission in `:app`

The Gradle task `verifyNoInternetPermission` (in
[app/build.gradle.kts](app/build.gradle.kts#L227)) runs as part of
`preBuild` on every variant and fails the build if any `AndroidManifest.xml`
under `app/src/**` declares `INTERNET`, `ACCESS_NETWORK_STATE`,
`ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, or `CHANGE_WIFI_STATE`. CI
re-runs this on every push / PR.

If a feature needs network access, it must move to an isolated optional
addon APK loaded via the `dev.patrickgold.florisboard.permission.REGISTER_ADDON`
signature-protected enrolment mechanism, never the base APK.

### 2. Apache-2.0 ceiling on `:app`

GPL / AGPL / LGPL / FUTO Source-First code may **not** link into `:app`.
They may ship as a clearly-isolated optional addon under their own
license. See [.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md §4](.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md#4-license-compatibility-verification)
for the current compatibility matrix. KenLM is LGPL — **runtime cannot
link into `:app`**; the in-`:app` header parser is original code parsing
a public format and is fine.

### 3. No closed-source binary blobs

No `libjni_latinimegoogle.so`-style swipe blobs. Reproducible builds with
toolchain pinning (see
[docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md)).

### 4. Per-release changelog pattern

Every shipped release adds a `## vX.Y.Z` section to the consolidated
[`CHANGELOG.md`](CHANGELOG.md) (newest first, with an `<a id="vX.Y.Z"></a>`
anchor). This **replaced** the former root-level `RELEASE_NOTES_v*.md`
file-per-release pattern — do not re-introduce per-release note files. Each
section describes intent, files touched, tests added, and Definition-of-Done
evidence. A matching `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
(≤500 chars; draft ≤480 for headroom) ships alongside, enforced by
`scripts/check-fastlane-metadata.sh`.

`gradle.properties` `projectVersionCode` + `projectVersionName` bump in lockstep
with the changelog commit. Tag the release commit at the same time
(`git push --tags`). HEAD is `v1.8.243` (versionCode 2043) as of 2026-06-04.

### 5. Definition of Done (per [ROADMAP.md](ROADMAP.md) §15)

Before marking a roadmap item complete:

1. `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` all
   green locally.
2. Manual QA pass on a real device.
3. `CHANGELOG.md` `## vX.Y.Z` section written (+ matching fastlane changelog).
4. `gradle.properties` versionCode + versionName bumped.
5. New dep / asset → `NOTICE` / `LICENSES/` updated.
6. APK signed and installable; SHA-256 published.
7. CI passes: `verifyNoInternetPermission`, OSV scan, 16 KB alignment.
8. Tests added (unit minimum; Roborazzi if visual; Macrobenchmark if
   perf-sensitive).

### 6. Per-PR scope discipline

One logical improvement per commit / PR. Don't bundle unrelated
refactors. Repo hygiene and scope rules live in
[`docs/REPO_HYGIENE.md`](docs/REPO_HYGIENE.md).

### 7. Verification surface for any code change

The project's Windows / Linux build commands:

```powershell
./gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew.bat :app:verifyRoborazziDebug   # visual regression (if UI-touching)
./gradlew.bat :app:installDebug           # device smoke
```

See [docs/LOCAL_VERIFICATION.md](docs/LOCAL_VERIFICATION.md) for the full local
and CI verification paths, including lint baseline-drift checks and benchmark
harness commands.

Repo hygiene rules for generated outputs, intentional legacy markdown deletions,
commit scope, and final handoffs live in
[docs/REPO_HYGIENE.md](docs/REPO_HYGIENE.md).

This Windows host has a working JDK / Android SDK path for the Gradle
commands above.

---

## Where to put new work

| If your change is… | Lives in… |
|---|---|
| One feature slice, one release | A new `## vX.Y.Z` section in [`CHANGELOG.md`](CHANGELOG.md) + a `gradle.properties` bump + a fastlane changelog |
| A SwiftKey-parity slice (Phase A/B/C/D/E) | The "Phased plan" in [docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md) |
| A roadmap-tier change (NOW / NEXT / LATER / UNDER CONSIDERATION) | [ROADMAP.md](ROADMAP.md) §6/§7/§8/§9 |
| A quality / a11y / perf / test / CI / release-hygiene workstream | [ROADMAP.md](ROADMAP.md); historical workstream context is archived at [docs/archive/IMPROVEMENT_PLAN_2026-05-18.md](docs/archive/IMPROVEMENT_PLAN_2026-05-18.md) |
| A research finding | New `.ai/research/<YYYY-MM-DD>/` directory; updates fold into the next `ROADMAP.md` `v5.X` refresh |
| A security / dependency / crypto migration | [docs/SECURITY.md](docs/SECURITY.md) + [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) + [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md) |
| Addon spec (theme / dict / language / layout / popup-mapping pack) | [docs/addons/](docs/addons/) |

---

## Things that are explicitly rejected

These will not be accepted into the repo. Restating so they don't get
re-proposed (full reasoning in [ROADMAP.md](ROADMAP.md) §10):

- Cloud sync of personal LM via vendor servers
- Microsoft / Google / any account requirement
- GPL / AGPL / LGPL / FUTO Source-First code in `:app`
- Closed-source `.so` blobs (e.g. `libjni_latinimegoogle.so`)
- Federated learning to vendor cloud (federated to *user's own
  devices* via Syncthing CRDT is fine)
- In-keyboard ads / sponsored content
- Cloud-bound Bing / Copilot / Gemini API
- Default-on T9 layout (OK as alt; not default)
- In-keyboard search (Maps/YouTube/web)
- Tenor / Giphy GIF keyboard (bundled local sticker packs are the
  offline equivalent)
- Google Play Store as primary distribution
- Self-update (in-app APK download + install)
- Mandatory analytics opt-out toggle that defaults on
- **MediaPipe LLM Inference API on Android** (deprecated by Google
  2026-03-31; LiteRT-LM is the named successor)
- **NLLB-200 distilled-600M** (CC-BY-NC-4.0 — non-commercial conflicts
  with §1 audit-friendly distribution)
- **CleverKeys glide model (as-shipped)** (GPL-3.0 — cannot link;
  architecture reference only)

---

## Local environment notes

Environment capability is **host-dependent** — check before assuming:

- **`git push`**: works from authorized maintainer hosts (the prior
  "always 403, commit-only" note was specific to one dev VM and is retired —
  see the global "push from any machine" rule). Auto-commit-and-push per
  logical change unless told otherwise.
- **Gradle**: the working host may or may not have a JDK + Android SDK. Where
  it does (e.g. JDK 21 Temurin + Android SDK platforms 34–36.1 + Gradle 9.5.1),
  run the full Definition-of-Done verification locally. If the env `JAVA_HOME`
  points at a stale path, override it inline:
  `export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"`.
  Where it does not, ship the tractable (non-compiling-risk) tier and defer the
  gradle gates to a host that has the toolchain.
- The repo root differs per host (`Z:\repos\…`, `C:\Users\…\repos\…`); use the
  working directory you're invoked in, not a hard-coded path.

---

## Recommended pointer for tool-specific files

If you add another tool-specific instruction file (e.g. `GEMINI.md`,
`.cursorrules`), open it with this pointer block:

```md
## Canonical Project Context

For consolidated project memory, current architecture, known gaps, and
roadmap context, see `PROJECT_CONTEXT.md` and `AGENTS.md`. This file
remains the tool-specific instruction file for <tool name>.
```

Keep tool-specific content focused on what the tool genuinely needs
(slash commands, prompt overrides, model selection, MCP servers).
General project facts belong in `PROJECT_CONTEXT.md`.

---

## How to do a research run

A research run is a full read-through of the repo + external ecosystem
producing the artifacts in `.ai/research/<YYYY-MM-DD>/`. The most recent
run is the model (2026-05-17). Run the canonical "Autonomous Deep
Research, Memory Consolidation, and Roadmap Planning Agent" prompt
that produced the prior run. Recommended cadence: once per major release
window (Phase boundary in `SWIFTKEY_PARITY_ROADMAP_*.md`), or whenever
the ROADMAP claims feel stale.

Required artifacts per run:

- `STATE_OF_REPO.md`
- `MEMORY_CONSOLIDATION.md`
- `SOURCE_REGISTER.md`
- `RESEARCH_LOG.md`
- `COMPETITOR_MATRIX.md`
- `FEATURE_BACKLOG.md`
- `PRIORITIZATION_MATRIX.md`
- `SECURITY_AND_DEPENDENCY_REVIEW.md`
- `DATASET_MODEL_INTEGRATION_REVIEW.md`
- `CHANGESET_SUMMARY.md`
- `CONTINUE_FROM_HERE.md` (only if hit a hard limit)

Plus root-level:

- `PROJECT_CONTEXT.md` (refresh if the load-bearing facts changed)
- `ROADMAP_RESEARCH_ADDENDUM_<YYYY-MM-DD>.md` (additive — never rewrite
  `ROADMAP.md` mechanically)
