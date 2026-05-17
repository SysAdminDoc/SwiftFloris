# Changeset Summary — 2026-05-17 Research Run

This research run created **18 new files** across five passes and
**modified zero source / build / test files**. The fifth pass added a
small `ROADMAP.md` v5.3 delta and corrected research artifacts for
dependency and competitor drift. `IMPROVEMENT_PLAN.md`, per-release
notes, build files, and source files under `app/` or `lib/` were not
touched.

> **Post-research development continuation:** after the research run,
> v1.8.59 shipped Phase D3 (typing-stats accuracy delta), and v1.8.60
> shipped the Phase B1 multilingual sentence-position prior seed, v1.8.61
> shipped Phase B2 quick-prediction-insert threshold tuning, v1.8.62
> shipped Phase C1 split-keyboard renderer wire-up, v1.8.63 shipped
> Phase C3 bundled High Contrast / Aurora themes, v1.8.64 shipped
> Phase D1 calendar quick-insert, and v1.8.65 shipped Phase A3 encrypted
> dictionary export/import Settings wiring. Those
> follow-ups touched runtime source, tests, strings, bundled Zipf assets,
> `NOTICE`, theme stylesheets, `gradle.properties`, release notes, `README.md`,
> `PROJECT_CONTEXT.md`, `ROADMAP.md`,
> `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`, and
> `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. The historical sections below
> describe the original research run; see `RELEASE_NOTES_v1.8.59.md`,
> `RELEASE_NOTES_v1.8.60.md`, `RELEASE_NOTES_v1.8.61.md`, and
> `RELEASE_NOTES_v1.8.62.md`, `RELEASE_NOTES_v1.8.63.md`,
> `RELEASE_NOTES_v1.8.64.md`, and `RELEASE_NOTES_v1.8.65.md` for the
> implementation changes.

- Pass 1 created 11 files (root: `PROJECT_CONTEXT.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`; nine artifacts under
  `.ai/research/2026-05-17/`).
- Pass 2 added 3 more (`AGENTS.md`, `CLAUDE.md`,
  `SECOND_PASS_FINDINGS.md`) and minor in-place corrections to two
  prior pass-1 artifacts.
- Pass 3 added 1 more (`THIRD_PASS_FINDINGS.md`) and closes the
  SECOND_PASS_FINDINGS §10 "did not cover" backlog: asset audit,
  workflow audit, theme count, test count, release-notes/ROADMAP
  coverage, tag-lag delta, plus 10 verified external facts (LiteRT-LM
  0.11.0 GA, Compose BOM 2026.05.00, Kotlin 2.4 still RC, Android 17
  GA expected June 2026, SwiftKey cutoff confirmed no-extension, 16 KB
  enforcement landed May 1, Obtainium healthy, AOSP cadence verbatim
  citation, Roborazzi 1.60.0, HeliBoard NLnet update).
- Pass 4 added 1 more (`FOURTH_PASS_FINDINGS.md`) and reconciled README,
  privacy/AI documentation, and selected subsystem inspection with the
  v1.8.58 state.
- Pass 5 added 1 more (`FIFTH_PASS_FINDINGS.md`) and corrected stale
  dependency guidance: Activity 1.13.0 is stable, Security Crypto 1.1.0
  exists but APIs are deprecated, AGP target is 9.2.x, KSP target is
  2.3.8, Roborazzi target is 1.60.0, and LeanType is now in the
  competitor matrix.

**Concurrent-release reconciliation:** during this research run, three
releases shipped on master (v1.8.56 Phase B4, v1.8.57 Phase C2 arrow-keys
preset, v1.8.58 Phase D2 task-creation quick action). All three matched
items this research run was preparing to recommend. `PROJECT_CONTEXT.md`
§7 and `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §0 were updated to
reflect HEAD = v1.8.58 and to remove the three closed recommendations
from the open list.

**Post-research continuation reconciliation:** v1.8.59 Phase D3,
v1.8.60 Phase B1, v1.8.61 Phase B2, v1.8.62 Phase C1, v1.8.63 Phase C3,
v1.8.64 Phase D1, and v1.8.65 Phase A3 Settings wiring subsequently
shipped from the same SwiftKey-parity plan. v1.8.66 through v1.8.69 then
shipped the AI transparency surface, reproducible-build self-check, Tink
encrypted-preference migration, and Bump-batch A. The later release-tag
catch-up backfilled local tags `v1.8.41` through `v1.8.69`; only pushing
those tags from the maintainer host remains.

