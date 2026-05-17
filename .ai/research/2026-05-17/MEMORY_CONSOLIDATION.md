# Memory Consolidation — SwiftFloris

**Research date:** 2026-05-17

This file inventories every AI-instruction, memory, planning, and changelog
file in the repo, and reconciles them against the current v1.8.58 HEAD
reality after same-day research corrections.
Where two files conflict, the resolution is recorded here. Where a file is
stale but harmless, it is left intact with a pointer.

---

## 1. Files inventoried

### 1.1 AI-instruction / agent files

| Checked path | Present? |
|---|---|
| `AGENTS.md` | ✅ — canonical cross-agent instruction file; read first after `PROJECT_CONTEXT.md` |
| `CLAUDE.md` | ✅ — Claude-specific supplement pointing back to `AGENTS.md` / `PROJECT_CONTEXT.md` |
| `GEMINI.md` | ❌ |
| `COPILOT_INSTRUCTIONS.md` / `.github/copilot-instructions.md` | ❌ |
| `.claude/` | ❌ |
| `.claude-instructions` | ❌ |
| `.cursor/rules/**` | ❌ |
| `.cursorrules` | ❌ |
| `.windsurfrules` | ❌ |
| `.ai/` | ✅ — created by the research run; contains date-scoped artifacts |

**Finding:** the first-pass inventory found no instruction files, then the
research sequence added `PROJECT_CONTEXT.md`, `AGENTS.md`, and
`CLAUDE.md`. Future AI sessions should read `PROJECT_CONTEXT.md` first,
then `AGENTS.md`, then tool-specific supplements such as `CLAUDE.md`.

### 1.2 Planning / roadmap / research markdown (12 files)

| File | Bytes | Last logical update | Authoritative for |
|---|---|---|---|
| `ROADMAP.md` | 340 K | v5.3 fifth-pass delta; bodies updated through v1.8.58 | The full picture: NOW / NEXT / LATER tiers, rejected list, risk register, glossary, sources appendix |
| `ROADMAP.md.backup-v2` | 22 K | Pre-v5.0 snapshot | Historical only; treat as audit trail |
| `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` | 37 K | 2026-05-17 (Phase A/B in flight) | Active SwiftKey-parity sprint plan; supersedes the five earlier `SWIFTKEY_*` docs |
| `SWIFTKEY_PARITY_AUDIT.md` | 9.8 K | Pre-2026-05-17 audit | Reconciled into the parity roadmap |
| `SWIFTKEY_PARITY_BUILD_PLAN.md` | 11 K | Pre-2026-05-17 plan | Reconciled into the parity roadmap |
| `SWIFTKEY_PARITY_RESEARCH.md` | 8.6 K | Pre-2026-05-17 research | Reconciled into the parity roadmap |
| `SWIFTKEY_AI_RESEARCH.md` | 4.5 K | Pre-2026-05-17 | Reconciled into the parity roadmap |
| `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md` | 23 K | Pre-2026-05-17 | Reconciled into the parity roadmap |
| `IMPROVEMENT_PLAN.md` | 21 K | 2026-05-05 | Execution-focused quality plan (test, lint, perf, a11y, build); 15 workstreams; alongside ROADMAP, not subordinate to it |
| `GESTURE_TYPING_MULTILINGUAL.md` | 4.9 K | v1.7.x | User-facing guide; behavior frozen as of N1.3 |
| `GESTURE_TYPING_MULTILINGUAL_RESEARCH.md` | 4.6 K | Pre-N1 | Research backing for the multilingual gesture decision |
| `FUTO_VOICE_INPUT_TROUBLESHOOTING.md` | 5.9 K | v1.5.x | User-facing troubleshooting guide; matches current handoff path |
| `VOICE_COMMANDS.md` | 4.3 K | Continually updated | Voice-command grammar reference; current as of Next-2.4 |

### 1.3 Per-release notes (80+ files at root)

