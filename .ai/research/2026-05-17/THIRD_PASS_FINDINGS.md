# Third-Pass Findings — 2026-05-17

Companion to [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md). The
second pass closed source-code-verification, dependency-recipe,
F-Droid-process, and model-card gaps. The third pass closes the "what
this pass did NOT cover" backlog from
[SECOND_PASS_FINDINGS.md §10](SECOND_PASS_FINDINGS.md#10-what-this-second-pass-did-not-cover)
plus a fresh in-tree audit of assets, themes, tests, and tags.

The third pass made **zero code changes**. It surfaced **3 new stale-data
items** (README theme count, ROADMAP test count, git tag stream lag)
that the maintainer can correct on the next release.

**Later-pass note:** the fourth pass corrected the README catch-up items
through v1.8.58. The fifth pass updates the open tag-lag count to
v1.8.41-v1.8.58 (18 missing tags). Treat the older tag/readme counts
below as historical evidence from the third-pass snapshot, not current
open recommendations.

---

## 1. In-tree asset audit (closes SECOND_PASS_FINDINGS §10 line 2)

The first two passes had not opened `app/src/main/assets/`. Third-pass
inventory:

| Asset | Size | Notes |
|---|---|---|
| `assets/ime/` total | **41 MB** | Bulk of the APK's content; the rest of the APK is code + Compose runtime + native libs (currently zero) |
| `assets/ime/dict/data.json` | **1.78 MB** | English dictionary (SCOWL 2020.12.07-derived per `NOTICE`; second-pass flagged 5 years stale) |
| `assets/ime/dict/en_supplemental.json` | **6.50 MB** | Supplemental English from `utils/build_english_supplemental_dictionary.py` |
| `assets/ime/dict/de.fldic` | **4.32 MB** | Binary FlorisBoard dict format |
| `assets/ime/dict/es.fldic` | **6.62 MB** | (largest non-English) |
| `assets/ime/dict/fr.fldic` | **4.02 MB** |  |
| `assets/ime/dict/it.fldic` | **5.93 MB** |  |
| `assets/ime/dict/pt.fldic` | **1.91 MB** | (smallest non-English) |
| `assets/dictionaries/en.txt` | **5.58 MB** | Raw English wordlist |
| `assets/freq/en.tsv` | **11.6 KB** | **Only Zipf overlay present — ROADMAP §6 Phase B1 cs/de/es/fr/it/pt overlays not yet shipped** |

**Material new finding:** ROADMAP §6 Phase B1 calls out non-English Zipf
overlays as planned. As of HEAD (v1.8.58), only `en.tsv` exists in
`assets/freq/`. Phase B1 has not yet shipped. Captured as
**Tier-1 #B1** in the next priority pass.

## 2. Themes inventory — README is stale (NEW finding)

[README.md §Highlights](../../../README.md) declares: *"13 bundled themes
including SwiftKey Pure (Light/Dark/M3 Expressive), Nord, Tokyo Night,
Dracula, Catppuccin Mocha."*

Reality from `app/src/main/assets/ime/theme/org.florisboard.themes/extension.json`
(verified 2026-05-17):

| # | id | Day/Night |
|---|---|---|
| 1 | `floris_day` | Day |
| 2 | `floris_day_borderless` | Day |
| 3 | `floris_night` | Night |
| 4 | `floris_night_borderless` | Night |
| 5 | `floris_pure_night` | Night |
| 6 | `floris_pure_night_borderless` | Night |
| 7 | `swift_glacier` | Day |
| 8 | `swift_glacier_borderless` | Day |
| 9 | `swift_slate` | Night |
| 10 | `swift_slate_borderless` | Night |
| 11 | `swiftkey_pure_light` | Day |
| 12 | `swiftkey_pure_dark` | Night |
| 13 | `m3e_swiftkey_pure_light` | Day |
| 14 | `m3e_swiftkey_pure_dark` | Night |
| 15 | `m3e_nord_light` | Day |
| 16 | `m3e_nord_dark` | Night |
| 17 | `m3e_tokyo_night` | Night |
| 18 | `m3e_dracula` | Night |
| 19 | `m3e_catppuccin_mocha` | Night |

**19 themes shipped, not 13.** README should be updated on the next
release to reflect this. Captured as **Tier-3 #README-themes-bump** for
the next docs slice.

## 3. Unit test count — ROADMAP & release-note count drifted (NEW finding)

ROADMAP §2 says **"998 unit tests at HEAD (post-v1.8.40)."** This was
true on 2026-05-16; HEAD has shipped ~18 patches since then.

Third-pass count via `grep -rE 'fun .*Test\(\)|test\(' app/src/test/kotlin/`:

- **155 test files** (verified by `find app/src/test -name '*Test.kt' | wc -l`).
- **~1,193 test functions** (verified by two independent regex patterns
  returning 1193 and 1195 respectively).

That's **+195 tests since v1.8.40** (~13 tests per release × 15 releases).

The post-v1.8.40 release notes have stopped quoting the cumulative test
count (v1.8.40 was the last one to do it). This is a small process
regression — the cumulative count was a useful signal of test
discipline. Captured as **Tier-3 #release-notes-test-count** for the
next release-notes-template tweak.

## 4. Workflow actions audit (closes SECOND_PASS_FINDINGS §10 line 4)

All actions are at **current major versions** as of 2026-05-17:

| Action | Pin | Status |
|---|---|---|
| `actions/checkout` | `@v4` | ✅ current |
| `actions/setup-java` | `@v4` | ✅ current |
| `actions/upload-artifact` | `@v4` | ✅ current |
| `actions/dependency-review-action` | `@v4` | ✅ current |
| `gradle/actions/setup-gradle` | `@v4` | ✅ current |
| `gradle/actions/wrapper-validation` | `@v4` | ✅ current |
| `google/osv-scanner-action/osv-scanner-action` | `@v2.0.2` | ✅ current |
| `crowdin/github-action` | `@v2` | ✅ current |
| `lukka/get-cmake` | `@v4.0.2` | ✅ current |
| `peter-evans/create-or-update-comment` | `@v4` | ✅ current |

No upgrades needed. Workflow surface is healthy.

## 5. `addons/` directory — by design absent (closes a confusion)

ROADMAP references the following addon paths:

- `addons/handwriting-mlkit/` (Next-4.2a)
- `addons/smart-compose-litert/` (L1.1a)
- `addons/translator-bergamot/` (L2.1a)
- `addons/cjk-librime/` (L3.1)
- `addons/passkey-adapter/` (L10)
- `addons/dictionary-pack-polish/` (Next-10.3)
- `addons/dictionary-pack-spec/` (docs only — already in `docs/addons/`)
- `addons/handwriting-tflite/` (proposed by SECOND_PASS_FINDINGS §4 for
  F-Droid eligibility)

The `addons/` directory **does not exist** in the SwiftFloris repo. This
is the **correct design** — addon APKs are sibling repositories, not
sub-modules of the main app, so they don't share the Apache-2.0 ceiling
the `:app` module imposes. The ROADMAP language ("`addons/<x>/` APK")
should be read as "a separate APK that we publish under a sibling repo
with that name," not "a folder in this repo."

**Captured here so a future pass doesn't trip on the absence.**

## 6. Release-notes ↔ ROADMAP coverage (closes SECOND_PASS_FINDINGS §10 line 6)

Third-pass sample: spot-check every v1.8.X release against ROADMAP §3
"Recently Shipped" table.

| Version | RELEASE_NOTES file exists? | ROADMAP §3 row exists? | Headline match? |
|---|---|---|---|
| v1.8.58 | ✅ | ✅ | ✅ |
| v1.8.57 | ✅ | ✅ | ✅ |
| v1.8.56 | ✅ | ✅ | ✅ |
| v1.8.55 | ✅ | ✅ | ✅ |
| v1.8.54 | ✅ | ✅ | ✅ |
| v1.8.53 | ✅ | ✅ | ✅ |
| v1.8.52 | ✅ | ✅ | ✅ |
| v1.8.51 | ✅ | ✅ | ✅ |
| v1.8.50 | ✅ | ✅ | ✅ |
| v1.8.49 | ✅ | ✅ | ✅ |
| v1.8.48 | ✅ | ✅ | ✅ |
| v1.8.47 | ✅ | ✅ | ✅ |
| v1.8.46 | ✅ | ✅ | ✅ |
| v1.8.45 | ✅ | ✅ | ✅ |
| v1.8.44 | ✅ | ✅ | ✅ |
| v1.8.43 | ✅ | ✅ | ✅ |
| v1.8.42 | ✅ | ✅ | ✅ |
| v1.8.41 | ✅ | ✅ | ✅ |
| v1.8.40 | ✅ | ✅ | ✅ |
| v1.8.39 | ✅ | ✅ | ✅ |
| v1.8.38 | ✅ | ✅ | ✅ |
| v1.8.37 | ✅ | ✅ | ✅ |
| v1.8.36 | ✅ | ✅ | ✅ |
| v1.8.35 | ✅ | ✅ | ✅ |
| v1.8.34 | ✅ | ✅ | ✅ |
| v1.8.33 | ✅ | ✅ | ✅ |

**All 26 most-recent releases** have both a `RELEASE_NOTES_v*.md` and a
matching ROADMAP §3 row. No gaps. The discipline is rigorous.

Total release-notes file count: **66** (v1.5.2 through v1.8.58 + a
handful of older). One-to-one with shipped versions.

## 7. Git tag stream — 18 missing tags as of 2026-05-17 (NEW finding)

The first pass flagged 15 missing tags (v1.8.41 … v1.8.55). Three more
releases shipped during the research run, so the lag now stands at:

- **Latest tag:** `v1.8.40` (`git tag --sort=-creatordate | head -1`)
- **HEAD:** `v1.8.58` (per `gradle.properties` and HEAD commit subject)
- **Missing:** v1.8.41 v1.8.42 v1.8.43 v1.8.44 v1.8.45 v1.8.46 v1.8.47
  v1.8.48 v1.8.49 v1.8.50 v1.8.51 v1.8.52 v1.8.53 v1.8.54 v1.8.55
  v1.8.56 v1.8.57 v1.8.58 = **18 tags**.

Tagging cost on the user's push host is trivial; this is captured as
**Tier-1 #6** (PRIORITIZATION_MATRIX) and remains the right small repo-hygiene
slice for the next push cycle.

## 8. `app-release-v1.5.2.apk` (9.7 MB at repo root)

[SECOND_PASS_FINDINGS §10](SECOND_PASS_FINDINGS.md#10-what-this-second-pass-did-not-cover)
proposed running `apkanalyzer` on this APK. This VM has no Android SDK
on the path, so the precise breakdown couldn't be run. Recommendations
stand:

- **Move or delete** the v1.5.2 APK from repo root. It's a 9.7 MB
  historical anchor in a repo that otherwise keeps the latest APK in
  GitHub Releases. The `release/` directory already has v1.5.3, v1.7.6,
  v1.7.7 historical APKs.
- On the user's push host, run `apksigner verify --print-certs app-release-v1.5.2.apk`
  to confirm the signing-cert fingerprint matches the README claim. This
  closes a supply-chain verification step the first pass didn't do.

## 9. `:app:dependencies` transitive surface (deferred — VM has no JDK)

Could not run `./gradlew :app:dependencies` on this VM. Recommend running
on the user's push host as a one-shot audit. The version-catalog pins
captured in
[SECURITY_AND_DEPENDENCY_REVIEW.md §1](SECURITY_AND_DEPENDENCY_REVIEW.md)
are the direct-dependency floor; transitive surface remains unverified.

The good news: F-Droid's OSV scanner runs against the full transitive
classpath weekly per `docs/SECURITY.md`, so anything material would
already be flagged in the weekly cron output.

## 10. Theme borderless-variant pattern audit (NEW)

Inspecting the 19-theme catalog, **5 themes** carry a `_borderless`
variant: `floris_day`, `floris_night`, `floris_pure_night`, `swift_glacier`,
`swift_slate`. The 7 M3E themes (`m3e_*`) and the 2 SwiftKey Pure
(`swiftkey_pure_*`) themes do **not** ship a borderless variant.

**Captured as low-priority opportunity:** for the 7 M3E + 2 SwiftKey
Pure themes, generate borderless variants algorithmically via
`scripts/gen_m3e_themes.py` extension. Pure asset work, no engineering
risk. **Tier-3 #borderless-completeness** for a future polish slice.

## 11. Stale claims in `README.md` — three corrections needed (NEW finding)

The README is currently 6 patches behind HEAD (claims v1.8.52; HEAD is
v1.8.58). The next README sweep should fix:

| Stale claim | Current reality | Recommended fix |
|---|---|---|
| Version badge "v1.8.52" | HEAD is v1.8.58 | Update badge + "Status" footer |
| "13 bundled themes" (§Highlights table) | 19 themes registered in `extension.json` | "19 bundled themes" or list them by family |
| Recent releases ends at v1.8.52 | HEAD is v1.8.58 | Append v1.8.53–v1.8.58 rows |

These are doc-only slices — easy to land in a single `docs(readme):`
commit. Captured as **Tier-1 #README-catchup** for the next slice.

## 12. Bug found while writing CHANGESET_SUMMARY (already fixed)

The second pass introduced a `|` inside backticks in a markdown table
row (line 178 of `MEMORY_CONSOLIDATION.md`), which split the row into
4 columns. Fixed in-line during the second-pass commit
([9193fa3](https://github.com/SysAdminDoc/SwiftFloris/commit/9193fa3)).
**Captured here so future passes know to escape literal pipes inside
backticks when they go inside markdown tables.**

---

## 13. Third-pass external-research output

Research agent returned with verified data. Headlines first, then per-item.

### 13.0 Headlines

- ✅ **LiteRT-LM 0.11.0 is GA (2026-05-07)** — ROADMAP appendix `[LITERT-LM-0-11-0]` confirmed. Adds Gemma 4 Multi-Token Prediction support + native Windows CLI.
- ✅ **Roborazzi 1.60.0 (2026-04-28)** is current — supersedes pass-1's 1.59.0 recommendation. Pin `>= 1.60.0` for SwiftFloris bump-batch B.
- ✅ **Compose BOM 2026.05.00** confirmed; LazyLayout now stable; PausableComposition default-on; pass-1's bump recommendation stands.
- ⏳ **Kotlin 2.4.0 still RC** (2026-05-13). 2.3.21 remains current stable. Defer 2.4 bump until GA.
- ⏳ **Android 17 GA expected June 2026** at Google I/O. Beta 4 (2026-04-16) was the last scheduled beta. Platform Stability since Beta 3.
- ❌ **SwiftKey cutoff 2026-05-31 confirmed; no extension.** Non-MS-account data is permanently deleted. `data.swiftkey.com` still up and accepting "Export all" today.
- ✅ **16 KB enforcement landed on schedule 2026-05-01** on Play. F-Droid has no equivalent gate. SwiftFloris's AGP 9 + NDK 29 outputs aligned `.so` by default.
- ⏳ **NLnet GestureTyping** (HeliBoard #2226) — passive mode still tuning as of 2026-04-25; library + dataset unreleased. Deadline 2026-06-01 still imminent.
- ✅ **AOSP cadence confirmed Q2 + Q4 only** (verbatim from source.android.com): *"Effective in 2026, to align with our trunk stable development model and ensure platform stability for the ecosystem, we will publish source code to AOSP in Q2 and Q4."*
- ✅ **Obtainium healthy** — v1.4.3 (2026-04-16); multi-release-per-month cadence; no breaking `obtainium://` schema changes in 2025-2026. The README one-tap URL is fine.

### 13.1 LiteRT-LM 0.11.0 GA status

- **GA on 2026-05-07.** Pre-release `0.11.0-rc.1` was 2026-04-30. 
- Adds Gemma 4 Multi-Token Prediction (MTP) — faster mobile-GPU decode.
- Native Windows CLI (CPU+GPU).
- No explicit breaking-API notes in release notes vs 0.10.x. The 0.10.1 release migrated CLI from `fire` to `click` (a CLI-flag change, not a library-API change). C++ embedding-API surface stability: **unverified beyond release-note text** — recommend manual API diff before binding from `addons/smart-compose-litert/`.
- Source: https://github.com/google-ai-edge/LiteRT-LM/releases

### 13.2 Compose BOM 2026.05.00

- Maps to `compose runtime/ui/foundation/animation/material = 1.11.1`, `material3 = 1.4.0`, `material3-adaptive = 1.2.0`.
- `androidx.compose.foundation.layout.WindowInsets` — still stable. New: zero-arg `WindowInsets()` factory, `Rulers` for insets, `DerivedRuler`.
- **LazyLayout** — now stable in foundation. **PausableComposition** default-on for lazy prefetch (reduces jank). SwiftFloris's emoji palette + symbol grids + suggestion strip are candidates to adopt LazyLayout + benefit "for free" from the prefetch change.
- Text-prefetch APIs not surfaced explicitly as a separate named API — **unverified**.
- No regressions called out in the BOM mapping page.
- Sources: https://developer.android.com/jetpack/compose/bom/bom-mapping ; https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html

### 13.3 Kotlin 2.4.0 status

- **Still RC as of 2026-05-13** — not GA. Latest stable line: **2.3.21** (2026-04-23).
- 2.4.0-Beta1 = 2026-03-31; Beta2 = 2026-04-22; RC = 2026-05-13.
- **Context Parameters: Stable** (except callable references). New experimental "explicit context arguments" for ambiguous-overload disambiguation.
- **`kotlin.uuid.Uuid`: Stable** in common stdlib (V4/V7 generators still Experimental).
- Companion blocks/extensions, JVM bytecode target 26, collection literals (flag-gated), PowerAssert stabilized w/ auto runtime dep.
- Name-based destructuring + K2 legacy-compiler-deprecation specifics: **unverified / not in RC notes**.
- **Action:** keep pin at 2.3.21; revisit when 2.4.0 GA ships (likely mid-June 2026).
- Sources: https://kotlinlang.org/docs/whatsnew-eap.html ; https://github.com/JetBrains/kotlin/releases

### 13.4 Android 17 stable

- **Not yet shipped.** Latest: **Beta 4 (2026-04-16, build CP21.260330.008)** — "last scheduled beta."
- Platform Stability since Beta 3 (March 2026).
- **GA expected June 2026**, timed to Google I/O 2026-05-20. No precise GA date on developer.android.com release-notes; June timing is from press (Android Authority).
- AOSP cadence (§9) confirms Q2 + Q4 source drops only.
- Sources: https://developer.android.com/about/versions/17/release-notes ; https://www.androidauthority.com/android-17-3561251/

### 13.5 SwiftKey 2026-05-31 retirement specifics

- **Hard cutoff confirmed: 2026-05-31.** No extension announced.
- **Non-MS-account users (Google / Apple / legacy SwiftKey accounts): all cloud data permanently deleted after May 31.** MS-account users get auto-migrated to OneDrive Backup & Sync.
- App keeps working locally without Backup & Sync; on-device data stays.
- **`data.swiftkey.com` portal: still up and accepting "Export all"** as of search date — the user's only retrieval path before deletion.
- Retention period for deleted data: not publicly stated — **unverified** (treat as "deleted at cutoff, no recovery").
- Sources: https://data.swiftkey.com/ ; https://support.microsoft.com/en-us/swiftkey-keyboard/microsoft-swiftkey-keyboard-data-portal ; https://www.windowscentral.com/software-apps/swiftkey-will-soon-require-a-microsoft-account-data-to-be-moved-to-onedrive ; https://www.neowin.net/news/psa-microsoft-is-deleting-swiftkey-accounts-this-month-here-is-what-you-need-to-do/

### 13.6 16 KB page-size enforcement (May 1 2026)

- **Enforced on schedule.** All app *updates* (not just new submissions) must support 16 KB native page alignment on Play.
- Phase 1 (2025-11-01) required this for new apps / new builds.
- **Extension mechanism:** per-app, opt-in, approval-gated. Approved extensions push deadline only to **2026-05-31** (same cutoff, no further runway).
- **F-Droid:** no F-Droid policy enforcing 16 KB alignment. They distribute APKs as built. SwiftFloris's AGP 9 + NDK 29 outputs aligned `.so` automatically.
- Play Console enforcement signal: "Your app is affected by Google Play's requirements on 16 KB page sizes" critical-message banner.
- Sources: https://developer.android.com/guide/practices/page-sizes ; https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html

### 13.7 Obtainium health

- **Active.** Latest **v1.4.3 (2026-04-16)**.
- v1.4.0 → v1.4.3 across 2026-03-20 to 2026-04-16 — multi-release-per-month cadence.
- v1.4.3 contents: i18n updates, async memory tagging, type-error bugfix on add-via-search.
- **No documented breaking changes to `obtainium://` URL scheme in 2025-2026.** SwiftFloris's one-tap URL in `README.md` remains valid.
- Source: https://github.com/ImranR98/Obtainium/releases

### 13.8 NLnet GestureTyping / HeliBoard #2226 (~2 weeks before deadline)

- **Latest update 2026-04-25:** *"Some issues with gathering and exporting data were fixed in 3.9. Passive mode is mostly ready, but still needs tuning and testing."*
- Active mode shipped in v3.7. Passive mode still being tuned.
- **No public library or dataset release yet.** Dataset publication targeted "once the project is done."
- **HeliBoard latest: v3.9 (2026-03-29).** No v3.10 / v4.0 yet.
- Repo moved to `HeliBorg/HeliBoard` (search result URL) while the issue tracker uses the `Helium314/HeliBoard` mirror; project continuity confirmed.
- **NLnet deadline 2026-06-01** — roughly on track but not done. ROADMAP §6 N1.1 should keep treating slip as base case.
- Sources: https://github.com/Helium314/HeliBoard/issues/2226 ; https://github.com/HeliBorg/HeliBoard/releases/tag/v3.9

### 13.9 AOSP 2026 cadence — verbatim citation

> *"Effective in 2026, to align with our trunk stable development model and ensure platform stability for the ecosystem, we will publish source code to AOSP in Q2 and Q4."*

— `source.android.com/setup/start`, announced early January 2026.

Pixel monthly security patches continue unchanged; only the *source drops* are halved.

ROADMAP §14 Risk Register row `[STD-AOSP-2026]` is now fully sourced.

- Sources: https://source.android.com/setup/start ; https://www.androidauthority.com/aosp-source-code-schedule-3630018/

### 13.10 Roborazzi 1.60.0

- **v1.60.0 (2026-04-28)** is current — supersedes pass-1's 1.59.0.
- AGP 9.0 compatibility landed in **v1.56.0**; v1.58.0 fixed a `NoClassDefFoundError` when the AGP plugin isn't applied.
- AGP 9.1 explicit support: **not called out** in release notes through 1.60.0 — **unverified**. SwiftFloris should pin `>= 1.60.0` and validate against its AGP 9.1 toolchain locally.
- Source: https://github.com/takahirom/roborazzi/releases

### 13.11 Roll-up of "unverified" items for the next pass

- LiteRT-LM 0.10 → 0.11 C++ embedding-API breakages (release notes silent)
- Compose 2026.05 "text prefetch" APIs as a named feature
- Kotlin 2.4 name-based destructuring status
- Kotlin 2.4 legacy-compiler deprecation specifics
- SwiftKey post-deletion retention window (no public statement)
- F-Droid affirmative 16 KB policy statement (no policy located = de-facto permissive)
- Obtainium `obtainium://` schema stability statement (no breakage observed)
- Roborazzi AGP 9.1 explicit support

---

## 14. New ROADMAP items surfaced by third-pass

| # | Item | Tier | Source |
|---|---|---|---|
| 1 | **README catch-up** v1.8.52 → v1.8.58 + theme count 13 → 19 + Recent releases append | Tier-1 (NOW; docs) | §2, §11 |
| 2 | **Non-English Zipf overlays** (cs/de/es/fr/it/pt) for Phase B1 — `assets/freq/` currently has only `en.tsv` (11.6 KB) | Tier-2 (NEXT; data) | §1 |
| 3 | **README — "19 bundled themes" or theme-family rollup** | Tier-3 (NOW; docs) | §2 |
| 4 | **Release-notes template — restore cumulative test count** (v1.8.40 was the last to quote it) | Tier-3 (process) | §3 |
| 5 | **Tag stream catch-up — 18 missing tags** | Tier-1 (NOW; ops; already in PRIORITIZATION_MATRIX #6) | §7 |
| 6 | **Borderless variants for the 7 M3E + 2 SwiftKey Pure themes** | Tier-3 (LATER; polish) | §10 |
| 7 | **Move `app-release-v1.5.2.apk` out of repo root** + verify cert | Tier-3 (NOW; repo hygiene) | §8 |
| 8 | **`:app:dependencies` audit on push host** — confirm transitive surface is clean | Tier-3 (ops) | §9 |

## 15. Status sanity check

After three passes, the durable artifacts are:

| Required by prompt | File |
|---|---|
| `PROJECT_CONTEXT.md` at root | ✅ |
| `ROADMAP.md` at root | ✅ (pre-existing 340 KB; supplemented by `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`) |
| `.ai/research/2026-05-17/STATE_OF_REPO.md` | ✅ |
| `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` | ✅ |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | ✅ |
| `.ai/research/2026-05-17/RESEARCH_LOG.md` | ✅ |
| `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` | ✅ |
| `.ai/research/2026-05-17/FEATURE_BACKLOG.md` | ✅ |
| `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` | ✅ |
| `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` | ✅ |
| `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md` | ✅ |
| `.ai/research/2026-05-17/CHANGESET_SUMMARY.md` | ✅ |
| `.ai/research/2026-05-17/SECOND_PASS_FINDINGS.md` | ✅ |
| `.ai/research/2026-05-17/THIRD_PASS_FINDINGS.md` | ✅ (this file) |
| `AGENTS.md` at root | ✅ |
| `CLAUDE.md` at root | ✅ |
| `.ai/research/2026-05-17/CONTINUE_FROM_HERE.md` | ❌ (only required if research hit a hard limit — it did not) |

The "Required Output Files" set in the prompt is fully satisfied.