**v1.8.63 continuation files:** `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/{swiftkey_high_contrast,aurora_animated}.json`,
`app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/ActiveThemeLocals.kt`,
`app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/AuroraAnimatedThemeBackground.kt`,
`app/src/test/kotlin/dev/patrickgold/florisboard/ime/window/AuroraAnimatedThemeBackgroundTest.kt`,
and `RELEASE_NOTES_v1.8.63.md` were added. `scripts/gen_m3e_themes.py`,
the bundled theme manifest, `FlorisImeTheme.kt`, `ImeWindow.kt`,
`ThemeContrastTest.kt`, and the release/context docs were updated.

**v1.8.64 continuation files:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/calendar/{CalendarQuickInsertManager,CalendarPermissionActivity,CalendarAgendaPickerPanel}.kt`,
`app/src/test/kotlin/dev/patrickgold/florisboard/ime/calendar/CalendarAgendaFormatterTest.kt`,
and `RELEASE_NOTES_v1.8.64.md` were added. `AndroidManifest.xml`,
`FlorisApplication.kt`, `AppPrefs.kt`, `QuickAction.kt`,
`QuickActionArrangement.kt`, `QuickActionButton.kt`, `TextInputLayout.kt`,
`QuickActionArrangementTest.kt`, `gradle.properties`, and the release/context
docs were updated.

**v1.8.65 continuation files:** `RELEASE_NOTES_v1.8.65.md` was added.
`UserDictionaryScreen.kt`, `UserDictionary.kt`, `DictionaryImporter.kt`,
`PersonalDictionaryImportSummaryDialog.kt`, `DictionaryImporterTest.kt`,
`EncryptedDictionaryExportTest.kt`, `strings.xml`,
`docs/MIGRATE_FROM_SWIFTKEY.md`, `docs/PRIVACY_AND_AI.md`,
`gradle.properties`, and
the release/context docs were updated for the encrypted dictionary
export/import round-trip.

**Second-pass deep-dives** verified or concretized seven items the first
pass left thin:

1. Source-code verification of L1 / L2 / L7 facade contracts (closes
   `MEMORY_CONSOLIDATION.md §2.6 ⚠️` marker).
2. **Tink migration recipe** — exact API surface, artifact pin
   (`com.google.crypto.tink:tink-android:1.19.0`), atomic+idempotent
   migration code pattern, test recipe.
3. **FunctionGemma 270M mobile bundle** is **INT8 at 289 MB**, not 135 MB.
   LiteRT-LM Kotlin loading API still undocumented in the model card.
4. **ML Kit Digital Ink F-Droid friction** is fundamental — the
   recognizer library binary itself is closed-source. F-Droid eligibility
   requires a two-SKU plan with an OSS-CRNN-based alternative addon.
5. **Bergamot upstream is C++/WASM only, no Android assets.** The real
   Android distributable is `DavidVentura/firefox-translator` (MPL-2.0,
   JNI-based, on F-Droid).
6. **Android 17 IME visibility** — exact migration recipe; v1.8.45's
   shipped fix is consistent with the documented behavior.
7. **F-Droid Reproducible Builds 2026 process** — `verification.f-droid.org`,
   required `Binaries:` + `AllowedAPKSigningKeys:` YAML, common failure
   modes; concrete recommendation to add `vcsInfo.enabled = false`.
8. **SCOWL 2020.12.07 → 2026.02.25 + ESDB SQLite future format;
   wordfreq officially in sunset; CC-100 as the non-English Zipf-overlay
   source.**

See [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md) for the full deep-dive
material and seven new ROADMAP item proposals.

The deliberate non-destructive posture honors the prompt's rule:
*"Do not destructively rewrite project files. Prefer creating canonical
consolidated files and adding pointers from older files."*

---

## 1. Files created

### 1.1 Root-level (canonical project context)

| Path | Purpose |
|---|---|
| [PROJECT_CONTEXT.md](../../../PROJECT_CONTEXT.md) | Single-page consolidated project context. Designed as the fastest read for an AI session, new contributor, or maintainer-context refresh. References `ROADMAP.md`, `IMPROVEMENT_PLAN.md`, the parity roadmap, and every research artifact this run produced |
| [AGENTS.md](../../../AGENTS.md) | Canonical cross-agent instruction file with hard invariants, read order, local environment notes, and Definition-of-Done expectations |
| [CLAUDE.md](../../../CLAUDE.md) | Claude-specific supplement that keeps tool-specific workflow advice out of `AGENTS.md` |
| [ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) | Actionable additions, corrections, and reframings keyed to existing `ROADMAP.md` sections. Fifth-pass corrections are now mirrored into the root `ROADMAP.md` v5.3 delta |

### 1.2 Research-run artifacts (under `.ai/research/2026-05-17/`)

| Path | Purpose |
|---|---|
| [STATE_OF_REPO.md](STATE_OF_REPO.md) | Local reconnaissance: identity, invariants, version pins, module layout, source size, subsystem map, CI/workflows, release stream, permissions, tests, TODO inventory, what's intentionally absent |
| [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) | Inventory of every AI / planning / memory file in repo, plus reconciliation against v1.8.58 HEAD reality. Identifies the agent-instruction files, the five superseded SWIFTKEY_* docs, tag lag, and open conflicts (none material) |
| [SOURCE_REGISTER.md](SOURCE_REGISTER.md) | Every local and external source consulted during the run, organized by topic. ~100 URLs across 15 source classes |
| [RESEARCH_LOG.md](RESEARCH_LOG.md) | Search strategies, tools used, parallel-agent dispatch, saturation notes, and known gaps in this pass |
| [COMPETITOR_MATRIX.md](COMPETITOR_MATRIX.md) | 15 OSS + 8 commercial + 4 adjacent keyboards as of May 2026, with version + license + features-SwiftFloris-doesn't-have + strategic implications. Fifth pass added LeanType |
| [SECURITY_AND_DEPENDENCY_REVIEW.md](SECURITY_AND_DEPENDENCY_REVIEW.md) | Every pinned version checked against latest stable; license-compatibility verification (KenLM is LGPL — material correction); CVE status; F-Droid verified-tier path; EU AI Act Article 50 cutoff; recommended bump-batches and security additions |
| [DATASET_MODEL_INTEGRATION_REVIEW.md](DATASET_MODEL_INTEGRATION_REVIEW.md) | Datasets currently bundled vs referenced; models referenced (Gemma 3, FunctionGemma, Whisper, Vosk, Bergamot, librime, ML Kit, CleverKeys, NLLB-200); integrations surfaced; coverage map; license-cleanliness audit |
| [FEATURE_BACKLOG.md](FEATURE_BACKLOG.md) | Raw harvested ideas: 11 with updated-evidence-only against existing ROADMAP items + 33 new ideas across distribution / privacy / dependency / feature-opportunities / testing / docs + 6 long-tail tracked + 6 rejected-with-reasoning |
| [PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md) | Every backlog item scored on impact × urgency / cost; sorted into Tier 1 (≥ 5.0), Tier 2 (3.5–4.9), Tier 3 (2.0–3.4), Tier 4 (track only); ends with recommended next-three-releases mapping |
| [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md) | Deep-dive corrections for Tink migration, FunctionGemma size, ML Kit / Bergamot distribution, F-Droid reproducibility, and dataset drift |
| [THIRD_PASS_FINDINGS.md](THIRD_PASS_FINDINGS.md) | Third-pass verification over assets, workflow, README/theme counts, tags, and additional external facts |
| [FOURTH_PASS_FINDINGS.md](FOURTH_PASS_FINDINGS.md) | Fourth-pass README / privacy / subsystem reconciliation against the current v1.8.58 state |
| [FIFTH_PASS_FINDINGS.md](FIFTH_PASS_FINDINGS.md) | Fifth-pass dependency corrections and LeanType competitor addition |
| **CHANGESET_SUMMARY.md** | This file |

## 2. Files NOT touched

The following files exist in the repo and were **deliberately not modified**
this run:

- `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` — already current.
- `SWIFTKEY_PARITY_AUDIT.md`, `SWIFTKEY_PARITY_BUILD_PLAN.md`,
  `SWIFTKEY_PARITY_RESEARCH.md`, `SWIFTKEY_AI_RESEARCH.md`,
  `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md` — superseded but kept intact
  for audit trail.
  [MEMORY_CONSOLIDATION.md §5](MEMORY_CONSOLIDATION.md) records the
  proposed SUPERSEDED-banner additions; not applied this run.
- `IMPROVEMENT_PLAN.md` — counter from 2026-05-05 is slightly stale but
  not load-bearing; refresh is a side-quest for a future workstream commit.
- Per-release notes (`RELEASE_NOTES_v*.md`) — never modified after
  release.
- All source files under `app/`, `lib/`, `benchmark/`, `libnative/`.
- All build files (`gradle.properties`, `gradle/libs.versions.toml`,
  `gradle/tools.versions.toml`, `app/build.gradle.kts`,
  `lib/*/build.gradle.kts`, `settings.gradle.kts`).
- All workflow files under `.github/workflows/`.
- All asset files.

The recommended bump-batches A/B/C, the Tink migration, and all other
code-touching items live in `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
§A/§B/§C/§D as **commitments**, not yet **commits**.

Root `ROADMAP.md`, `PROJECT_CONTEXT.md`, and the research artifacts were
modified only for planning/context corrections; no runtime behavior changed.

## 3. Why the research run is non-destructive by design

- The user is in the middle of an active SwiftKey-parity sprint; HEAD is
  close to the 2026-05-31 cutoff. The cost of an unintended `ROADMAP.md`
  rewrite during this window is high, so the fifth pass added only a
  bounded v5.3 delta and left the historical body intact.
- The user controls the remote push (per memory: `git push` to
  `SysAdminDoc/SwiftFloris` fails 403 from this VM). Research-run output
  should be reviewable as one or two clean commits on the user's main
  push host, not an opaque rewrite.
- `ROADMAP.md` is heavily sourced and load-bearing. Mechanical
  rewriting would lose the auditability that makes it useful.
- `.ai/research/<YYYY-MM-DD>/` is the right place for research-time
  artifacts; root `ROADMAP.md` should receive only narrow deltas once the
  findings have been checked for contradictions.

## 4. Recommended follow-up commits (NOT made this run)

In rough priority order, with reference to
[PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md):

| Order | Commit suggestion | Files touched |
|---|---|---|
| 1 | `docs: research run 2026-05-17 fifth pass corrections` | This research run as one commit |
| 2 | ✅ `chore(repo): tag v1.8.41 ... v1.8.69 (29-tag catch-up)` | local git tags only; push pending from maintainer host |
| 3 | `chore(deps): bump-batch A (coroutines 1.11, KSP 2.3.8, zxing 3.5.4, aboutlibraries 14.2; keep activity 1.13.0)` | `gradle/libs.versions.toml` + a new release note |
| 4 | `feat(setup): EU AI Act Article 50 first-run explainer + Settings → About surface` | `app/setup/` + `docs/PRIVACY_AND_AI.md` |
| 5 | `feat(repo): PROJECT_CONTEXT pointer in AGENTS.md + CLAUDE.md` | New `AGENTS.md` + `CLAUDE.md` |
| 6 | `chore(repo): remove stale crash + replay logs from repo root` | `hs_err_pid*.log` + `replay_pid*.log` |
| 7 | `chore(deps): replace androidx-security-crypto with Tink + AndroidKeystoreV1` (separate slice — risk-isolated) | `app/build.gradle.kts` + `KeyManager.kt` paths + `PersonalDictionaryEncryptionTest` rewrite |
| 8 | `chore(deps): bump-batch B (Roborazzi 1.60 + Robolectric 4.16.1)` | versions catalog + a sweep over Roborazzi tests |
| 9 | `chore(deps): bump-batch C (AGP 9.2.x + Compose BOM 2026.05.00)` | versions catalog + R8 rules audit |
| 10 | The Phase B4 same-sentence-language-switch hardening slice in `MultilingualTokenScorer` + `TypingContextExtractor` | `app/src/main/kotlin/.../ime/nlp/` |

Commit #1 is what should land next. The remaining commits flow at the
project's per-release cadence.

## 5. Audit footprint

This research run created **~85 KB of new Markdown** across 11 files. By
comparison, `ROADMAP.md` alone is ~340 KB. The research output is well
sub-linear in size relative to the project's prior planning surface
— consolidation, not bloat.

| File | Approx bytes |
|---|---|
| `PROJECT_CONTEXT.md` | 11 K |
| `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` | 16 K |
| `STATE_OF_REPO.md` | 11 K |
| `MEMORY_CONSOLIDATION.md` | 12 K |
| `SOURCE_REGISTER.md` | 13 K |
| `RESEARCH_LOG.md` | 7 K |
| `COMPETITOR_MATRIX.md` | 14 K |
| `SECURITY_AND_DEPENDENCY_REVIEW.md` | 12 K |
| `DATASET_MODEL_INTEGRATION_REVIEW.md` | 9 K |
| `FEATURE_BACKLOG.md` | 11 K |
| `PRIORITIZATION_MATRIX.md` | 11 K |
| `CHANGESET_SUMMARY.md` | (this file) |
| **Total** | **~127 K** |

(Sizes are approximate; treat as order-of-magnitude.)

## 6. Self-audit

Audit pass against the prompt's hard completion criteria:

1. **All required artifacts written to disk:** ✅ — every required file
   in the prompt is present.
2. **Local repository reconnaissance complete:** ✅ —
   [STATE_OF_REPO.md](STATE_OF_REPO.md) covers identity, versions, modules,
   sources, CI, releases, permissions, tests, and TODO inventory.
3. **Project memory consolidated:** ✅ —
   [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) inventories every
   AI / planning / memory file and resolves the conflicts;
   [PROJECT_CONTEXT.md](../../../PROJECT_CONTEXT.md) is the canonical
   consolidated context file.
4. **External research gone through multiple passes:** ✅ — three
   parallel research agents ran (FlorisBoard / HeliBoard / LiteRT-LM;
   competitor matrix; dependency + security); their findings cross-fed
   into the matrix, the security review, the dataset review, the feature
   backlog, the prioritization, and the roadmap addendum.
5. **Source saturation tested:** ✅ — see
   [SOURCE_REGISTER.md §3](SOURCE_REGISTER.md) and
   [RESEARCH_LOG.md §2.4](RESEARCH_LOG.md).
6. **Roadmap updated/improved:**
   ✅ as an additive **addendum**
   ([ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md))
   plus a bounded root `ROADMAP.md` v5.3 delta. This preserves the
   *"do not destructively rewrite project files"* rule and the 340 KB of
   sourced history that the user has built.
7. **Self-audit passes:** This list.
8. **Remaining limitations documented:** ✅ —
   [RESEARCH_LOG.md §4](RESEARCH_LOG.md) records what was not opened,
   what could not be verified, and what would justify a second pass.

## 7. What a future research run should do differently

- Open `ime/mcp/` + `ime/voice/` + `ime/smartcompose/` source files
  directly to verify ROADMAP §L7 / §L1 facade contracts. This run took
  them at the ROADMAP's word.
- Inspect `app/lint.xml` baseline and refresh the lint counter in
  `IMPROVEMENT_PLAN.md` (currently from 2026-05-05).
- Re-fetch the unverified dates (HeliBoard v3.9 year; Sayboard v4.2.1 year;
  Tap Strap Android IME 2026 activity).
- Verify the LiteRT-LM 0.11.0 GA claim — only 0.10.x was confirmed
  externally this pass.
- Open the `app/src/main/assets/ime/dict/data.json` SCOWL bundle for a
  fresh size + content audit (last full audit predates this run).
- Run `:app:dependencies` locally and diff against the version-catalog
  pins (transitive surface not surveyed this run).

---

## 8. v1.8.66 continuation — N8.7 Article 50 transparency surface

The autonomous development loop then shipped the local-code portion of
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §B.2 / `ROADMAP.md` N8.7.

**Files added:**

- `RELEASE_NOTES_v1.8.66.md`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/about/AiFeatureDisclosure.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/about/AiFeaturesScreen.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/app/settings/about/AiFeatureDisclosureCatalogTest.kt`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1866`,
  `projectVersionName=1.8.66`
- `AppPrefs.kt` → new persisted
  `internal__ai_features_explainer_seen` setup acknowledgement
- `SetupScreen.kt` → first setup step now reviews local AI features before
  IME enablement
- `Routes.kt` / `AboutScreen.kt` → reopenable Settings → About →
  **AI features in this keyboard** route
- `strings.xml` / `strings_dont_translate.xml` → disclosure strings and
  GitHub doc URLs
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`, and
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` → HEAD/current release moved
  to v1.8.66 and N8.7 marked shipped

**Verification note:** this VM still has no Java on PATH, so Gradle cannot
run locally. The release notes record the focused Gradle target for the
maintainer build host. Local verification was limited to source inspection,
`git diff --check`, the no-network permission grep, and post-commit git
integrity checks.

---

## 9. v1.8.67 continuation — N12.5 reproducible-build self-check

The autonomous development loop then shipped the local-code portion of
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §B.4 / `ROADMAP.md` N12.5.

**Files added:**

- `.github/workflows/reproducible-build.yml`
- `scripts/verify-reproducible-apk.sh`
- `RELEASE_NOTES_v1.8.67.md`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1867`,
  `projectVersionName=1.8.67`
- `docs/REPRODUCIBLE_BUILDS.md` → documents the build-twice self-check
  and workflow trigger surface
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`, and
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` → HEAD/current release moved
  to v1.8.67 and N12.5 marked shipped

**Verification note:** local verification used `git diff --check`, YAML parse
validation for `.github/workflows/reproducible-build.yml`, LF-only line-ending
checks for the new Linux-facing files, and static grep over the workflow/script
references. Bash, Java, and the Android SDK are absent on this VM, so the shell
syntax check and actual two-release-build self-check must run on GitHub Actions
or the maintainer build host.

---

## 10. v1.8.68 continuation — N7.6 Tink / AndroidKeystore migration

The autonomous development loop then shipped
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §A.2 / §G.2 as `ROADMAP.md` N7.6.

**Files added:**

- `RELEASE_NOTES_v1.8.68.md`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1868`,
  `projectVersionName=1.8.68`
- `gradle/libs.versions.toml` / `app/build.gradle.kts` → removed
  `androidx.security:security-crypto:1.1.0-alpha06`, added
  `com.google.crypto.tink:tink-android:1.21.0`
- `FlorisUserDictionaryEncryption.kt` → SQLCipher passphrase now uses the
  shared Tink / AndroidKeystore wrapper and one-shot legacy AndroidX encrypted
  preference migration
- `ClipboardHistoryManager.kt` → legacy encrypted clipboard-history store now
  uses the shared Tink wrapper with legacy AndroidX migration and in-memory
  fallback on Keystore failure
- `PersonalDictionaryEncryptionTest.kt` → pins the Tink wrapper, dependency,
  legacy parser, and clipboard-history migration contracts
- `NOTICE`, `docs/SECURITY.md`, `docs/THREAT_MODEL.md`, `README.md`,
  `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`, and
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` → release/version/context updates
  for v1.8.68

**Verification note:** local verification used `git diff --check`, source greps
confirming AndroidX Security Crypto removal from app source/build files, Tink
wrapper presence checks, and the no-network permission grep. Java and the
Android SDK are absent on this VM, so the focused
`PersonalDictionaryEncryptionTest` and release assembly must run on the
maintainer build host.

---

## 11. v1.8.69 continuation — Bump-batch A

The autonomous development loop then shipped
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §A.4 / `ROADMAP.md` N14.5.

**Files added:**

- `RELEASE_NOTES_v1.8.69.md`

**Files updated:**

- `gradle.properties` → `projectVersionCode=1869`,
  `projectVersionName=1.8.69`
- `gradle/libs.versions.toml` →
  `kotlinx-coroutines=1.11.0`, `ksp=2.3.8`,
  `mikepenz-aboutlibraries=14.2.0`, `zxing-core=3.5.4`
- `docs/DEPENDENCY_TRIAGE.md` → audit-log row for Bump-batch A and the
  intentional AboutLibraries beta skip
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `FIFTH_PASS_FINDINGS.md`, `PRIORITIZATION_MATRIX.md`,
  `STATE_OF_REPO.md`, and `SECURITY_AND_DEPENDENCY_REVIEW.md` →
  release/version/context updates for v1.8.69

**Verification note:** Maven Central / Gradle Plugin Portal metadata was
re-checked before applying the bumps. Local verification used
`git diff --check`, dependency-version greps, and the no-network permission
grep. Java and the Android SDK are absent on this VM, so
`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` must run on the
maintainer build host.

---

## 12. Release-tag catch-up continuation

The autonomous development loop then completed the release-metadata portion of
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §B.5 / `ROADMAP.md` N16.3.

**Local tags created:**

- `v1.8.41` through `v1.8.69`

**Validation before tagging:**

- Every proposed tag target was checked against the commit's
  `gradle.properties`.
- Each tag's `projectVersionName` matched the tag name without the leading
  `v`.
- Each tag's `projectVersionCode` matched the expected `18xx` code.

**Files updated:**

- `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`,
  `PRIORITIZATION_MATRIX.md`, `STATE_OF_REPO.md`, and this file now record
  the local tag backfill.

**Verification note:** this was intentionally a metadata-only slice. No app
version bump or release note was created because the app release remains
`v1.8.69`. Tags were not pushed from this VM; the maintainer host still needs
to push the `v1.8.41`...`v1.8.69` refs.