Pattern `RELEASE_NOTES_v<MAJOR>.<MINOR>.<PATCH>.md`. **Memory rule (per
user's auto-memory):** "per-release file pattern" is the canonical changelog
style. There is no rolled-up `CHANGELOG.md` and there should not be — each
release's contract is to ship one note describing intent, files touched,
tests added, and Definition-of-Done evidence. Latest observed release
metadata is v1.8.58.

### 1.4 Internal documentation in `docs/`

| File | Purpose |
|---|---|
| `docs/THREAT_MODEL.md` | Threat actors, surfaces, mitigations (last updated 2026-05-09 for v1.7.0) |
| `docs/SECURITY.md` | Dependency-scan + release-time security posture |
| `docs/REPRODUCIBLE_BUILDS.md` | Toolchain pin matrix + F-Droid verifier recipe |
| `docs/BENCHMARKS.md` | Macrobenchmark trace-section contract; device-number capture pending |
| `docs/ACCESSIBILITY.md` | A11y posture |
| `docs/FONTS.md` | Bundled fonts (Nastaliq + Naskh) |
| `docs/INLINE_AUTOFILL.md` | Inline-autofill matrix; verification recipe |
| `docs/MIGRATE_FROM_SWIFTKEY.md` | Three migration paths, 2026-05-31 cutoff |
| `docs/TASKER_INTEGRATION.md` | Tasker intent contract |
| `docs/DEPENDENCY_TRIAGE.md` | Dependency triage playbook + cadence (added v1.8.51) |
| `docs/AI_PROMPTS_EXTERNAL_WORK.md` | Paste-into-fresh-chat prompts for external work |
| `docs/addons/apk-validation.md` | Addon APK validation contract |
| `docs/addons/dictionary-pack-spec.md` | Dictionary pack JSON schema |

### 1.5 Other root-level files

- `LICENSE` — Apache-2.0
- `NOTICE` — bundled-asset attribution (SCOWL BSD, LDNOOBW CC-BY-4.0,
  FlorisBoard upstream Apache-2.0)
- `LICENSES/SCOWL-Copyright.txt` — verbatim SCOWL license
- `README.md` — front door
- `README.md.bak` — superseded; retained for diff
- `FUTO_VOICE_INPUT_TROUBLESHOOTING.md`, `VOICE_COMMANDS.md`,
  `GESTURE_TYPING_MULTILINGUAL.md` — user-facing guides at root (ROADMAP §11
  flags a future consolidation into `docs/`)
- `SwiftFloris_icon.png`, `generate_icon.py` — branding assets
- `hs_err_pid*.log`, `replay_pid*.log` — JVM crash logs from earlier debug
  sessions; **should be deleted** (3+ MB of build noise in repo root)
- `app-release-v1.5.2.apk` — 9.7 MB historical artifact; can stay or move
  into `release/`

---

## 2. Conflict resolution

### 2.1 `SWIFTKEY_PARITY_*` proliferation (5 files)

`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` declares it supersedes the prior
five `SWIFTKEY_*` docs (line 8: *"This document supersedes the earlier
`SWIFTKEY_PARITY_AUDIT.md`, `SWIFTKEY_PARITY_RESEARCH.md`,
`SWIFTKEY_PARITY_BUILD_PLAN.md`, `SWIFTKEY_AI_RESEARCH.md`, and
`SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md`"*).

**Resolution:** keep the five superseded files intact as audit trail but
add a top-of-file pointer to each. This is non-destructive and gives
future contributors a single jumping-off point.

**Applied in continuation:** each of the five superseded files now has a
5-line "SUPERSEDED — 2026-05-17" banner pointing to
`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`, `ROADMAP.md`, and
`PROJECT_CONTEXT.md`; see [CHANGESET_SUMMARY.md](CHANGESET_SUMMARY.md).

### 2.2 Latest version is 1.8.58 (HEAD); README was caught up in later pass

[README.md](../../../README.md) lagged during the first pass, but later
same-day work caught the public README up through the v1.8.58 release
metadata. `gradle.properties` declares **v1.8.58** and the root roadmap
current-version block now matches.

**Resolution:** no open conflict after the fourth/fifth pass; keep release
notes and README in lockstep on future release slices.

### 2.3 Latest git tag is v1.8.40, HEAD is v1.8.58

`git tag --sort=-creatordate` shows `v1.8.40` as the most recent tag. HEAD
is `v1.8.58`. **18 releases have shipped without a git tag.**

**Resolution:** the per-release notes + `gradle.properties` bump are the
release contract; tagging is an aspirational secondary step that has
fallen behind. The `release.yml` GitHub Action expects a `workflow_dispatch`
with a version input — until that workflow is dispatched from a tag, no
tag lands.

Recommend a quick catch-up: tag every shipped release (v1.8.41 … v1.8.58)
from its corresponding commit hash, then push tags. This is reversible and
costs only the commits' bytes in `refs/tags/`. The user controls the
remote (per memory: pushes to SysAdminDoc/SwiftFloris fail 403 from this VM),
so this should be done on the user's main push host.

### 2.4 `IMPROVEMENT_PLAN.md` lint counter is from 2026-05-05

`IMPROVEMENT_PLAN.md` reports "Current lint shape after cleanup batches: 259
warnings, 0 hints." This was an early-May snapshot. The number has almost
certainly drifted because:

- v1.8.42 bumped Kotlin 2.3.20 → 2.3.21 (resolves some warnings, may add
  others).
- v1.8.43 activated the Roborazzi plugin (adds test-source).
- v1.8.46–v1.8.48 added the SwiftKey importer + adversarial-input
  hardening.

**Resolution:** the lint baseline lives in `app/lint.xml`; the *count* in
`IMPROVEMENT_PLAN.md` is informational, not load-bearing. Future lint
batches should refresh that count. Not a blocker.

### 2.5 `:benchmark` and `:lib:native` present-but-detached

`settings.gradle.kts` has both module-include lines commented out. The
`benchmark/` directory has a full `build.gradle.kts` + benchmarks; the
`libnative/` directory contains a `dummy/` placeholder. ROADMAP §2 and §7
Next-12 both reference these.

**Resolution:** ROADMAP §2 already calls them "present-but-detached"; the
right description. The roadmap's plan to re-include them at the
appropriate cost-bearing slice (Next-12.1 = Macrobenchmark; L1+L3+Next-2 =
native runtime) is sound. No change needed.

### 2.6 ROADMAP claims vs. observed code (sampled)

| ROADMAP claim | Observed | Reconciled? |
|---|---|---|
| §0 "v1.8.x reality" — "618+ tracked Kotlin files repo-wide" | `find . -name '*.kt'` piped to `wc -l` ≈ 652 (incl. tests + lib) | ✅ within drift |
| §3 "v1.8.58 latest release" | `gradle.properties` = 1.8.58, HEAD metadata = v1.8.58 | ✅ |
| §6 N7.1 — "verifyNoInternetPermission" Gradle task | Present in [app/build.gradle.kts](../../../app/build.gradle.kts#L227) | ✅ |
| §6 N7.4 — SQLCipher 4.16.0 | Pinned in [libs.versions.toml](../../../gradle/libs.versions.toml#L27) | ✅ |
| §7 Next-10.1 — `permission.REGISTER_ADDON` signature-protected | Declared in [AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml#L17) | ✅ |
| §8 L7 — `dev.patrickgold.florisboard.action.BIND_MCP_DAEMON` | Verified in [`ime/mcp/McpBridgeContract.kt:49`](../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/mcp/McpBridgeContract.kt#L48-L49) — `ACTION_BIND_MCP_DAEMON` constant exact match. 13 MCP source files + 13 MCP test files in tree | ✅ (second-pass verified 2026-05-17) |
| Parity §A2 — `PersonalDictionaryImportBatch` shipped v1.8.53 | Commit `b062127` "Phase A2: post-import confirmation + rollback + wire DictionaryImporter into Settings UI" | ✅ |
| Parity §A3 — encrypted-blob personal-dictionary export codec | Commit `6a47f83` "Phase A3 codec primitive: encrypted-blob personal dictionary export envelope (AES-256-GCM + PBKDF2-HMAC-SHA-256)" — UI wiring still pending | ✅ codec shipped; UI is the open slice |
| Parity §B3 — shared-spelling bilingual handling | Commit `5ebcba1` (HEAD) — `MultilingualTokenScorer` new branch for one-locale candidate, score `0.30` | ✅ |

## 3. External-research findings that update prior memory

From the first external-research pass (saved separately in this run's
`COMPETITOR_MATRIX.md` and `SOURCE_REGISTER.md`):

1. **FlorisBoard upstream is frozen on v0.6.0-alpha02 (2025-01-23).** 16
   months without an alpha. Glide + predictions were re-scoped to v0.7
   (public-beta milestone), not v0.6.
   - ROADMAP §4 thesis "Upstream drift. FlorisBoard v0.6-alpha targets
     glide typing, predictions, floating mode, and Snygg v2 themes" needs
     a soft update: those are aspirational v0.7 items, not in any shipped
     v0.6 alpha. Update without losing the historical reference.

2. **HeliBoard NLnet open-glide library — still not released, ~2 weeks
   before deadline.** HeliBoard v3.9 (2026-03-29) shipped data-gathering
   bug fixes; passive mode "still needs tuning and testing."
   - ROADMAP §6 N1.1 should record the **slip risk** as the base case, not
     just a Risk-Register low-likelihood item.

3. **MediaPipe LLM Inference on Android is now officially deprecated**
   (Google recommends LiteRT-LM). ROADMAP already targets LiteRT-LM for
   L1, but should explicitly state MediaPipe LLM Inference is rejected,
   not just unselected — to prevent future contributors from re-proposing
   it.

4. **FunctionGemma 270M shipped Jan 2026** (Google Developers Blog) —
   structured function-calling + unified action/chat. Distributable via
   `litert-community/functiongemma-270m-ft-mobile-actions` on HuggingFace.
   ROADMAP §C.3 (Gemma 3 270M Q4) should note this is the more relevant
   model for any smart-compose work that involves tool use.

5. **CleverKeys neural swipe pipeline is in F-Droid.** Working transformer
   encoder/decoder + beam search, <200 ms on Pixel 7. GPL-3.0 so SwiftFloris
   cannot link it, but the *quantized ONNX artifact* could be evaluated as
   a drop-in if a permissive license can be negotiated or a clean Apache-2.0
   re-implementation can train against the same architecture.

6. **Android 17 IME-relevant changes** verified: IME visibility on config
   change is **not auto-restored** if the app doesn't handle it (v1.8.45
   already shipped the corresponding fix), edge-to-edge is semi-mandatory
   (v1.8.x already adopted `enableEdgeToEdge()`), local-network permission
   exists but is keyboard-irrelevant.

These items are reflected as actionable additions in
[ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md).

## 4. Open conflicts (none material)

After reconciling the §2 items above, no contradiction in the AI-memory
surface blocks the next slice. README lag was closed by the later same-day
pass. The remaining release-tracking lag is git tags: latest tag v1.8.40
vs HEAD v1.8.58.

## 5. Pointer plan (applied for superseded SwiftKey docs)

Non-destructive pointers applied for the superseded SwiftKey files:

| File | Applied top-of-file banner |
|---|---|
| `SWIFTKEY_PARITY_AUDIT.md` | `> Superseded 2026-05-17 by SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` |
| `SWIFTKEY_PARITY_BUILD_PLAN.md` | (same) |
| `SWIFTKEY_PARITY_RESEARCH.md` | (same) |
| `SWIFTKEY_AI_RESEARCH.md` | (same) |
| `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md` | (same) |
| `ROADMAP.md.backup-v2` | Recommended only: `> Historical roadmap snapshot. Current plan in ROADMAP.md` |
| `README.md.bak` | Recommended only: `> Historical README snapshot. Current in README.md` |

These were not applied by this research run to keep the commit diff focused
on additive artifacts.
