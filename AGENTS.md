# AGENTS.md — SwiftFloris

This is the canonical instruction file for **any AI coding agent** working
in this repository (Claude Code / Claude Agent SDK, OpenAI Codex,
Aider, OpenHands, Sourcegraph Cody, Replit Agent, Devin, Cursor agent
mode, etc.). Tool-specific files like [`CLAUDE.md`](CLAUDE.md) carry
**only** what is genuinely tool-specific; everything else lives here.

---

## Read this first

1. [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) — single-page consolidated
   project context. Pins the load-bearing invariants, the v1.8.65
   stack, the module layout, the roadmap-file routing, and the current
   sprint state.
2. [`ROADMAP.md`](ROADMAP.md) — full roadmap with history; ~340 KB,
   tiered NOW / NEXT / LATER / UNDER CONSIDERATION / REJECTED with
   sourced appendix.
3. [`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`](SWIFTKEY_PARITY_ROADMAP_2026-05-17.md)
   — current sprint plan (Phase A → E) timed to the 2026-05-31 SwiftKey
   account-retirement cutoff.
4. [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md) — execution-focused
   quality / a11y / perf / test / build / release plan; 15 workstreams.
5. [`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
   — most recent research-run additions and corrections, not yet folded
   into `ROADMAP.md`.
6. [`.ai/research/<YYYY-MM-DD>/`](.ai/research/) — full research-run
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

### 4. Per-release file pattern

Every shipped release writes its own `RELEASE_NOTES_v<MAJOR>.<MINOR>.<PATCH>.md`
at the repository root. There is no rolled-up `CHANGELOG.md` and there
should not be. Each note describes intent, files touched, tests added,
and Definition-of-Done evidence.

`gradle.properties` `projectVersionCode` + `projectVersionName` bump
in lockstep with the release-notes commit. Tag the release commit (the
tag stream currently lags HEAD by 24 tags; see
[.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md) #6).

### 5. Definition of Done (per [ROADMAP.md](ROADMAP.md) §15)

Before marking a roadmap item complete:

1. `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` all
   green locally.
2. Manual QA pass on a real device.
3. `RELEASE_NOTES_v*.md` file written.
4. `gradle.properties` versionCode + versionName bumped.
5. New dep / asset → `NOTICE` / `LICENSES/` updated.
6. APK signed and installable; SHA-256 published.
7. CI passes: `verifyNoInternetPermission`, OSV scan, 16 KB alignment.
8. Tests added (unit minimum; Roborazzi if visual; Macrobenchmark if
   perf-sensitive).

### 6. Per-PR scope discipline

One logical improvement per commit / PR. Don't bundle unrelated
refactors. `IMPROVEMENT_PLAN.md` §9 Repo Hygiene explicitly tracks this.

### 7. Verification surface for any code change

The project's Windows / Linux build commands:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew.bat :app:verifyRoborazziDebug   # visual regression (if UI-touching)
./gradlew.bat :app:installDebug           # device smoke
```

This VM has no JDK / Android SDK on the path; recommend running the
commands above on the user's main build host before pushing.

---

## Where to put new work

| If your change is… | Lives in… |
|---|---|
| One feature slice, one release | A new `RELEASE_NOTES_vX.Y.Z.md` at repo root + a `gradle.properties` bump |
| A SwiftKey-parity slice (Phase A/B/C/D/E) | The "Phased plan" in [SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](SWIFTKEY_PARITY_ROADMAP_2026-05-17.md) |
| A roadmap-tier change (NOW / NEXT / LATER / UNDER CONSIDERATION) | [ROADMAP.md](ROADMAP.md) §6/§7/§8/§9 |
| A quality / a11y / perf / test / CI / release-hygiene workstream | [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md) |
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

- `git push` to `SysAdminDoc/SwiftFloris` returns 403 from the
  maintainer's dev VM. **Commit locally only**; the user pushes from a
  separate host.
- All SwiftFloris work happens at `Z:\repos\SwiftFloris\`. `Z:\repos\` is
  the master directory.
- The maintainer's primary build / test host has the JDK / Android SDK
  set up; this VM does not. Run gradle commands on the build host before
  merging.

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
